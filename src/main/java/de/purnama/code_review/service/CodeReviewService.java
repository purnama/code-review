package de.purnama.code_review.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.purnama.code_review.model.git.GitFile;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.CodeReviewException;
import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.exception.InvalidCodeReviewRequestException;
import de.purnama.code_review.exception.RequestInterruptedException;
import de.purnama.code_review.model.CodeReviewRequest;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.service.git.GitProvider;
import de.purnama.code_review.service.git.GitProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CodeReviewService
 * <p>
 * Service responsible for generating automated code reviews using AI models
 * and integrating with Git repository providers.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final EmbeddingService embeddingService;
    public final ChatModel chatModel;
    private final OpenAIConfig openAIConfig;
    private final MarkdownConverter markdownConverter;
    private final GitProviderFactory gitProviderFactory;
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
            
            Repository URL: %s
            
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
    public CodeReviewResponse reviewCode(CodeReviewRequest request) throws CodeReviewException, GitProviderException {
        try {
            // Use the generic repositoryUrl getter
            String repositoryUrl = request.getRepositoryUrl();
            log.info("Starting code review for repository URL: {}", repositoryUrl);

            // Get the appropriate Git provider for this URL
            GitProvider gitProvider = gitProviderFactory.getProvider(repositoryUrl);

            // Extract owner and repo using the Git provider
            Map<String, String> repoInfo = gitProvider.extractRepositoryInfoFromUrl(repositoryUrl);
            String owner = repoInfo.get("owner");
            String repo = repoInfo.get("repo");
            String branch = repoInfo.get("branch");

            if (owner == null || repo == null) {
                throw new InvalidCodeReviewRequestException("Could not extract repository information from URL. Please provide a valid repository URL.");
            }

            // Check if URL points to a specific file or the whole repository
            if (repoInfo.get("path") != null && !repoInfo.get("path").isEmpty()) {
                // Single file review
                return reviewSingleFile(repositoryUrl);
            } else {
                // Project review
                return reviewProject(owner, repo, branch, repositoryUrl);
            }

        } catch (GitProviderException e) {
            // Git provider specific exceptions are already properly typed, just rethrow
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
     * Reviews a single file from a Git repository
     */
    private CodeReviewResponse reviewSingleFile(String repositoryUrl) throws CodeReviewException, AIModelException, GitProviderException {
        try {
            // Fetch code content from Git provider
            GitProvider gitProvider = gitProviderFactory.getProvider(repositoryUrl);
            String codeContent = gitProvider.fetchFileContent(repositoryUrl);

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
                return processLargeFileInChunks(repositoryUrl, codeContent, formattedGuidelines, relevantGuidelines);
            }

            // Create prompt with the code and relevant guidelines
            String promptContent = String.format(
                    REVIEW_PROMPT_TEMPLATE,
                    repositoryUrl,
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
                        .repositoryUrl(repositoryUrl)
                        .build();
            } catch (Exception e) {
                log.error("Error calling AI model: {}", e.getMessage(), e);
                throw new AIModelException("Failed to generate code review: " + e.getMessage(), e);
            }

        } catch (GitProviderException e) {
            // Rethrow Git provider exceptions
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
     * @throws AIModelException    If the AI model fails to generate a review
     */
    private CodeReviewResponse processLargeFileInChunks(String repositoryUrl, String codeContent,
                                                        String formattedGuidelines, List<String> relevantGuidelines)
            throws CodeReviewException, AIModelException {
        log.info("Processing large file in chunks: {}", repositoryUrl);

        // In test mode, we limit the processing to avoid memory issues
        if (testMode) {
            log.info("Running in test mode - using simplified processing for large file");
            return createSimplifiedReviewForTest(repositoryUrl, formattedGuidelines, relevantGuidelines);
        }

        int chunkSize = openAIConfig.getFileChunkSize();


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
                String chunkReview = processIndividualChunk(repositoryUrl, chunk, formattedGuidelines, currentChunk, totalChunks);

                // Add chunk header and review
                finalReview.append("## Chunk ").append(currentChunk).append(" of ").append(totalChunks).append("\n\n");
                finalReview.append(chunkReview).append("\n\n");

            } catch (RequestInterruptedException e) {
                // Log and continue with next chunk - this is a known condition we can handle
                log.warn("Chunk processing was interrupted {}/{}: {}", currentChunk, totalChunks, e.getMessage());
                finalReview.append("## Chunk ").append(currentChunk).append(" of ").append(totalChunks).append("\n\n");
                finalReview.append("Processing of this chunk was interrupted: ").append(e.getMessage()).append("\n\n");
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
                .repositoryUrl(repositoryUrl)
                .build();
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
    private CodeReviewResponse createSimplifiedReviewForTest(String repositoryUrl, String formattedGuidelines, List<String> relevantGuidelines) {
        String testReview = "# Test Mode Review\n\n" +
                "This review was generated in test mode with reduced memory usage.\n\n" +
                "In a production environment, the file would be split into chunks and each chunk would be reviewed separately.\n\n" +
                "Repository URL: " + repositoryUrl;

        String htmlReview = markdownConverter.convertMarkdownToHtml(testReview);

        return CodeReviewResponse.builder()
                .review(testReview)
                .htmlReview(htmlReview)
                .guidelines(relevantGuidelines)
                .timestamp(LocalDateTime.now())
                .repositoryUrl(repositoryUrl)
                .build();
    }

    /**
     * Process an individual chunk of a large file
     * Extracted to a separate method to make unit testing easier and reduce memory pressure
     *
     * @param repositoryUrl       The repository URL of the file being reviewed
     * @param chunk               The code chunk to review
     * @param formattedGuidelines The relevant guidelines for the review
     * @param chunkNumber         Current chunk number
     * @param totalChunks         Total number of chunks
     * @return The review text for this chunk
     * @throws AIModelException            If the AI model fails to generate a review
     * @throws RequestInterruptedException If the request is interrupted during processing
     */
    protected String processIndividualChunk(String repositoryUrl, String chunk, String formattedGuidelines,
                                            int chunkNumber, int totalChunks) throws AIModelException, RequestInterruptedException {
        log.info("Processing chunk {} of {}, size: {} characters", chunkNumber, totalChunks, chunk.length());

        // Create a prompt for this chunk
        String chunkPrompt = String.format(
                REVIEW_PROMPT_TEMPLATE,
                repositoryUrl + " (Chunk " + chunkNumber + " of " + totalChunks + ")",
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
                    code.charAt(i) == '}' && code.charAt(i + 1) == '\n') {
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
    boolean isInterruptionException(Throwable e) {
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
     * Reviews a whole project from a Git repository
     */
    private CodeReviewResponse reviewProject(String owner, String repo, String branch, String repositoryUrl) throws CodeReviewException {
        try {
            log.info("Starting project review for {}/{} on branch {}", owner, repo, branch);

            // Fetch and prepare repository files for review
            GitProvider gitProvider = gitProviderFactory.getProvider(repositoryUrl);

            // Use the fetchRepositoryFiles method from the GitProvider interface
            List<GitFile> filesToReview = gitProvider.fetchRepositoryFiles(
                owner, repo, branch, openAIConfig.getMaxFilesToReview());

            // If no files to review, return early with informative message
            if (filesToReview == null || filesToReview.isEmpty()) {
                return createEmptyReviewResponse(repositoryUrl);
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
                GitFile file = filesToReview.get(i);
                log.info("Reviewing file {} of {}: {}", (i + 1), filesToReview.size(), file.getPath());

                // Process this file and add its review to the final review
                processRepositoryFile(file, repositoryUrl, formattedGuidelines, finalReview);
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
                    .repositoryUrl(repositoryUrl)
                    .build();

        } catch (Exception e) {
            log.error("Error performing project review: {}", e.getMessage(), e);
            throw new CodeReviewException("Failed to perform project review: " + e.getMessage(), e);
        }
    }

    /**
     * Processes a single file from the repository and appends its review to the final review output
     *
     * @param file The file to review
     * @param repositoryUrl The repository URL for the repository
     * @param formattedGuidelines The formatted coding guidelines for the review
     * @param finalReview The StringBuilder to append the review to
     */
    protected void processRepositoryFile(GitFile file, String repositoryUrl, String formattedGuidelines, StringBuilder finalReview) {
        // Add review header for this file
        finalReview.append("## File: ").append(file.getPath()).append("\n\n");

        // Create single file review prompt
        String singleFilePrompt = String.format(
                REVIEW_PROMPT_TEMPLATE,
                repositoryUrl,
                formattedGuidelines,
                file.getContent()
        );

        try {
            // Generate AI review for this file
            String fileReview = generateAIReview(singleFilePrompt, file.getPath());

            // Add the file review to the final review
            finalReview.append(fileReview).append("\n\n");
        } catch (Exception e) {
            log.error("Error reviewing file {}: {}", file.getPath(), e.getMessage(), e);
            finalReview.append("Error reviewing this file: ").append(e.getMessage()).append("\n\n");
            // Continue to the next file instead of failing the entire review
        }
    }

    /**
     * Generates a code review using the AI model with retry logic for handling interruptions
     *
     * @param prompt The prompt to send to the AI model
     * @param fileIdentifier A string to identify the file in logs (file path or name)
     * @return The generated review text
     * @throws AIModelException If the AI model fails to generate a review
     * @throws RequestInterruptedException If the request is interrupted and cannot be retried
     */
    protected String generateAIReview(String prompt, String fileIdentifier) throws AIModelException, RequestInterruptedException {
        // Generate user message object for the AI model
        UserMessage userMessage = new UserMessage(prompt);
        Prompt aiPrompt = new Prompt(userMessage);

        // Add retry logic for handling interruptions
        int maxRetries = 3;
        int currentAttempt = 0;
        ChatResponse response = null;

        while (currentAttempt < maxRetries) {
            try {
                currentAttempt++;
                log.info("Attempt {} of {} to call AI model for file: {}",
                        currentAttempt, maxRetries, fileIdentifier);

                response = chatModel.call(aiPrompt);
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
            return "Failed to review this file after multiple attempts.";
        }

        // Extract and return the review text
        return response.getResult().getOutput().getText();
    }

    /**
     * Creates a response for an empty repository (no files to review)
     *
     * @param repositoryUrl Original repository URL
     * @return CodeReviewResponse with informative message
     */
    protected CodeReviewResponse createEmptyReviewResponse(String repositoryUrl) {
        String noFilesMessage = "No suitable files were found for review in the repository. This may be because:\n" +
                "1. The repository is empty or contains no supported file types\n" +
                "2. All code files are in ignored directories\n" +
                "3. There was an issue accessing the files from the repository\n\n" +
                "Please check that your repository contains code files with supported extensions and try again.";

        // Convert markdown to HTML
        String htmlMessage = markdownConverter.convertMarkdownToHtml(noFilesMessage);

        return CodeReviewResponse.builder()
                .review(noFilesMessage)
                .htmlReview(htmlMessage)
                .guidelines(new ArrayList<>()) // Initialize with empty list instead of null
                .timestamp(LocalDateTime.now())
                .repositoryUrl(repositoryUrl)
                .build();
    }

}
