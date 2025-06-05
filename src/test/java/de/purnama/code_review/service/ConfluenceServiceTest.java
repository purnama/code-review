package de.purnama.code_review.service;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.purnama.code_review.config.ConfluenceConfig;
import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import reactor.core.publisher.Mono;

class ConfluenceServiceTest {
    @Mock ConfluenceConfig confluenceConfig;
    @Mock ContentBlockService contentBlockService;
    @Mock WebClient.Builder webClientBuilder;
    @InjectMocks ConfluenceService service;

    // Mock objects for WebClient chain
    private WebClient mockWebClient;
    private WebClient.RequestHeadersUriSpec mockUriSpec;
    private WebClient.RequestHeadersSpec mockHeadersSpec;
    private WebClient.ResponseSpec mockResponseSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize mock objects for WebClient chain
        mockWebClient = mock(WebClient.class);
        mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        mockResponseSpec = mock(WebClient.ResponseSpec.class);

        // Set up common WebClient behavior
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(mockWebClient);
        when(mockWebClient.get()).thenReturn(mockUriSpec);
    }

    @Test
    void fetchConfluenceContent_success() throws Exception {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/12345");
        String htmlContent = "<h1>Title</h1><p>Content</p>";
        String title = "Test Page";

        // Mock config
        when(confluenceConfig.getBaseUrl()).thenReturn("https://confluence.example.com");
        when(confluenceConfig.getUsername()).thenReturn("user");
        when(confluenceConfig.getApiToken()).thenReturn("token");

        // Set up WebClient chain - exact match with service implementation
        when(mockUriSpec.uri(eq("/wiki/rest/api/content/{pageId}?expand=body.storage"), eq("12345"))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);

        // Mock JSON response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).putObject("body").putObject("storage").put("value", htmlContent);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("title", title);
        when(mockResponseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(node));

        // Act
        ConfluenceUrl result = service.fetchConfluenceContent(url);

        // Assert
        assertEquals(htmlContent, result.getHtmlContent());
        assertEquals(title, result.getTitle());
    }

    @Test
    void fetchConfluenceContent_missingPageId() {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/invalid-url-format");

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.fetchConfluenceContent(url);
        });

        assertTrue(exception.getMessage().contains("Could not extract page ID from URL"));
    }

    @Test
    void fetchConfluenceContent_nullContent() {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/12345");

        // Mock config
        when(confluenceConfig.getBaseUrl()).thenReturn("https://confluence.example.com");
        when(confluenceConfig.getUsername()).thenReturn("user");
        when(confluenceConfig.getApiToken()).thenReturn("token");

        // Set up WebClient chain - exact match with service implementation
        when(mockUriSpec.uri(eq("/wiki/rest/api/content/{pageId}?expand=body.storage"), eq("12345"))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);

        // Return null content from API
        when(mockResponseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.fetchConfluenceContent(url);
        });

        // Check the message - it should contain "Failed to retrieve content"
        String message = exception.getMessage();
        assertTrue(message.contains("Failed to retrieve content"), "Expected message to contain 'Failed to retrieve content', but was: " + message);
    }

    @Test
    void fetchConfluenceContent_emptyHtml() {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/12345");
        String title = "Test Page";

        // Mock config
        when(confluenceConfig.getBaseUrl()).thenReturn("https://confluence.example.com");
        when(confluenceConfig.getUsername()).thenReturn("user");
        when(confluenceConfig.getApiToken()).thenReturn("token");

        // Set up WebClient chain - exact match with service implementation
        when(mockUriSpec.uri(eq("/wiki/rest/api/content/{pageId}?expand=body.storage"), eq("12345"))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);

        // Mock JSON response with empty HTML
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).putObject("body").putObject("storage").put("value", "");
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("title", title);
        when(mockResponseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(node));

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.fetchConfluenceContent(url);
        });

        // Check the message - it should contain "Empty HTML content received"
        String message = exception.getMessage();
        assertTrue(message.contains("Empty HTML content received"), "Expected message to contain 'Empty HTML content received', but was: " + message);
    }

    @Test
    void fetchConfluenceContent_genericException() {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/12345");

        // Mock config
        when(confluenceConfig.getBaseUrl()).thenReturn("https://confluence.example.com");
        when(confluenceConfig.getUsername()).thenReturn("user");
        when(confluenceConfig.getApiToken()).thenReturn("token");

        // Mock WebClient to throw exception - exact match with service implementation
        when(mockUriSpec.uri(eq("/wiki/rest/api/content/{pageId}?expand=body.storage"), eq("12345"))).thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.fetchConfluenceContent(url);
        });

        assertTrue(exception.getMessage().contains("Error fetching content from Confluence"));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    void processContentIntoBlocks_success() throws Exception {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setUrl("https://confluence.example.com/pages/12345");
        confluenceUrl.setHtmlContent("<h1>Title</h1><p>First paragraph content.</p><h2>Section</h2><p>Second paragraph with more content.</p>");

        // Create expected ContentBlocks
        List<ContentBlock> expectedBlocks = List.of(
            ContentBlock.builder()
                .confluenceUrl(confluenceUrl)
                .content("Title First paragraph content.")
                .sequence(1)
                .title("Title First paragraph content.")
                .build(),
            ContentBlock.builder()
                .confluenceUrl(confluenceUrl)
                .content("Section Second paragraph with more content.")
                .sequence(2)
                .title("Section Second paragraph with more content.")
                .build()
        );

        // Mock contentBlockService - use doNothing for void method
        doNothing().when(contentBlockService).deleteByConfluenceUrl(any());
        when(contentBlockService.saveAll(anyList())).thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> result = service.processContentIntoBlocks(confluenceUrl);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(contentBlockService).deleteByConfluenceUrl(confluenceUrl);
        verify(contentBlockService).saveAll(anyList());
    }

    @Test
    void processContentIntoBlocks_noHtml() {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setUrl("https://confluence.example.com/pages/12345");
        confluenceUrl.setHtmlContent(null); // No HTML content

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.processContentIntoBlocks(confluenceUrl);
        });

        assertTrue(exception.getMessage().contains("No HTML content available"));
        // Verify that deleteByConfluenceUrl was not called
        verify(contentBlockService, never()).deleteByConfluenceUrl(any());
    }

    @Test
    void processContentIntoBlocks_genericException() {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setUrl("https://confluence.example.com/pages/12345");
        confluenceUrl.setHtmlContent("<h1>Title</h1><p>Content</p>");

        // Mock contentBlockService to throw exception
        doThrow(new RuntimeException("Database error")).when(contentBlockService).deleteByConfluenceUrl(any());

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.processContentIntoBlocks(confluenceUrl);
        });

        assertTrue(exception.getMessage().contains("Error processing content into blocks"));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    void extractContentFromConfluence_success() throws Exception {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/12345");
        String htmlContent = "<h1>Title</h1><p>Content</p>";
        String title = "Test Page";
        List<ContentBlock> expectedBlocks = List.of(
            ContentBlock.builder()
                .content("Title Content")
                .sequence(1)
                .title("Title Content")
                .build()
        );

        // Mock config
        when(confluenceConfig.getBaseUrl()).thenReturn("https://confluence.example.com");
        when(confluenceConfig.getUsername()).thenReturn("user");
        when(confluenceConfig.getApiToken()).thenReturn("token");

        // Set up WebClient chain - exact match with service implementation
        when(mockUriSpec.uri(eq("/wiki/rest/api/content/{pageId}?expand=body.storage"), eq("12345"))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);

        // Mock JSON response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).putObject("body").putObject("storage").put("value", htmlContent);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("title", title);
        when(mockResponseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(node));

        // Mock contentBlockService - use doNothing for void method
        doNothing().when(contentBlockService).deleteByConfluenceUrl(any());
        when(contentBlockService.saveAll(anyList())).thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> result = service.extractContentFromConfluence(url);

        // Assert
        assertNotNull(result);
        assertEquals(expectedBlocks.size(), result.size());
        verify(contentBlockService).saveAll(anyList());
    }

    @Test
    void extractContentFromConfluence_error() {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/invalid-url-format");

        // Act & Assert
        ConfluenceException exception = assertThrows(ConfluenceException.class, () -> {
            service.extractContentFromConfluence(url);
        });

        assertTrue(exception.getMessage().contains("Could not extract page ID from URL"));
    }

    @Test
    void testSplitIntoParagraphs() throws Exception {
        // Get access to the private method
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoParagraphs", String.class);
        method.setAccessible(true);
        
        // Test with null input
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, (String) null);
        assertEquals(0, result.size(), "Null input should return empty list");
        
        // Test with empty string
        @SuppressWarnings("unchecked")
        List<String> emptyResult = (List<String>) method.invoke(service, "");
        assertEquals(0, emptyResult.size(), "Empty string should return empty list");
        
        // Test with single paragraph
        @SuppressWarnings("unchecked")
        List<String> singleResult = (List<String>) method.invoke(service, "This is a single paragraph.");
        assertEquals(1, singleResult.size(), "Should create a single paragraph");
        assertEquals("This is a single paragraph.", singleResult.get(0), "Content should match");
        
        // Test with multiple paragraphs separated by double newlines
        @SuppressWarnings("unchecked")
        List<String> multiResult = (List<String>) method.invoke(service, "First paragraph.\n\nSecond paragraph.");
        assertEquals(2, multiResult.size(), "Should create two paragraphs");
        assertEquals("First paragraph.", multiResult.get(0), "First paragraph content should match");
        assertEquals("Second paragraph.", multiResult.get(1), "Second paragraph content should match");
        
        // Test with consecutive newlines (more than two)
        @SuppressWarnings("unchecked")
        List<String> manyNewlinesResult = (List<String>) method.invoke(service, "Para 1.\n\n\n\nPara 2.");
        assertEquals(2, manyNewlinesResult.size(), "Should handle multiple consecutive newlines");
        assertEquals("Para 1.", manyNewlinesResult.get(0), "First paragraph with multiple newlines should match");
        assertEquals("Para 2.", manyNewlinesResult.get(1), "Second paragraph with multiple newlines should match");
        
        // Test with single newlines (should be treated as spaces)
        @SuppressWarnings("unchecked")
        List<String> singleNewlinesResult = (List<String>) method.invoke(service, "This has\na single\nnewline.");
        assertEquals(1, singleNewlinesResult.size(), "Single newlines should not create paragraphs");
        assertTrue(singleNewlinesResult.get(0).contains("This has a single newline."), "Single newlines should be replaced with spaces");
    }

    @Test
    void testSplitIntoChunks_Simple() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoChunks", String.class);
        method.setAccessible(true);
        
        // Test with simple text
        String text = "This is a simple test.";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, text);
        
        // Simple text should be kept as one chunk since it's under MAX_CHUNK_SIZE
        assertEquals(1, result.size());
        assertEquals("This is a simple test.", result.get(0));
    }

    @Test
    void testSplitIntoChunks_LongText() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoChunks", String.class);
        method.setAccessible(true);
        
        // Test with long text
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longText.append("word").append(i).append(" ");
        }
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, longText.toString());
        
        // Should be split into multiple chunks
        assertTrue(result.size() > 1);
        
        // Each chunk should be smaller than MAX_CHUNK_SIZE (1000)
        for (String chunk : result) {
            assertTrue(chunk.length() <= 1000);
        }
    }
    
    @Test
    void testSplitIntoChunks_SpecialCharacters() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoChunks", String.class);
        method.setAccessible(true);
        
        // Test with text containing special characters
        String text = "This is a test with special characters: !@#$%^&*()_+";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, text);
        
        // Special character text should be kept as one chunk since it's under MAX_CHUNK_SIZE
        assertEquals(1, result.size());
        assertEquals("This is a test with special characters: !@#$%^&*()_+", result.get(0));
    }
    
    @Test
    void testExtractTitle_Simple() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("extractTitle", String.class);
        method.setAccessible(true);
        
        // Test with simple title
        String title = "This is a test title.";
        String result = (String) method.invoke(service, title);
        
        assertEquals("This is a test title.", result);
    }
    
    @Test
    void testExtractTitle_LongTitle() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("extractTitle", String.class);
        method.setAccessible(true);
        
        // Test with a very long title
        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longTitle.append("word").append(i).append(" ");
        }
        
        String result = (String) method.invoke(service, longTitle.toString());
        
        // Should be truncated to MAX_TITLE_LENGTH (100) with ellipsis (...)
        assertTrue(result.startsWith("word0 word1"));
        assertTrue(result.endsWith("..."));
        
        // Should contain approximately 10 words
        String[] words = result.split("\\s+");
        assertTrue(words.length <= 11);  // 10 words plus the ellipsis counted as a word
    }
    
    @Test
    void testExtractTitle_NoWords() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("extractTitle", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(service, "");
        assertEquals("", result);
    }
    
    @Test
    void testExtractPageIdFromUrl_StandardPattern() throws Exception {
        // Use reflection to test private method
        Method method = ConfluenceService.class.getDeclaredMethod("extractPageIdFromUrl", String.class);
        method.setAccessible(true);
        
        // Test with null URL
        String result = (String) method.invoke(service, (Object) null);
        assertNull(result, "Null URL should return null");
        
        // Test with standard Confluence Cloud URL pattern
        result = (String) method.invoke(service, "https://confluence.example.com/pages/123456");
        assertEquals("123456", result, "Should extract numeric page ID from standard URL");
        
        // Test with trailing slash
        result = (String) method.invoke(service, "https://confluence.example.com/pages/123456/");
        assertEquals("123456", result, "Should extract numeric page ID even with trailing slash");
        
        // Test with additional path components
        result = (String) method.invoke(service, "https://confluence.example.com/pages/123456/page-title");
        assertEquals("123456", result, "Should extract numeric page ID with additional path components");
    }
    
    @Test
    void testExtractPageIdFromUrl_QueryParameter() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("extractPageIdFromUrl", String.class);
        method.setAccessible(true);
        
        // Test with pageId query parameter
        String result = (String) method.invoke(service, "https://confluence.example.com/wiki?pageId=789012");
        assertEquals("789012", result, "Should extract numeric page ID from query parameter");
        
        // Test with pageId query parameter as part of other parameters
        result = (String) method.invoke(service, "https://confluence.example.com/wiki?key=value&pageId=789012&foo=bar");
        assertEquals("789012", result, "Should extract numeric page ID from query parameter with other parameters");
    }
    
    @Test
    void testExtractPageIdFromUrl_InvalidValues() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("extractPageIdFromUrl", String.class);
        method.setAccessible(true);
        
        // Test with non-numeric page ID in standard pattern
        String result = (String) method.invoke(service, "https://confluence.example.com/pages/abc123");
        assertNull(result, "Non-numeric page ID in standard URL should return null");
        
        // Test with non-numeric page ID in query parameter
        result = (String) method.invoke(service, "https://confluence.example.com/wiki?pageId=abc123");
        assertNull(result, "Non-numeric page ID in query parameter should return null");
        
        // Test with URL not containing page ID pattern
        result = (String) method.invoke(service, "https://confluence.example.com/wiki/something-else");
        assertNull(result, "URL without page ID pattern should return null");
    }
    
    @Test
    void testIsNumeric() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("isNumeric", String.class);
        method.setAccessible(true);
        
        // Test with null and empty string
        boolean result = (boolean) method.invoke(service, (Object) null);
        assertFalse(result);
        
        result = (boolean) method.invoke(service, "");
        assertFalse(result);
        
        // Test with various inputs
        result = (boolean) method.invoke(service, "123");
        assertTrue(result);
        
        result = (boolean) method.invoke(service, "123abc");
        assertFalse(result);
        
        result = (boolean) method.invoke(service, "123.45");
        assertFalse(result);
        
        result = (boolean) method.invoke(service, "-123");
        assertFalse(result);
    }
    
    @Test
    void testSplitIntoChunks_LargeChunk() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoChunks", String.class);
        method.setAccessible(true);
        
        // Create a sentence that is larger than MAX_CHUNK_SIZE (1000)
        StringBuilder largeSentence = new StringBuilder();
        for (int i = 0; i < 1200; i++) {
            largeSentence.append("x");
        }
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, largeSentence.toString());
        
        // Should be split into multiple chunks due to size
        assertTrue(result.size() > 1, "Text larger than MAX_CHUNK_SIZE should be split into multiple chunks");
        
        // Each chunk should be smaller than MAX_CHUNK_SIZE
        for (String chunk : result) {
            assertTrue(chunk.length() <= 1000, "Each chunk should be <= MAX_CHUNK_SIZE");
        }
    }
    
    @Test
    void testSplitIntoChunks_MultipleSentences() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoChunks", String.class);
        method.setAccessible(true);
        
        // Create text with multiple sentences that collectively exceed MAX_CHUNK_SIZE
        StringBuilder text = new StringBuilder();
        // First sentence (500 chars)
        for (int i = 0; i < 500; i++) {
            text.append("a");
        }
        text.append(". "); // End first sentence
        
        // Second sentence (600 chars) - this will cause the chunk to exceed MAX_CHUNK_SIZE when added
        for (int i = 0; i < 600; i++) {
            text.append("b");
        }
        text.append(". "); // End second sentence
        
        // Third sentence
        text.append("This is a short third sentence.");
        
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, text.toString());
        
        // Should have at least 2 chunks due to size constraints
        assertTrue(result.size() >= 2, "Text should be split into at least 2 chunks");
        
        // First chunk should contain the first sentence
        assertTrue(result.get(0).contains("a"), "First chunk should contain first sentence");
        
        // Second chunk should contain the second sentence
        boolean hasSecondSentence = false;
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i).contains("b")) {
                hasSecondSentence = true;
                break;
            }
        }
        assertTrue(hasSecondSentence, "A chunk should contain the second sentence");
    }
    
    @Test
    void testRemoveHtmlTags() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("removeHtmlTags", String.class);
        method.setAccessible(true);
        
        // Test with null input
        String result = (String) method.invoke(service, (String) null);
        assertEquals("", result, "Null input should return empty string");
        
        // Test with empty string
        result = (String) method.invoke(service, "");
        assertEquals("", result, "Empty string should return empty string");
        
        // Test with basic HTML
        result = (String) method.invoke(service, "<p>Hello world</p>");
        // Check content rather than exact spacing since tag removal adds spaces
        assertTrue(result.trim().equals("Hello world"), "Should remove HTML tags");
        
        // Test with nested HTML
        result = (String) method.invoke(service, "<div><p>Nested <strong>tags</strong> here</p></div>");
        // Check content rather than exact spacing
        assertTrue(result.trim().contains("Nested") && 
                 result.trim().contains("tags") && 
                 result.trim().contains("here"), 
                 "Should remove all nested HTML tags");
        
        // Test with unclosed tag - note that everything after the unclosed tag will be treated as part of the tag
        result = (String) method.invoke(service, "This has <unclosed tag");
        // The implementation considers all content after '<' as inside the tag, so only "This has " remains
        assertTrue(result.contains("This has"), "Should handle unclosed tags per implementation");
        
        // Test with invalid HTML
        result = (String) method.invoke(service, "Text with < and > characters");
        // After '<', everything until '>' is considered a tag
        assertTrue(result.contains("Text with") && result.contains("characters"),
                "Should handle invalid HTML per implementation");
    }
    
    @Test
    void testNormalizeWhitespace() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("normalizeWhitespace", String.class);
        method.setAccessible(true);
        
        // Test with null input
        String result = (String) method.invoke(service, (String) null);
        assertEquals("", result, "Null input should return empty string");
        
        // Test with empty string
        result = (String) method.invoke(service, "");
        assertEquals("", result, "Empty string should return empty string");
        
        // Test with normal text
        result = (String) method.invoke(service, "Hello world");
        assertEquals("Hello world", result, "Normal text should be unchanged");
        
        // Test with extra whitespace
        result = (String) method.invoke(service, "  Multiple    spaces   between   words  ");
        assertEquals("Multiple spaces between words", result, "Extra whitespace should be normalized");
        
        // Test with HTML entities
        result = (String) method.invoke(service, "Text with &lt;tags&gt; and &quot;quotes&quot;");
        assertEquals("Text with <tags> and \"quotes\"", result, "HTML entities should be replaced");
        
        // Test with mixed whitespace types (tabs, newlines, etc.)
        result = (String) method.invoke(service, "Text with\ttabs\nand\rnewlines");
        assertEquals("Text with tabs and newlines", result, "Different whitespace types should be normalized");
    }
    
    @Test
    void testSplitIntoWords_EmptyAndNull() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoWords", String.class);
        method.setAccessible(true);
        
        // Test with null input
        String[] result = (String[]) method.invoke(service, (String) null);
        assertEquals(0, result.length, "Null input should return empty array");
        
        // Test with empty string
        result = (String[]) method.invoke(service, "");
        assertEquals(0, result.length, "Empty string should return empty array");
    }
    
    @Test
    void testSplitIntoWords_LastWord() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoWords", String.class);
        method.setAccessible(true);
        
        // Test with text ending with a word (no trailing space)
        String[] result = (String[]) method.invoke(service, "First second third");
        assertEquals(3, result.length, "Should split into three words");
        assertEquals("third", result[2], "Last word should be included");
        
        // Test with text ending with whitespace
        result = (String[]) method.invoke(service, "First second third ");
        assertEquals(3, result.length, "Should split into three words even with trailing space");
        assertEquals("third", result[2], "Last word before trailing space should be included");
        
        // Test with single word
        result = (String[]) method.invoke(service, "Single");
        assertEquals(1, result.length, "Single word should be correctly identified");
        assertEquals("Single", result[0], "Single word should be included");
    }
    
    @Test
    void testSplitIntoSentences_NullAndEmpty() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoSentences", String.class);
        method.setAccessible(true);
        
        // Test with null input
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, (String) null);
        assertEquals(0, result.size(), "Null input should return empty list");
        
        // Test with empty string
        @SuppressWarnings("unchecked")
        List<String> emptyResult = (List<String>) method.invoke(service, "");
        assertEquals(0, emptyResult.size(), "Empty string should return empty list");
    }
    
    @Test
    void testSplitIntoSentences_EndMarkers() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoSentences", String.class);
        method.setAccessible(true);
        
        // Test with period as sentence terminator
        @SuppressWarnings("unchecked")
        List<String> resultPeriod = (List<String>) method.invoke(service, "First sentence. Second sentence.");
        assertEquals(2, resultPeriod.size(), "Should identify 2 sentences terminated by periods");
        assertEquals("First sentence.", resultPeriod.get(0), "First sentence with period should be correct");
        assertEquals("Second sentence.", resultPeriod.get(1), "Second sentence with period should be correct");
        
        // Test with exclamation mark as sentence terminator
        @SuppressWarnings("unchecked")
        List<String> resultExclam = (List<String>) method.invoke(service, "First sentence! Second sentence!");
        assertEquals(2, resultExclam.size(), "Should identify 2 sentences terminated by exclamation marks");
        assertEquals("First sentence!", resultExclam.get(0), "First sentence with exclamation should be correct");
        assertEquals("Second sentence!", resultExclam.get(1), "Second sentence with exclamation should be correct");
        
        // Test with question mark as sentence terminator
        @SuppressWarnings("unchecked")
        List<String> resultQuestion = (List<String>) method.invoke(service, "First sentence? Second sentence?");
        assertEquals(2, resultQuestion.size(), "Should identify 2 sentences terminated by question marks");
        assertEquals("First sentence?", resultQuestion.get(0), "First sentence with question mark should be correct");
        assertEquals("Second sentence?", resultQuestion.get(1), "Second sentence with question mark should be correct");
        
        // Test with mixed terminators
        @SuppressWarnings("unchecked")
        List<String> resultMixed = (List<String>) method.invoke(service, "First sentence. Second sentence! Third sentence?");
        assertEquals(3, resultMixed.size(), "Should identify 3 sentences with different terminators");
    }
    
    @Test
    void testSplitIntoSentences_NoTerminator() throws Exception {
        Method method = ConfluenceService.class.getDeclaredMethod("splitIntoSentences", String.class);
        method.setAccessible(true);
        
        // Test with no sentence terminator
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, "This is a sentence without terminator");
        assertEquals(1, result.size(), "Should treat unterminated text as a single sentence");
        assertEquals("This is a sentence without terminator", result.get(0), "Unterminated sentence should be included as-is");
        
        // Test with period but no following whitespace
        @SuppressWarnings("unchecked")
        List<String> resultNospace = (List<String>) method.invoke(service, "This sentence.Has no space after period");
        assertEquals(1, resultNospace.size(), "Should not split when period isn't followed by whitespace");
    }
}
