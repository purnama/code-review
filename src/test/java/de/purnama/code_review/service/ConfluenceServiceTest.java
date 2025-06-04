package de.purnama.code_review.service;

import de.purnama.code_review.config.ConfluenceConfig;
import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
}
