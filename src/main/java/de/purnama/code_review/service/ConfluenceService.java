package de.purnama.code_review.service;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.purnama.code_review.config.ConfluenceConfig;
import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Service for interacting with Confluence API to retrieve page content,
 * parse code blocks, and extract relevant information for code reviews.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfluenceService {

    private final ConfluenceConfig confluenceConfig;
    private final ContentBlockService contentBlockService;
    private final WebClient.Builder webClientBuilder;

    private static final int MAX_CHUNK_SIZE = 1000; // Maximum characters per content block

    /**
     * Fetch content and metadata from a Confluence page and populate the ConfluenceUrl object
     *
     * @param confluenceUrl The Confluence URL to fetch from
     * @return The updated ConfluenceUrl with content fetched
     * @throws ConfluenceException if content cannot be fetched
     */
    public ConfluenceUrl fetchConfluenceContent(ConfluenceUrl confluenceUrl) throws ConfluenceException {
        try {
            // Extract page ID from the URL
            String pageId = extractPageIdFromUrl(confluenceUrl.getUrl());
            if (pageId == null) {
                throw new ConfluenceException("Could not extract page ID from URL: " + confluenceUrl.getUrl());
            }

            // Create WebClient for Confluence API
            WebClient webClient = createConfluenceWebClient();

            // Fetch content from Confluence API
            JsonNode contentNode = webClient.get()
                    .uri("/wiki/rest/api/content/{pageId}?expand=body.storage", pageId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (contentNode == null) {
                throw new ConfluenceException("Failed to retrieve content for page ID: " + pageId);
            }

            // Extract HTML content
            String htmlContent = contentNode.path("body").path("storage").path("value").asText();
            if (htmlContent == null || htmlContent.isEmpty()) {
                throw new ConfluenceException("Empty HTML content received from Confluence for page ID: " + pageId);
            }

            // Extract page title 
            String title = contentNode.path("title").asText("Untitled Page");

            // Update the confluence URL object
            confluenceUrl.setPageId(pageId);
            confluenceUrl.setHtmlContent(htmlContent);

            // Only set title if it's not already set
            if (confluenceUrl.getTitle() == null || confluenceUrl.getTitle().isEmpty()) {
                confluenceUrl.setTitle(title);
            }

            return confluenceUrl;

        } catch (ConfluenceException e) {
            // Re-throw ConfluenceException directly
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions
            throw new ConfluenceException("Error fetching content from Confluence: " + e.getMessage(), e);
        }
    }

    /**
     * Process content from Confluence into content blocks
     *
     * @param confluenceUrl The Confluence URL with HTML content to process
     * @return List of processed content blocks
     * @throws ConfluenceException if content cannot be processed
     */
    public List<ContentBlock> processContentIntoBlocks(ConfluenceUrl confluenceUrl) throws ConfluenceException {
        try {
            if (confluenceUrl.getHtmlContent() == null || confluenceUrl.getHtmlContent().isEmpty()) {
                throw new ConfluenceException("No HTML content available in the ConfluenceUrl object");
            }

            // Simple HTML to text conversion (could be improved with a proper HTML parser)
            String plainText = confluenceUrl.getHtmlContent().replaceAll("<[^>]*>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            List<ContentBlock> contentBlocks = new ArrayList<>();

            // First delete any existing content blocks
            contentBlockService.deleteByConfluenceUrl(confluenceUrl);

            // Split by headers to create logical chunks
            List<String> chunks = splitIntoChunks(plainText);

            int sequence = 1;
            for (String chunk : chunks) {
                if (chunk.trim().length() > 30) { // Skip very small chunks
                    ContentBlock contentBlock = ContentBlock.builder()
                            .confluenceUrl(confluenceUrl)
                            .content(chunk.trim())
                            .sequence(sequence++)
                            .title(extractTitle(chunk))
                            .build();

                    contentBlocks.add(contentBlock);
                }
            }

            if (contentBlocks.isEmpty()) {
                log.warn("No content blocks created for URL: {}", confluenceUrl.getUrl());
            }

            return contentBlockService.saveAll(contentBlocks);

        } catch (ConfluenceException e) {
            // Re-throw ConfluenceException directly
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions
            throw new ConfluenceException("Error processing content into blocks: " + e.getMessage(), e);
        }
    }

    /**
     * For backward compatibility - now uses the separated methods with the updated model
     */
    public List<ContentBlock> extractContentFromConfluence(ConfluenceUrl confluenceUrl) throws ConfluenceException {
        // Fetch content from Confluence and update the ConfluenceUrl object
        ConfluenceUrl updatedUrl = fetchConfluenceContent(confluenceUrl);

        // Process the HTML content into content blocks
        return processContentIntoBlocks(updatedUrl);
    }

    /**
     * Create a WebClient for the Confluence API with authentication
     */
    private WebClient createConfluenceWebClient() {
        return webClientBuilder
                .baseUrl(confluenceConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + java.util.Base64.getEncoder().encodeToString(
                                (confluenceConfig.getUsername() + ":" + confluenceConfig.getApiToken()).getBytes()))
                .build();
    }

    /**
     * Extract the page ID from a Confluence URL
     */
    private String extractPageIdFromUrl(String url) {
        // Try to match standard Confluence Cloud URL pattern
        Pattern pattern = Pattern.compile(".*\\/pages\\/(\\d+).*");
        Matcher matcher = pattern.matcher(url);

        if (matcher.matches()) {
            return matcher.group(1);
        }

        // Try alternative pattern
        pattern = Pattern.compile(".*pageId=(\\d+).*");
        matcher = pattern.matcher(url);

        if (matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Split text into manageable chunks, trying to preserve context
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        // Try to split by paragraphs first
        String[] paragraphs = text.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.length() <= MAX_CHUNK_SIZE) {
                chunks.add(paragraph);
            } else {
                // If paragraph is too large, split by sentences
                String[] sentences = paragraph.split("(?<=[.!?])\\s+");

                StringBuilder currentChunk = new StringBuilder();
                for (String sentence : sentences) {
                    if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE) {
                        if (currentChunk.length() > 0) {
                            chunks.add(currentChunk.toString());
                            currentChunk = new StringBuilder();
                        }

                        // If single sentence is too long, split arbitrarily
                        if (sentence.length() > MAX_CHUNK_SIZE) {
                            int start = 0;
                            while (start < sentence.length()) {
                                int end = Math.min(start + MAX_CHUNK_SIZE, sentence.length());
                                chunks.add(sentence.substring(start, end));
                                start = end;
                            }
                        } else {
                            currentChunk.append(sentence);
                        }
                    } else {
                        currentChunk.append(sentence).append(" ");
                    }
                }

                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
            }
        }

        return chunks;
    }

    /**
     * Try to extract a title from the content chunk
     */
    private String extractTitle(String chunk) {
        // Find the first sentence or first few words
        String firstSentence = chunk.split("(?<=[.!?])\\s+")[0];

        if (firstSentence.length() <= 100) {
            return firstSentence;
        } else {
            // Just use the first few words
            String[] words = chunk.split("\\s+");
            StringBuilder title = new StringBuilder();

            for (int i = 0; i < Math.min(10, words.length); i++) {
                title.append(words[i]).append(" ");
            }

            return title.toString().trim() + "...";
        }
    }
}