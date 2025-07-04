package de.purnama.code_review.service.git;

import java.util.List;
import java.util.Map;

import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.model.git.GitRepository;
import de.purnama.code_review.model.git.GitFile;

/**
 * Interface for Git repository providers (GitHub, GitLab, etc.)
 */
public interface GitProvider {

    /**
     * Check if this provider can handle the given URL
     *
     * @param url The URL to check
     * @return true if this provider can handle the URL
     */
    boolean canHandle(String url);

    /**
     * Extract repository information from a URL
     *
     * @param url The URL to extract information from
     * @return A map containing repository information (owner, repo, branch, path)
     * @throws GitProviderException If the URL cannot be parsed
     */
    Map<String, String> extractRepositoryInfoFromUrl(String url) throws GitProviderException;

    /**
     * Fetch code content from a specific file URL
     *
     * @param url The URL to fetch content from
     * @return The code content as a string
     * @throws GitProviderException If the content cannot be fetched
     */
    String fetchFileContent(String url) throws GitProviderException;

    /**
     * Fetch repository files suitable for code review
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param branch Repository branch
     * @param maxFiles Maximum number of files to fetch
     * @return List of Git files
     * @throws GitProviderException If the repository contents cannot be fetched
     */
    List<GitFile> fetchRepositoryFiles(String owner, String repo, String branch, int maxFiles) throws GitProviderException;

    /**
     * Get the name of the provider (e.g., "GitHub", "GitLab")
     *
     * @return The provider name
     */
    String getProviderName();
}
