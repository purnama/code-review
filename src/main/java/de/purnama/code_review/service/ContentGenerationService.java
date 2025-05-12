package de.purnama.code_review.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.model.ConfluenceUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Service responsible for generating structured content from Confluence pages
 * using AI models for metadata extraction and analysis.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGenerationService {

    private final ChatModel chatModel;
    private final ConfluenceService confluenceService;

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that analyzes Confluence page content and generates metadata.
            Given the content from a Confluence page, generate:
            1. A brief description (2-3 sentences) that explains what this content covers
            
            Return your response in JSON format with one field:
            - description: The generated description
            
            Do not include any additional text, explanations, or markdown in your response.
            Just return the JSON object.
            """;

    /**
     * Generates a description for a Confluence URL by first fetching and analyzing its content
     *
     * @param confluenceUrl The Confluence URL object containing the URL to fetch from
     * @return The same ConfluenceUrl object with description populated
     */
    public ConfluenceUrl generateTitleAndDescription(ConfluenceUrl confluenceUrl) {
        try {
            // First fetch raw content from Confluence
            log.info("Fetching content from Confluence URL: {}", confluenceUrl.getUrl());

            ConfluenceUrl updatedUrl;
            try {
                updatedUrl = confluenceService.fetchConfluenceContent(confluenceUrl);
            } catch (ConfluenceException e) {
                log.error("Failed to fetch content from Confluence: {}", e.getMessage());
                setFallbackMetadata(confluenceUrl);
                return confluenceUrl;
            }

            if (updatedUrl == null || updatedUrl.getHtmlContent() == null || updatedUrl.getHtmlContent().isEmpty()) {
                log.warn("No content extracted from URL: {}", confluenceUrl.getUrl());
                setFallbackMetadata(confluenceUrl);
                return confluenceUrl;
            }

            // We already have the title from Confluence, now generate the description
            String htmlContent = updatedUrl.getHtmlContent();

            // Convert HTML to plain text for OpenAI to process
            String plainText = htmlContent.replaceAll("<[^>]*>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Truncate if too long
            if (plainText.length() > 4000) {
                plainText = plainText.substring(0, 4000) + "...";
            }

            // Log a sample of the content to help with debugging
            log.info("Content sample (first 200 chars): {}",
                    plainText.length() > 200 ? plainText.substring(0, 200) + "..." : plainText);

            // Generate just the description using OpenAI
            String description = generateDescription(plainText);

            // Update the confluenceUrl with the generated description
            confluenceUrl.setDescription(description);

            log.info("Generated description for URL: {}", confluenceUrl.getUrl());
            return confluenceUrl;
        } catch (Exception e) {
            log.error("Error generating description for URL: {}", confluenceUrl.getUrl(), e);
            setFallbackMetadata(confluenceUrl);
            return confluenceUrl;
        }
    }

    /**
     * Generate a description for the content using OpenAI
     */
    private String generateDescription(String content) {
        try {
            SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT);
            UserMessage userMessage = new UserMessage("Generate metadata for this Confluence content:\n\n" + content);

            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            log.info("Sending prompt to OpenAI with content length: {}", content.length());

            ChatResponse response = chatModel.call(prompt);
            String responseText = response.getResult().getOutput().getText();

            log.info("Received response from OpenAI: {}", responseText);

            // Parse the JSON response
            responseText = responseText.trim();

            // Strip any markdown code block formatting if present
            if (responseText.startsWith("```json")) {
                responseText = responseText.substring(7);
            }
            if (responseText.startsWith("```")) {
                responseText = responseText.substring(3);
            }
            if (responseText.endsWith("```")) {
                responseText = responseText.substring(0, responseText.length() - 3);
            }

            responseText = responseText.trim();

            // Use Jackson to parse the JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, String> result = mapper.readValue(responseText, Map.class);

            return result.get("description");
        } catch (Exception e) {
            log.error("Error generating description: {}", e.getMessage());
            return "Content from Confluence page with various code examples and technical information.";
        }
    }

    /**
     * Sets fallback metadata on the ConfluenceUrl object when content extraction or AI generation fails
     */
    private void setFallbackMetadata(ConfluenceUrl confluenceUrl) {
        log.warn("Using fallback metadata for URL: {}", confluenceUrl.getUrl());
        if (confluenceUrl.getTitle() == null || confluenceUrl.getTitle().isEmpty()) {
            confluenceUrl.setTitle("Confluence Documentation");
        }
        confluenceUrl.setDescription("Content from Confluence page: " + confluenceUrl.getUrl());
    }
}