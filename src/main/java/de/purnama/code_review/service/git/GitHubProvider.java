package de.purnama.code_review.service.git;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.purnama.code_review.config.GitHubConfig;
import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.model.git.GitFile;
import de.purnama.code_review.model.github.GitContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHub-specific implementation of GitProvider interface
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubProvider implements GitProvider {

    private final WebClient githubWebClient;
    private final GitHubConfig githubConfig;
    private final ObjectMapper objectMapper;

    // Configurable timeout for reactive operations
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public boolean canHandle(String url) {
        return url != null && url.contains("github.com");
    }

    @Override
    public Map<String, String> extractRepositoryInfoFromUrl(String githubUrl) throws GitProviderException {
        Map<String, String> result = new HashMap<>();

        // Handle repository URLs
        if (githubUrl.contains("github.com")) {
            // Remove protocol and domain
            String path = githubUrl.replaceAll("https?://github.com/", "");

            // Split path components
            String[] parts = path.split("/");

            if (parts.length >= 2) {
                result.put("owner", parts[0]);
                result.put("repo", parts[1]);

                // Default branch is main, but could be overridden if specified in URL
                result.put("branch", parts.length > 2 && parts[2].equals("tree") ? parts[3] : "main");

                // Check if URL points to a specific file
                if (parts.length > 3 && parts[2].equals("blob")) {
                    StringBuilder filePath = new StringBuilder();
                    for (int i = 4; i < parts.length; i++) {
                        filePath.append(parts[i]);
                        if (i < parts.length - 1) {
                            filePath.append("/");
                        }
                    }
                    result.put("path", filePath.toString());
                }
            } else {
                throw new GitProviderException("Invalid GitHub URL format. Could not extract repository information.");
            }
        } else {
            throw new GitProviderException("URL is not a GitHub URL: " + githubUrl);
        }

        return result;
    }

    @Override
    public String fetchFileContent(String githubUrl) throws GitProviderException {
        log.info("Fetching code from GitHub URL: {}", githubUrl);

        try {
            // Convert GitHub web URL to raw content URL
            String rawUrl = convertToRawGitHubUrl(githubUrl);

            // Fetch the raw content
            String codeContent = githubWebClient.get()
                    .uri(rawUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (codeContent == null || codeContent.isBlank()) {
                throw new GitProviderException("Could not fetch code content from GitHub");
            }

            return codeContent;
        } catch (GitProviderException e) {
            // Rethrow our custom exception
            throw e;
        } catch (Exception e) {
            log.error("Error fetching code from GitHub: {}", e.getMessage(), e);
            throw new GitProviderException("Failed to fetch code from GitHub: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GitFile> fetchRepositoryFiles(String owner, String repo, String branch, int maxFiles)
            throws GitProviderException {
        try {
            log.info("Fetching repository contents for {}/{} on branch {}", owner, repo, branch);

            // Fetch important files from the repository
            List<GitFile> filesToReview = new ArrayList<>();

            // First fetch the root contents
            String rootUrl = String.format("https://api.github.com/repos/%s/%s/contents", owner, repo);
            if (branch != null && !branch.isEmpty()) {
                rootUrl += "?ref=" + branch;
            }

            log.info("Fetching root contents from URL: {}", rootUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            if (githubConfig.getToken() != null && !githubConfig.getToken().isEmpty()) {
                headers.set("Authorization", "token " + githubConfig.getToken());
            }

            // Now recursively fetch files worth reviewing directly
            recursivelyFetchContentsForReview(rootUrl, headers, filesToReview, maxFiles);

            log.info("Total files collected for review: {}", filesToReview.size());
            return filesToReview;

        } catch (Exception e) {
            log.error("Error fetching repository contents: {}", e.getMessage(), e);
            throw new GitProviderException("Failed to fetch repository contents: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "GitHub";
    }

    /**
     * Converts a regular GitHub URL to a raw content URL
     */
    private String convertToRawGitHubUrl(String githubUrl) throws GitProviderException {
        if (githubUrl.contains("github.com") && githubUrl.contains("/blob/")) {
            return githubUrl.replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/");
        } else {
            throw new GitProviderException("Invalid GitHub URL format. Please provide a URL to a specific file.");
        }
    }

    /**
     * Recursively fetches contents from GitHub API, filtering and collecting only files worth reviewing
     */
    private void recursivelyFetchContentsForReview(String url, HttpHeaders headers,
                                                   List<GitFile> filesToReview, int maxFilesToReview) {
        // If we've reached the maximum files to review, stop collecting
        if (filesToReview.size() >= maxFilesToReview) {
            log.info("Reached maximum number of files to review ({})", maxFilesToReview);
            return;
        }

        try {
            // Use WebClient to fetch contents
            String responseBody = githubWebClient.get()
                    .uri(url)
                    .headers(httpHeaders -> {
                        if (headers.containsKey("Accept")) {
                            httpHeaders.add("Accept", headers.getFirst("Accept"));
                        }
                        if (headers.containsKey("Authorization")) {
                            httpHeaders.add("Authorization", headers.getFirst("Authorization"));
                        }
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (responseBody == null) {
                return;
            }

            try {
                // Parse the response
                GitContent[] contents = objectMapper.readValue(responseBody, GitContent[].class);

                // Process each item
                for (GitContent content : contents) {
                    // Skip if we've reached the maximum files
                    if (filesToReview.size() >= maxFilesToReview) {
                        return;
                    }

                    // If it's a directory, recursively process it if not ignored
                    if ("dir".equals(content.getType())) {
                        if (!shouldIgnoreDirectory(content.getName())) {
                            recursivelyFetchContentsForReview(content.getUrl(), headers, filesToReview, maxFilesToReview);
                        }
                    }
                    // If it's a file and it's a supported type, add it to the files to review
                    else if ("file".equals(content.getType()) && isSupportedFileType(content.getName())) {
                        try {
                            // Fetch the file content using WebClient
                            String fileContent = githubWebClient.get()
                                    .uri(content.getDownloadUrl())
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .timeout(REQUEST_TIMEOUT)
                                    .block();

                            if (fileContent != null && !fileContent.isBlank()) {
                                // Convert to our generic GitFile model
                                GitFile file = GitFile.builder()
                                    .name(content.getName())
                                    .path(content.getPath())
                                    .content(fileContent)
                                    .url(content.getHtmlUrl())
                                    .build();
                                filesToReview.add(file);
                            }
                        } catch (Exception e) {
                            log.warn("Could not fetch content for file {}: {}", content.getPath(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing GitHub API response: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error during recursively fetching content: {}", e.getMessage());
        }
    }

    /**
     * Check if a directory should be ignored during the recursive traversal
     */
    private boolean shouldIgnoreDirectory(String dirName) {
        // Ignore common directories that don't typically contain application code
        // or might contain too many files (like node_modules)
        return dirName != null && (
                dirName.equalsIgnoreCase("node_modules") ||
                        dirName.equalsIgnoreCase(".git") ||
                        dirName.equalsIgnoreCase(".github") ||
                        dirName.equalsIgnoreCase(".idea") ||
                        dirName.equalsIgnoreCase(".vscode") ||
                        dirName.equalsIgnoreCase("target") ||
                        dirName.equalsIgnoreCase("build") ||
                        dirName.equalsIgnoreCase("dist") ||
                        dirName.equalsIgnoreCase("out") ||
                        dirName.equalsIgnoreCase("coverage") ||
                        dirName.equalsIgnoreCase(".metadata") ||
                        dirName.equalsIgnoreCase("__pycache__") ||
                        dirName.equals("bin") ||   // Binary outputs
                        dirName.equals("obj") ||   // Object files
                        dirName.startsWith(".")    // Any hidden directory
        );
    }

    /**
     * Checks if a file is a supported code file based on its extension
     */
    private boolean isSupportedFileType(String fileName) {
        // List of file extensions to include
        List<String> SUPPORTED_EXTENSIONS = List.of(
            ".java", ".js", ".ts", ".py", ".rb", ".c", ".cpp", ".cs", ".go", ".php",
            ".html", ".css", ".scss", ".json", ".xml", ".yaml", ".yml"
        );

        return fileName != null && SUPPORTED_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }
}
