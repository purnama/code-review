package de.purnama.code_review.service;


import java.util.ArrayList;
import java.util.List;

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

            // Simple HTML to text conversion using String manipulation instead of regex
            // First, remove HTML tags
            String text = removeHtmlTags(confluenceUrl.getHtmlContent());
            // Then replace HTML entities and normalize whitespace
            String plainText = normalizeWhitespace(text);

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
     * Extract the page ID from a Confluence URL using String operations
     * instead of regular expressions for better readability and performance.
     */
    private String extractPageIdFromUrl(String url) {
        if (url == null) {
            return null;
        }
        
        // Handle standard Confluence Cloud URL pattern: /pages/123456/
        int pagesIndex = url.indexOf("/pages/");
        if (pagesIndex >= 0) {
            // Start after "/pages/"
            int startIndex = pagesIndex + 7;
            int endIndex = url.indexOf("/", startIndex);
            
            // If no trailing slash, use the end of the string
            if (endIndex < 0) {
                endIndex = url.length();
            }
            
            // Extract the substring and verify it's numeric
            String pageId = url.substring(startIndex, endIndex);
            if (isNumeric(pageId)) {
                return pageId;
            }
        }
        
        // Handle URL with pageId query parameter: ?pageId=123456
        int pageIdIndex = url.indexOf("pageId=");
        if (pageIdIndex >= 0) {
            // Start after "pageId="
            int startIndex = pageIdIndex + 7;
            int endIndex = url.indexOf("&", startIndex);
            
            // If no other parameters, use the end of the string
            if (endIndex < 0) {
                endIndex = url.length();
            }
            
            // Extract the substring and verify it's numeric
            String pageId = url.substring(startIndex, endIndex);
            if (isNumeric(pageId)) {
                return pageId;
            }
        }
        
        return null;
    }
    
    /**
     * Helper method to check if a string contains only digits
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Split text into manageable chunks, trying to preserve context
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        // Try to split by paragraphs first
        List<String> paragraphs = splitIntoParagraphs(text);

        for (String paragraph : paragraphs) {
            if (paragraph.length() <= MAX_CHUNK_SIZE) {
                chunks.add(paragraph);
            } else {
                // If paragraph is too large, split by sentences
                List<String> sentences = splitIntoSentences(paragraph);

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
        String firstSentence = extractFirstSentence(chunk);

        if (firstSentence.length() <= 100) {
            return firstSentence;
        } else {
            // Just use the first few words
            String[] words = splitIntoWords(chunk);
            StringBuilder title = new StringBuilder();

            for (int i = 0; i < Math.min(10, words.length); i++) {
                title.append(words[i]).append(" ");
            }

            return title.toString().trim() + "...";
        }
    }
    
    /**
     * Remove HTML tags from a string using character-by-character parsing
     * instead of regular expressions for improved performance.
     */
    private String removeHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder(html.length());
        boolean inTag = false;
        
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            
            if (c == '<') {
                inTag = true;
                // Add space to preserve word boundaries
                result.append(' ');
                continue;
            }
            
            if (c == '>') {
                inTag = false;
                continue;
            }
            
            // Only append characters that are not inside tags
            if (!inTag) {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Normalize whitespace and replace common HTML entities
     */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Replace common HTML entities
        String result = text.replace("&nbsp;", " ")
                           .replace("&lt;", "<")
                           .replace("&gt;", ">")
                           .replace("&amp;", "&")
                           .replace("&quot;", "\"")
                           .replace("&apos;", "'");
        
        // Normalize whitespace using character-by-character processing
        StringBuilder normalized = new StringBuilder(result.length());
        boolean lastWasSpace = false;
        
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            
            // Consider various whitespace characters
            boolean isWhitespace = Character.isWhitespace(c);
            
            // Skip consecutive whitespace
            if (isWhitespace) {
                if (!lastWasSpace) {
                    normalized.append(' ');
                    lastWasSpace = true;
                }
            } else {
                normalized.append(c);
                lastWasSpace = false;
            }
        }
        
        return normalized.toString().trim();
    }
    
    /**
     * Extract the first sentence from a string without using complex regex
     */
    private String extractFirstSentence(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        int end = text.length();
        
        // Look for common sentence endings followed by whitespace
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            
            if ((c == '.' || c == '!' || c == '?') && 
                Character.isWhitespace(text.charAt(i + 1))) {
                end = i + 1;
                break;
            }
        }
        
        return text.substring(0, end);
    }
    
    /**
     * Split text into words without using regex
     */
    private String[] splitIntoWords(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        
        List<String> words = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (Character.isWhitespace(c)) {
                // End of word
                if (currentWord.length() > 0) {
                    words.add(currentWord.toString());
                    currentWord = new StringBuilder();
                }
            } else {
                currentWord.append(c);
            }
        }
        
        // Add last word if any
        if (currentWord.length() > 0) {
            words.add(currentWord.toString());
        }
        
        return words.toArray(String[]::new);
    }
    
    /**
     * Split text into paragraphs based on double line breaks
     */
    private List<String> splitIntoParagraphs(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> paragraphs = new ArrayList<>();
        StringBuilder currentParagraph = new StringBuilder();
        boolean lastWasNewline = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '\n') {
                if (lastWasNewline) {
                    // Two consecutive newlines - end of paragraph
                    if (currentParagraph.length() > 0) {
                        paragraphs.add(currentParagraph.toString().trim());
                        currentParagraph = new StringBuilder();
                    }
                    lastWasNewline = false; // Reset to avoid creating empty paragraph
                } else {
                    lastWasNewline = true;
                    currentParagraph.append(' '); // Replace single newline with space
                }
            } else if (!Character.isWhitespace(c) || !lastWasNewline) {
                // Only skip whitespace after newline
                currentParagraph.append(c);
                lastWasNewline = false;
            }
        }
        
        // Add last paragraph if any
        if (currentParagraph.length() > 0) {
            paragraphs.add(currentParagraph.toString().trim());
        }
        
        return paragraphs;
    }
    
    /**
     * Split text into sentences without using complex regex
     */
    private List<String> splitIntoSentences(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> sentences = new ArrayList<>();
        StringBuilder currentSentence = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            currentSentence.append(c);
            
            // Check for end of sentence markers followed by whitespace
            if ((c == '.' || c == '!' || c == '?') && 
                (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1)))) {
                sentences.add(currentSentence.toString().trim());
                currentSentence = new StringBuilder();
                // Skip the whitespace
                i++;
            }
        }
        
        // Add the last sentence if there's anything left
        if (currentSentence.length() > 0) {
            sentences.add(currentSentence.toString().trim());
        }
        
        return sentences;
    }
}