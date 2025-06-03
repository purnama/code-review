package de.purnama.code_review.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.purnama.code_review.config.GitHubConfig;
import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.CodeReviewException;
import de.purnama.code_review.exception.GitHubException;
import de.purnama.code_review.exception.InvalidCodeReviewRequestException;
import de.purnama.code_review.exception.RequestInterruptedException;
import de.purnama.code_review.model.CodeReviewRequest;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CodeReviewService
 * 
 * Service responsible for generating automated code reviews using AI models
 * and integrating with GitHub repositories.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final GitHubConfig githubConfig;
    private final OpenAIConfig openAIConfig;
    private final WebClient githubWebClient;
    private final MarkdownConverter markdownConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Configurable timeout for reactive operations
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // List of file extensions to include (can be expanded)
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
            ".java", ".js", ".ts", ".py", ".rb", ".c", ".cpp", ".cs", ".go", ".php",
            ".html", ".css", ".scss", ".json", ".xml", ".yaml", ".yml"
    );

    private static final String REVIEW_PROMPT_TEMPLATE = """
            You are an expert code reviewer with a deep understanding of software engineering best practices.
            
            I will provide you with code to review and some relevant guidelines from our team's coding standards.
            
            Please analyze the code according to our guidelines and provide a comprehensive review, focusing on:
            1. Code quality and readability
            2. Potential bugs or edge cases
            3. Performance considerations
            4. Security issues
            5. Alignment with best practices and our guidelines
            
            GitHub URL: %s
            
            Relevant guidelines from our team's standards:
            %s
            
            Code to review:
            ```
            %s
            ```
            
            Please provide a well-structured review with specific recommendations for improvement.
            Be constructive and thorough, but also concise and focused on the most important issues.
            """;

    // Flag to enable test mode (reduces memory usage for tests)
    private boolean testMode = false;

    /**
     * Set the service to test mode which reduces memory usage
     * This should only be called in test environments
     *
     * @param isTestMode true to enable test mode
     */
    public void setTestMode(boolean isTestMode) {
        this.testMode = isTestMode;
        log.info("CodeReviewService test mode set to: {}", isTestMode);
    }

    /**
     * Processes a code review and returns the results to the client
     * Uses server-side markdown to HTML conversion
     */
    public CodeReviewResponse reviewCode(CodeReviewRequest request) throws CodeReviewException {
        try {
            String githubUrl = request.getGithubUrl();
            log.info("Starting code review for GitHub URL: {}", githubUrl);

            // Extract owner and repo from GitHub URL
            Map<String, String> repoInfo = extractRepoInfoFromUrl(githubUrl);
            String owner = repoInfo.get("owner");
            String repo = repoInfo.get("repo");
            String branch = repoInfo.get("branch");

            if (owner == null || repo == null) {
                throw new InvalidCodeReviewRequestException("Could not extract repository information from URL. Please provide a valid GitHub repository URL.");
            }

            // Check if URL points to a specific file or the whole repository
            if (repoInfo.get("path") != null && !repoInfo.get("path").isEmpty()) {
                // Single file review
                return reviewSingleFile(githubUrl);
            } else {
                // Project review
                return reviewProject(owner, repo, branch, githubUrl);
            }

        } catch (GitHubException e) {
            // GitHub specific exceptions are already properly typed, just rethrow
            throw e;
        } catch (AIModelException e) {
            // AI model exceptions are already properly typed, just rethrow
            throw e;
        } catch (CodeReviewException e) {
            // Any other custom exception we've already thrown, just rethrow
            throw e;
        } catch (Exception e) {
            log.error("Error performing code review: {}", e.getMessage(), e);
            throw new CodeReviewException("Failed to perform code review: " + e.getMessage(), e);
        }
    }

    /**
     * Reviews a single file from GitHub
     */
    private CodeReviewResponse reviewSingleFile(String githubUrl) throws CodeReviewException, AIModelException {
        try {
            // Fetch code content from GitHub URL
            String codeContent = fetchCodeFromGitHub(githubUrl);

            // Find relevant guidelines using embeddings-based similarity search
            List<ContentBlock> relevantBlocks = embeddingService.findSimilarContent(codeContent, openAIConfig.getContentBlocksLimit());

            // Extract and format the content from relevant blocks
            List<String> relevantGuidelines = relevantBlocks.stream()
                    .map(block -> {
                        String title = block.getTitle() != null ? block.getTitle() : "Guideline";
                        return "# " + title + "\n" + block.getContent();
                    })
                    .collect(Collectors.toList());

            String formattedGuidelines = String.join("\n\n", relevantGuidelines);

            // Check if the file is large and needs chunking
            int chunkSize = openAIConfig.getFileChunkSize();
            if (codeContent.length() > chunkSize) {
                log.info("Large file detected (size: {}), processing in chunks of {} characters", 
                        codeContent.length(), chunkSize);
                return processLargeFileInChunks(githubUrl, codeContent, formattedGuidelines, relevantGuidelines);
            }

            // Create prompt with the code and relevant guidelines
            String promptContent = String.format(
                    REVIEW_PROMPT_TEMPLATE,
                    githubUrl,
                    formattedGuidelines,
                    codeContent
            );

            try {
                // Generate code review using the LLM
                UserMessage userMessage = new UserMessage(promptContent);
                Prompt prompt = new Prompt(userMessage);
                ChatResponse response = chatModel.call(prompt);
                String review = response.getResult().getOutput().getText();
    
                // Convert markdown to HTML
                String htmlReview = markdownConverter.convertMarkdownToHtml(review);
    
                // Build and return the response
                return CodeReviewResponse.builder()
                        .review(review)
                        .htmlReview(htmlReview)
                        .guidelines(relevantGuidelines)
                        .timestamp(LocalDateTime.now())
                        .githubUrl(githubUrl)
                        .build();
            } catch (Exception e) {
                log.error("Error calling AI model: {}", e.getMessage(), e);
                throw new AIModelException("Failed to generate code review: " + e.getMessage(), e);
            }

        } catch (GitHubException e) {
            // Rethrow GitHub exceptions
            throw e;
        } catch (AIModelException e) {
            // Rethrow AI model exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error performing single file review: {}", e.getMessage(), e);
            // Only wrap as CodeReviewException if not already an AIModelException
            if (e instanceof AIModelException) {
                throw (AIModelException) e;
            }
            throw new CodeReviewException("Failed to perform code review: " + e.getMessage(), e);
        }
    }

    /**
     * Process a large file by breaking it into manageable chunks
     * Memory-optimized implementation that handles each chunk separately
     *
     * @throws CodeReviewException If an error occurs during processing
     * @throws AIModelException If the AI model fails to generate a review
     */
    private CodeReviewResponse processLargeFileInChunks(String githubUrl, String codeContent, 
                                                      String formattedGuidelines, List<String> relevantGuidelines)
                                                      throws CodeReviewException, AIModelException {
        log.info("Processing large file in chunks: {}", githubUrl);
        
        // In test mode, we limit the processing to avoid memory issues
        if (testMode) {
            log.info("Running in test mode - using simplified processing for large file");
            return createSimplifiedReviewForTest(githubUrl, formattedGuidelines, relevantGuidelines);
        }

        int chunkSize = openAIConfig.getFileChunkSize();

        try {
            // Calculate number of chunks without keeping all chunks in memory at once
            int totalChunks = calculateTotalChunks(codeContent, chunkSize);
            log.info("Estimated {} chunks needed for file", totalChunks);

            // Use a pre-sized StringBuilder to minimize reallocations - use absolute value to avoid negative size
            int initialCapacity = Math.min(Math.abs(totalChunks) * 1000, 100000);
            StringBuilder finalReview = new StringBuilder(initialCapacity > 0 ? initialCapacity : 10000);
            finalReview.append("# Code Review Summary\n\n");
            finalReview.append("This is a review of a large file that was processed in " + totalChunks + " chunks.\n\n");

            // Process the file in chunks directly instead of keeping all chunks in memory
            int currentChunk = 1;
            int start = 0;

            while (start < codeContent.length()) {
                // Extract single chunk
                int end = calculateChunkEndPosition(codeContent, start, chunkSize);
                String chunk = codeContent.substring(start, end);

                try {
                    // Process this single chunk
                    String chunkReview = processIndividualChunk(githubUrl, chunk, formattedGuidelines, currentChunk, totalChunks);

                    // Add chunk header and review
                    finalReview.append("## Chunk ").append(currentChunk).append(" of ").append(totalChunks).append("\n\n");
                    finalReview.append(chunkReview).append("\n\n");

                } catch (AIModelException e) {
                    // Propagate AI model exceptions
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing chunk {}/{}: {}", currentChunk, totalChunks, e.getMessage(), e);
                    finalReview.append("## Chunk ").append(currentChunk).append(" of ").append(totalChunks).append("\n\n");
                    finalReview.append("Error reviewing this chunk: ").append(e.getMessage()).append("\n\n");
                }

                // Move to next chunk
                start = end;
                currentChunk++;

                // Release memory and suggest garbage collection periodically
                chunk = null;
                if (currentChunk % 2 == 0) {
                    System.gc();
                }
            }

            // Release reference to the full codeContent to help GC
            codeContent = null;
            System.gc();

            // After processing all chunks, add a final summary section
            finalReview.append("# Final Summary\n\n");
            finalReview.append("This review was generated by processing a large file in chunks. ");
            finalReview.append("Please review the individual chunk analyses above for specific issues and recommendations.\n\n");

            String review = finalReview.toString();

            // Release StringBuilder to help GC
            finalReview = null;

            // Convert markdown to HTML
            String htmlReview = markdownConverter.convertMarkdownToHtml(review);

            return CodeReviewResponse.builder()
                    .review(review)
                    .htmlReview(htmlReview)
                    .guidelines(relevantGuidelines)
                    .timestamp(LocalDateTime.now())
                    .githubUrl(githubUrl)
                    .build();

        } catch (AIModelException e) {
            // Propagate AI model exceptions directly
            throw e;
        } catch (Exception e) {
            log.error("Error during large file processing: {}", e.getMessage(), e);
            throw new CodeReviewException("Failed to process large file: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate the end position for a chunk, finding a suitable boundary
     */
    private int calculateChunkEndPosition(String codeContent, int start, int chunkSize) {
        int approximateEnd = Math.min(start + chunkSize, codeContent.length());

        // If we're at the end of the content, just return it
        if (approximateEnd >= codeContent.length()) {
            return codeContent.length();
        }

        // Try to find an ideal boundary
        int idealEnd = findIdealChunkBoundary(codeContent, start, approximateEnd);
        return idealEnd > start ? idealEnd : approximateEnd;
    }

    /**
     * Calculate total number of chunks needed without storing them all in memory
     */
    private int calculateTotalChunks(String codeContent, int chunkSize) {
        if (codeContent.length() <= chunkSize) {
            return 1;
        }

        // Estimate chunks needed (this is an approximation)
        return (int) Math.ceil((double) codeContent.length() / (chunkSize * 0.9));
    }

    /**
     * Creates a simplified review response for use in test mode
     */
    private CodeReviewResponse createSimplifiedReviewForTest(String githubUrl, String formattedGuidelines, List<String> relevantGuidelines) {
        String testReview = "# Test Mode Review\n\n" +
                "This review was generated in test mode with reduced memory usage.\n\n" +
                "In a production environment, the file would be split into chunks and each chunk would be reviewed separately.\n\n" +
                "GitHub URL: " + githubUrl;

        String htmlReview = markdownConverter.convertMarkdownToHtml(testReview);

        return CodeReviewResponse.builder()
                .review(testReview)
                .htmlReview(htmlReview)
                .guidelines(relevantGuidelines)
                .timestamp(LocalDateTime.now())
                .githubUrl(githubUrl)
                .build();
    }

    /**
     * Process an individual chunk of a large file
     * Extracted to a separate method to make unit testing easier and reduce memory pressure
     *
     * @param githubUrl The GitHub URL of the file being reviewed
     * @param chunk The code chunk to review
     * @param formattedGuidelines The relevant guidelines for the review
     * @param chunkNumber Current chunk number
     * @param totalChunks Total number of chunks
     * @return The review text for this chunk
     * @throws AIModelException If the AI model fails to generate a review
     * @throws RequestInterruptedException If the request is interrupted during processing
     */
    protected String processIndividualChunk(String githubUrl, String chunk, String formattedGuidelines,
                                           int chunkNumber, int totalChunks) throws AIModelException, RequestInterruptedException {
        log.info("Processing chunk {} of {}, size: {} characters", chunkNumber, totalChunks, chunk.length());

        // Create a prompt for this chunk
        String chunkPrompt = String.format(
                REVIEW_PROMPT_TEMPLATE,
                githubUrl + " (Chunk " + chunkNumber + " of " + totalChunks + ")",
                formattedGuidelines,
                chunk
        );

        // Generate review for this chunk with retry logic
        int maxRetries = 3;
        int currentAttempt = 0;
        ChatResponse response = null;

        while (currentAttempt < maxRetries) {
            try {
                currentAttempt++;
                log.info("Attempt {} of {} to call AI model for chunk {}/{}",
                        currentAttempt, maxRetries, chunkNumber, totalChunks);

                UserMessage userMessage = new UserMessage(chunkPrompt);
                Prompt prompt = new Prompt(userMessage);
                response = chatModel.call(prompt);
                break; // If successful, break out of retry loop
            } catch (Exception e) {
                if (isInterruptionException(e) && currentAttempt < maxRetries) {
                    log.warn("AI model call was interrupted. Will retry ({}/{})", currentAttempt, maxRetries);
                    try {
                        Thread.sleep(1000 * currentAttempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RequestInterruptedException("Thread interrupted during retry wait", ie);
                    }
                } else if (currentAttempt >= maxRetries) {
                    throw new AIModelException("Failed to generate code review after multiple retries", e);
                }
            }
        }

        // If we got through retries with no success
        if (response == null) {
            return "Failed to review this chunk after multiple attempts.";
        }
        
        // Return the chunk review text
        return response.getResult().getOutput().getText();
    }

    /**
     * Split code content into chunks with proper handling for code blocks
     * Memory-optimized implementation that processes the content in segments
     */
    private List<String> splitCodeIntoChunks(String codeContent, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        
        // If code is small enough, return as single chunk
        if (codeContent.length() <= chunkSize) {
            chunks.add(codeContent);
            return chunks;
        }
        
        int start = 0;
        while (start < codeContent.length()) {
            int end = Math.min(start + chunkSize, codeContent.length());
            
            // If we're not at the end of the file, try to find a good split point
            if (end < codeContent.length()) {
                // Look for a blank line or end of method/class within the acceptable range
                int idealEnd = findIdealChunkBoundary(codeContent, start, end);
                
                // If we found a good boundary, use it
                if (idealEnd > start) {
                    end = idealEnd;
                }
            }
            
            // Add the chunk, using substring efficiently
            chunks.add(codeContent.substring(start, end));
            start = end;
        }
        
        return chunks;
    }
    
    /**
     * Find an ideal chunk boundary (blank line, method end, etc.) near the end point
     */
    private int findIdealChunkBoundary(String code, int start, int approximateEnd) {
        int searchWindow = (int) Math.max(5, (approximateEnd - start) * 0.15);
        int searchStart = Math.max(start, approximateEnd - searchWindow);
        int bestPosition = -1;
        for (int i = approximateEnd; i >= searchStart; i--) {
            if (i > code.length()) continue;
            // Find the start and end of the current line
            int lineStart = (i == 0) ? 0 : code.lastIndexOf('\n', i - 1) + 1;
            int lineEnd = code.indexOf('\n', i);
            if (lineEnd == -1) lineEnd = code.length();
            String line = code.substring(lineStart, lineEnd);
            // Check for a blank line (empty or whitespace-only)
            if (line.trim().isEmpty()) {
                bestPosition = lineEnd + 1;
                // Ensure we don't go past the code length
                if (bestPosition > code.length()) bestPosition = code.length();
                // Only return if it's within the chunk
                if (bestPosition > start && bestPosition <= approximateEnd) {
                    return bestPosition;
                }
            }
            // Check for a closing brace followed by a newline
            if (i > 0 && i < code.length() - 1 &&
                code.charAt(i) == '}' && code.charAt(i+1) == '\n') {
                bestPosition = i + 2;
                if (bestPosition > code.length()) bestPosition = code.length();
                if (bestPosition > start && bestPosition <= approximateEnd) {
                    return bestPosition;
                }
            }
        }
        // Fallback: return approximateEnd if no better boundary found
        return approximateEnd;
    }
    
    /**
     * Check if an exception is related to a network interruption or timeout
     */
    private boolean isInterruptionException(Throwable e) {
        if (e == null) return false;

        // Check if it's directly an InterruptedException
        if (e instanceof InterruptedException) return true;

        // Check if the message contains interruption-related terms
        if (e.getMessage() != null &&
                (e.getMessage().toLowerCase().contains("interrupt") ||
                        e.getMessage().toLowerCase().contains("timeout") ||
                        e.getMessage().toLowerCase().contains("timed out"))) {
            return true;
        }

        // Check cause recursively
        return isInterruptionException(e.getCause());
    }
    
    /**
     * Check if an exception is related to a network interruption or timeout
     * and convert it to our custom exception type if needed
     */
    private void handleInterruptionException(Throwable e, String message) throws RequestInterruptedException {
        if (e == null) return;

        // Check if it's directly an InterruptedException
        if (e instanceof InterruptedException) {
            throw new RequestInterruptedException(message, e);
        }

        // Check if the message contains interruption-related terms
        if (e.getMessage() != null &&
                (e.getMessage().toLowerCase().contains("interrupt") ||
                        e.getMessage().toLowerCase().contains("timeout") ||
                        e.getMessage().toLowerCase().contains("timed out"))) {
            throw new RequestInterruptedException(message, e);
        }

        // Check cause recursively
        if (e.getCause() != null) {
            handleInterruptionException(e.getCause(), message);
        }
    }

    /**
     * Reviews a whole project from GitHub
     */
    private CodeReviewResponse reviewProject(String owner, String repo, String branch, String githubUrl) throws CodeReviewException {
        try {
            log.info("Starting project review for {}/{} on branch {}", owner, repo, branch);

            // Fetch important files from the repository
            List<GitHubFile> filesToReview = fetchRepositoryContents(owner, repo, branch);

            log.info("Found {} items in repository contents", filesToReview.size());

            if (filesToReview.isEmpty()) {
                // Instead of throwing an exception, return a response with an informative message
                // and initialize guidelines as an empty list to avoid null pointer in the template
                log.info("No suitable files found for review in the repository.");
                String noFilesMessage = "No suitable files were found for review in the repository. This may be because:\n" +
                        "1. The repository is empty or contains no supported file types\n" +
                        "2. All code files are in ignored directories\n" +
                        "3. There was an issue accessing the files from GitHub\n\n" +
                        "Please check that your repository contains code files with supported extensions and try again.";
                
                // Convert markdown to HTML
                String htmlMessage = markdownConverter.convertMarkdownToHtml(noFilesMessage);
                
                return CodeReviewResponse.builder()
                        .review(noFilesMessage)
                        .htmlReview(htmlMessage)
                        .guidelines(new ArrayList<>()) // Initialize with empty list instead of null
                        .timestamp(LocalDateTime.now())
                        .githubUrl(githubUrl)
                        .build();
            }

            // Apply max files limit
            int maxFiles = openAIConfig.getMaxFilesToReview();
            if (filesToReview.size() > maxFiles) {
                log.info("Limiting review to {} files as per configuration (total files: {})",
                        maxFiles, filesToReview.size());
                filesToReview = filesToReview.subList(0, maxFiles);
            }

            // Find relevant guidelines using embeddings-based similarity search from combined code content
            log.info("Combining code content for embedding search");
            String combinedCode = filesToReview.stream()
                    .map(file -> file.getContent())
                    .collect(Collectors.joining("\n\n"));

            log.info("Finding similar content blocks");
            List<ContentBlock> relevantBlocks = embeddingService.findSimilarContent(combinedCode, openAIConfig.getContentBlocksLimit());
            log.info("Found {} relevant content blocks", relevantBlocks.size());

            // Extract and format the content from relevant blocks
            List<String> relevantGuidelines = relevantBlocks.stream()
                    .map(block -> {
                        return "# " + block.getTitle() + "\n" + block.getContent();
                    })
                    .collect(Collectors.toList());

            String formattedGuidelines = String.join("\n\n", relevantGuidelines);

            // Process files one by one
            log.info("Beginning sequential file review");
            StringBuilder finalReview = new StringBuilder();
            finalReview.append("# Code Review Summary\n\n");
            finalReview.append("The following files were reviewed:\n\n");

            for (int i = 0; i < filesToReview.size(); i++) {
                GitHubFile file = filesToReview.get(i);
                log.info("Reviewing file {} of {}: {}", (i + 1), filesToReview.size(), file.getPath());

                // Create single file review prompt
                String singleFilePrompt = String.format(
                        REVIEW_PROMPT_TEMPLATE,
                        githubUrl,
                        formattedGuidelines,
                        file.getContent()
                );

                // Generate code review for this single file
                UserMessage userMessage = new UserMessage(singleFilePrompt);
                Prompt prompt = new Prompt(userMessage);

                // Add review header for this file
                finalReview.append("## File: ").append(file.getPath()).append("\n\n");

                try {
                    // Add retry logic for handling interruptions
                    int maxRetries = 3;
                    int currentAttempt = 0;
                    ChatResponse response = null;

                    while (currentAttempt < maxRetries) {
                        try {
                            currentAttempt++;
                            log.info("Attempt {} of {} to call AI model for file: {}",
                                    currentAttempt, maxRetries, file.getPath());
                            response = chatModel.call(prompt);
                            // If successful, break out of the retry loop
                            break;
                        } catch (Exception e) {
                            // Check if it's an interruption-related exception
                            if (isInterruptionException(e) && currentAttempt < maxRetries) {
                                log.warn("AI model call was interrupted. Will retry ({}/{})", currentAttempt, maxRetries);
                                // Wait before retrying (exponential backoff)
                                try {
                                    Thread.sleep(1000 * currentAttempt);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RequestInterruptedException("Thread interrupted during retry wait", ie);
                                }
                            } else {
                                // Either not an interruption or we've exhausted retries
                                throw new AIModelException("Failed to generate code review after multiple retries", e);
                            }
                        }
                    }

                    // If we got through the retries with no success
                    if (response == null) {
                        finalReview.append("Failed to review this file after multiple attempts. Skipping to next file.\n\n");
                        continue;
                    }

                    // Add the file review to the final review
                    String fileReview = response.getResult().getOutput().getText();
                    finalReview.append(fileReview).append("\n\n");

                } catch (Exception e) {
                    log.error("Error reviewing file {}: {}", file.getPath(), e.getMessage(), e);
                    finalReview.append("Error reviewing this file: ").append(e.getMessage()).append("\n\n");
                    // Continue with next file instead of failing the entire review
                }
            }

            // Build and return the response with the combined reviews
            log.info("Building final response object with reviews from {} files", filesToReview.size());
            String review = finalReview.toString();
            
            // Convert markdown to HTML
            String htmlReview = markdownConverter.convertMarkdownToHtml(review);
            
            return CodeReviewResponse.builder()
                    .review(review)
                    .htmlReview(htmlReview)
                    .guidelines(relevantGuidelines)
                    .timestamp(LocalDateTime.now())
                    .githubUrl(githubUrl)
                    .build();

        } catch (CodeReviewException e) {
            // Rethrow our custom exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error performing project review: {}", e.getMessage(), e);
            throw new CodeReviewException("Failed to perform project review: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches code content from a specific GitHub file URL
     */
    private String fetchCodeFromGitHub(String githubUrl) throws GitHubException {
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
                throw new GitHubException("Could not fetch code content from GitHub");
            }

            return codeContent;
        } catch (GitHubException e) {
            // Rethrow our custom exception
            throw e;
        } catch (Exception e) {
            log.error("Error fetching code from GitHub: {}", e.getMessage(), e);
            throw new GitHubException("Failed to fetch code from GitHub: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts owner, repo, branch and path from GitHub URL
     */
    private Map<String, String> extractRepoInfoFromUrl(String githubUrl) {
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
            }
        }

        return result;
    }

    /**
     * Converts a regular GitHub URL to a raw content URL
     */
    private String convertToRawGitHubUrl(String githubUrl) throws InvalidCodeReviewRequestException {
        // Replace "github.com" with "raw.githubusercontent.com"
        // Replace "/blob/" with "/"

        if (githubUrl.contains("github.com") && githubUrl.contains("/blob/")) {
            return githubUrl.replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/");
        } else {
            throw new InvalidCodeReviewRequestException("Invalid GitHub URL format. Please provide a URL to a specific file.");
        }
    }

    /**
     * Fetches repository contents from GitHub API
     */
    private List<GitHubFile> fetchRepositoryContents(String owner, String repo, String branch) throws GitHubException {
        try {
            log.info("Fetching repository contents for {}/{} on branch {}", owner, repo, branch);

            // Fetch important files from the repository
            List<GitHubFile> filesToReview = new ArrayList<>();
            int maxFilesToReview = openAIConfig.getMaxFilesToReview();

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
            recursivelyFetchContentsForReview(rootUrl, headers, filesToReview, maxFilesToReview);

            log.info("Total files collected for review: {}", filesToReview.size());
            return filesToReview;

        } catch (Exception e) {
            log.error("Error fetching repository contents: {}", e.getMessage(), e);
            throw new GitHubException("Failed to fetch repository contents: " + e.getMessage(), e);
        }
    }

    /**
     * Recursively fetches contents from GitHub API, filtering and collecting only files worth reviewing
     * 
     * @param url The GitHub API URL to fetch contents from
     * @param headers HTTP headers for the request
     * @param filesToReview List to store files worth reviewing
     * @param maxFilesToReview Maximum number of files to collect for review
     */
    private void recursivelyFetchContentsForReview(String url, HttpHeaders headers, 
                                        List<GitHubFile> filesToReview, int maxFilesToReview) {
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
                GitHubContent[] contents = objectMapper.readValue(responseBody, GitHubContent[].class);
                
                // Process each item
                for (GitHubContent content : contents) {
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
                                // Add to files to review
                                GitHubFile file = new GitHubFile();
                                file.setName(content.getName());
                                file.setPath(content.getPath());
                                file.setContent(fileContent);
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
     * 
     * @param dirName Directory name to check
     * @return true if the directory should be ignored, false otherwise
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
            dirName.equalsIgnoreCase(".metadata") ||  // Added .metadata directory to ignore list
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
        return SUPPORTED_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }

    /**
     * GitHub Content API response class
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubContent {
        private String name;
        private String path;
        private String type;
        @JsonProperty("download_url")
        private String downloadUrl;
        private String url;

        // Jackson will map "download_url" from JSON to this field
        public String getDownloadUrl() {
            return downloadUrl;
        }

        // This is needed for Jackson deserialization
        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }
    }

    /**
     * GitHub file with content
     */
    @Data
    @NoArgsConstructor
    private static class GitHubFile {
        private String name;
        private String path;
        private String content;
    }
}
