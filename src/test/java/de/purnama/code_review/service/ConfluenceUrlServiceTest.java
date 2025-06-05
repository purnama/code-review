package de.purnama.code_review.service;

import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.exception.ConfluenceUrlException;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ConfluenceUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfluenceUrlServiceTest {
    @Mock ConfluenceUrlRepository confluenceUrlRepository;
    @Mock ConfluenceService confluenceService;
    @Mock EmbeddingService embeddingService;
    @InjectMocks ConfluenceUrlService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test void findAll_returnsList() throws ConfluenceException {
        // Arrange
        List<ConfluenceUrl> expectedUrls = Arrays.asList(
            new ConfluenceUrl(),
            new ConfluenceUrl()
        );
        when(confluenceUrlRepository.findAll()).thenReturn(expectedUrls);

        // Act
        List<ConfluenceUrl> result = service.findAll();

        // Assert
        assertEquals(expectedUrls, result);
        verify(confluenceUrlRepository).findAll();
    }

    @Test void findById_returnsOptional() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl expectedUrl = new ConfluenceUrl();
        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(expectedUrl));

        // Act
        Optional<ConfluenceUrl> result = service.findById(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedUrl, result.get());
        verify(confluenceUrlRepository).findById(id);
    }

    @Test void findByUrl_returnsOptional() throws ConfluenceException {
        // Arrange
        String url = "https://confluence.example.com/page";
        ConfluenceUrl expectedUrl = new ConfluenceUrl();
        when(confluenceUrlRepository.findByUrl(url)).thenReturn(Optional.of(expectedUrl));

        // Act
        Optional<ConfluenceUrl> result = service.findByUrl(url);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedUrl, result.get());
        verify(confluenceUrlRepository).findByUrl(url);
    }

    @Test void save_newUrl_fetchesTitleAndProcessesContent() throws ConfluenceException {
        // Arrange
        ConfluenceUrl newUrl = new ConfluenceUrl();
        newUrl.setUrl("https://confluence.example.com/page");
        newUrl.setActive(true);

        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/page");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);

        List<ContentBlock> contentBlocks = Arrays.asList(new ContentBlock(), new ContentBlock());

        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.fetchConfluenceContent(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.processContentIntoBlocks(any(ConfluenceUrl.class))).thenReturn(contentBlocks);

        // Act
        ConfluenceUrl result = service.save(newUrl);

        // Assert
        assertEquals(savedUrl, result);
        // Allow any number of calls to fetchConfluenceContent since the implementation may call it multiple times
        verify(confluenceService, atLeastOnce()).fetchConfluenceContent(any(ConfluenceUrl.class));
        verify(confluenceService).processContentIntoBlocks(any(ConfluenceUrl.class));
        verify(embeddingService, times(contentBlocks.size())).generateAndSaveEmbedding(any(ContentBlock.class));
    }

    @Test void save_existingUrl_processesContent() throws ConfluenceException {
        // Arrange
        ConfluenceUrl existingUrl = new ConfluenceUrl();
        existingUrl.setId(1L);
        existingUrl.setUrl("https://confluence.example.com/page");
        existingUrl.setTitle("Test Page");
        existingUrl.setActive(true);
        existingUrl.setHtmlContent("<html>Test content</html>");

        List<ContentBlock> contentBlocks = Arrays.asList(new ContentBlock(), new ContentBlock());

        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(existingUrl);
        when(confluenceService.processContentIntoBlocks(any(ConfluenceUrl.class))).thenReturn(contentBlocks);

        // Act
        ConfluenceUrl result = service.save(existingUrl);

        // Assert
        assertEquals(existingUrl, result);
        verify(confluenceService, never()).fetchConfluenceContent(any(ConfluenceUrl.class));
        verify(confluenceService).processContentIntoBlocks(any(ConfluenceUrl.class));
        verify(embeddingService, times(contentBlocks.size())).generateAndSaveEmbedding(any(ContentBlock.class));
        verify(confluenceUrlRepository, times(2)).save(any(ConfluenceUrl.class)); // Initial save + update with lastFetched
    }

    @Test void save_handlesConfluenceException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl newUrl = new ConfluenceUrl();
        newUrl.setUrl("https://confluence.example.com/page");
        newUrl.setActive(true);

        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/page");
        savedUrl.setActive(true);

        // Mock behavior to set the title and then throw exception
        doAnswer(invocation -> {
            ConfluenceUrl url = invocation.getArgument(0);
            url.setTitle("Untitled Confluence Page");
            throw new ConfluenceException("Failed to connect to Confluence");
        }).when(confluenceService).fetchConfluenceContent(any(ConfluenceUrl.class));

        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);

        // Act
        ConfluenceUrl result = service.save(newUrl);

        // Assert
        assertEquals(savedUrl, result);
        assertEquals("Untitled Confluence Page", newUrl.getTitle());
        // The method is called exactly twice - once in fetchTitleFromConfluence and once in fetchAndProcessContent
        verify(confluenceService, times(2)).fetchConfluenceContent(any(ConfluenceUrl.class));
        verify(confluenceService, never()).processContentIntoBlocks(any(ConfluenceUrl.class));
    }

    @Test void save_handlesGenericException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl newUrl = new ConfluenceUrl();
        newUrl.setUrl("https://confluence.example.com/page");
        newUrl.setActive(true);

        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/page");
        savedUrl.setActive(true);

        // Mock behavior to set the title and then throw exception
        doAnswer(invocation -> {
            ConfluenceUrl url = invocation.getArgument(0);
            url.setTitle("Untitled Confluence Page");
            throw new RuntimeException("Unexpected error");
        }).when(confluenceService).fetchConfluenceContent(any(ConfluenceUrl.class));

        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);

        // Act
        ConfluenceUrl result = service.save(newUrl);

        // Assert
        assertEquals(savedUrl, result);
        assertEquals("Untitled Confluence Page", newUrl.getTitle());
        // The method is called exactly twice - once in fetchTitleFromConfluence and once in fetchAndProcessContent
        verify(confluenceService, times(2)).fetchConfluenceContent(any(ConfluenceUrl.class));
        verify(confluenceService, never()).processContentIntoBlocks(any(ConfluenceUrl.class));
    }

    @Test void delete_deletesById() throws ConfluenceException {
        // Arrange
        Long id = 1L;

        // Act
        service.delete(id);

        // Assert
        verify(confluenceUrlRepository).deleteById(id);
    }

    @Test void toggleActive_togglesAndSaves() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        url.setActive(false); // Initially inactive

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));

        // Act
        boolean result = service.toggleActive(id);

        // Assert
        assertTrue(result);
        assertTrue(url.isActive()); // Should be toggled to active
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceUrlRepository).save(url);
    }

    @Test void toggleActive_togglesFromActiveToInactive() {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        url.setActive(true); // Initially active

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));

        // Act
        boolean result = service.toggleActive(id);

        // Assert
        assertTrue(result);
        assertFalse(url.isActive()); // Should be toggled to inactive
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceUrlRepository).save(url);
    }

    @Test void toggleActive_returnsFalseIfNotFound() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        boolean result = service.toggleActive(id);

        // Assert
        assertFalse(result);
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceUrlRepository, never()).save(any(ConfluenceUrl.class));
    }

    @Test void updateLastFetched_updatesTimestamp() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        LocalDateTime beforeUpdate = LocalDateTime.now().minusHours(1);
        url.setLastFetched(beforeUpdate);

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));

        // Act
        service.updateLastFetched(id);

        // Assert
        assertNotEquals(beforeUpdate, url.getLastFetched());
        assertNotNull(url.getLastFetched());
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceUrlRepository).save(url);
    }

    @Test void updateLastFetched_doesNothingIfNotFound() throws ConfluenceException {
        // Arrange
        Long id = 999L;
        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.empty());

        // Act - should not throw any exception
        service.updateLastFetched(id);

        // Assert
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceUrlRepository, never()).save(any(ConfluenceUrl.class));
    }

    @Test void refreshContent_success() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        url.setUrl("https://confluence.example.com/page");
        url.setTitle("Test Page");
        url.setActive(true);

        List<ContentBlock> contentBlocks = Arrays.asList(new ContentBlock(), new ContentBlock());

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));
        when(confluenceService.fetchConfluenceContent(any(ConfluenceUrl.class))).thenReturn(url);
        when(confluenceService.processContentIntoBlocks(any(ConfluenceUrl.class))).thenReturn(contentBlocks);

        // Act
        service.refreshContent(id);

        // Assert
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceService).fetchConfluenceContent(url);
        verify(confluenceService).processContentIntoBlocks(url);
        verify(embeddingService, times(contentBlocks.size())).generateAndSaveEmbedding(any(ContentBlock.class));
        verify(confluenceUrlRepository).save(url);
        assertNotNull(url.getLastFetched());
    }

    @Test void refreshContent_handlesConfluenceException() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        url.setUrl("https://confluence.example.com/page");

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));
        when(confluenceService.fetchConfluenceContent(any(ConfluenceUrl.class)))
            .thenThrow(new ConfluenceException("Failed to connect"));

        // Act & Assert
        ConfluenceUrlException exception = assertThrows(ConfluenceUrlException.class, () -> {
            service.refreshContent(id);
        });

        assertTrue(exception.getMessage().contains("Failed to refresh content"));
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceService).fetchConfluenceContent(url);
    }

    @Test void refreshContent_handlesGenericException() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(id);
        url.setUrl("https://confluence.example.com/page");

        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.of(url));
        when(confluenceService.fetchConfluenceContent(any(ConfluenceUrl.class)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        ConfluenceUrlException exception = assertThrows(ConfluenceUrlException.class, () -> {
            service.refreshContent(id);
        });

        assertTrue(exception.getMessage().contains("Failed to refresh content"));
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceService).fetchConfluenceContent(url);
    }

    @Test void refreshContent_notFound_throws() throws ConfluenceException {
        // Arrange
        Long id = 999L;
        when(confluenceUrlRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        ConfluenceUrlException exception = assertThrows(ConfluenceUrlException.class, () -> {
            service.refreshContent(id);
        });

        assertTrue(exception.getMessage().contains("not found"));
        verify(confluenceUrlRepository).findById(id);
        verify(confluenceService, never()).fetchConfluenceContent(any(ConfluenceUrl.class));
    }

    @Test void findByIdWithContentBlocks_returnsOptional() throws ConfluenceException {
        // Arrange
        Long id = 1L;
        ConfluenceUrl expectedUrl = new ConfluenceUrl();
        expectedUrl.setId(id);
        expectedUrl.setTitle("Test Page with Blocks");

        List<ContentBlock> contentBlocks = Arrays.asList(
            new ContentBlock(),
            new ContentBlock()
        );
        expectedUrl.setContentBlocks(contentBlocks);

        when(confluenceUrlRepository.findByIdWithContentBlocks(id)).thenReturn(Optional.of(expectedUrl));

        // Act
        Optional<ConfluenceUrl> result = service.findByIdWithContentBlocks(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedUrl, result.get());
        assertEquals(contentBlocks, result.get().getContentBlocks());
        verify(confluenceUrlRepository).findByIdWithContentBlocks(id);
    }

    @Test
    void save_whenIdNullAndTitleEmpty_shouldFetchTitle() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle(""); // Empty title
        url.setActive(false); // Set inactive to avoid the second fetch in processContentIfActive
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(url);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService, times(1)).fetchConfluenceContent(url); // Only for title fetching
        verify(confluenceUrlRepository, times(1)).save(url);
    }
    
    @Test
    void save_whenIdNullAndTitleNull_shouldFetchTitle() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle(null); // Null title
        url.setActive(false); // Set inactive to avoid the second fetch in processContentIfActive
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(url);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService, times(1)).fetchConfluenceContent(url); // Only for title fetching
        verify(confluenceUrlRepository, times(1)).save(url);
    }
    
    @Test
    void save_whenIdNotNullOrTitlePresent_shouldNotFetchTitle() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L); // ID is not null
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Existing Title");
        url.setActive(false); // Set inactive to avoid any fetches in processContentIfActive
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(url);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService, never()).fetchConfluenceContent(url); // Should not be called for title fetching
        verify(confluenceUrlRepository, times(1)).save(url);
    }
    
    @Test
    void save_whenActiveAndHtmlContentPresent_shouldProcessExistingContent() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent("<html><body>Test content</body></html>");
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent("<html><body>Test content</body></html>");
        
        List<ContentBlock> contentBlocks = List.of(new ContentBlock());
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.processContentIntoBlocks(savedUrl)).thenReturn(contentBlocks);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService).processContentIntoBlocks(savedUrl);
        verify(embeddingService).generateAndSaveEmbedding(any(ContentBlock.class));
        verify(confluenceUrlRepository, times(2)).save(any(ConfluenceUrl.class)); // Once for initial save, once after processing
    }
    
    @Test
    void save_whenActiveAndHtmlContentNull_shouldFetchAndProcessContent() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent(null);
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent(null);
        
        List<ContentBlock> contentBlocks = List.of(new ContentBlock());
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.fetchConfluenceContent(savedUrl)).thenReturn(savedUrl);
        when(confluenceService.processContentIntoBlocks(savedUrl)).thenReturn(contentBlocks);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService).fetchConfluenceContent(savedUrl);
        verify(confluenceService).processContentIntoBlocks(savedUrl);
        verify(embeddingService).generateAndSaveEmbedding(any(ContentBlock.class));
    }
    
    @Test
    void save_whenNotActive_shouldNotProcessContent() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(false);
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(false);
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        
        // Act
        service.save(url);
        
        // Assert
        verify(confluenceService, never()).fetchConfluenceContent(any(ConfluenceUrl.class));
        verify(confluenceService, never()).processContentIntoBlocks(any(ConfluenceUrl.class));
    }
    
    @Test
    void processExistingHtmlContent_handlesConfluenceException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent("<html><body>Test content</body></html>");
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent("<html><body>Test content</body></html>");
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.processContentIntoBlocks(savedUrl))
            .thenThrow(new ConfluenceException("Error processing content"));
        
        // Act
        ConfluenceUrl result = service.save(url);
        
        // Assert
        assertEquals(savedUrl, result);
        verify(confluenceService).processContentIntoBlocks(savedUrl);
        // Should not attempt to generate embeddings
        verify(embeddingService, never()).generateAndSaveEmbedding(any(ContentBlock.class));
    }

    @Test
    void processExistingHtmlContent_handlesGenericException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent("<html><body>Test content</body></html>");
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent("<html><body>Test content</body></html>");
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.processContentIntoBlocks(savedUrl))
            .thenThrow(new RuntimeException("Unexpected error processing content"));
        
        // Act
        ConfluenceUrl result = service.save(url);
        
        // Assert
        assertEquals(savedUrl, result);
        verify(confluenceService).processContentIntoBlocks(savedUrl);
        // Should not attempt to generate embeddings
        verify(embeddingService, never()).generateAndSaveEmbedding(any(ContentBlock.class));
    }
    
    @Test
    void fetchAndProcessContent_handlesConfluenceException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent(null); // No HTML content, so it will try to fetch
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent(null);
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.fetchConfluenceContent(savedUrl))
            .thenThrow(new ConfluenceException("Error fetching content"));
        
        // Act
        ConfluenceUrl result = service.save(url);
        
        // Assert
        assertEquals(savedUrl, result);
        verify(confluenceService).fetchConfluenceContent(savedUrl);
        verify(confluenceService, never()).processContentIntoBlocks(any(ConfluenceUrl.class));
        verify(embeddingService, never()).generateAndSaveEmbedding(any(ContentBlock.class));
    }
    
    @Test
    void fetchAndProcessContent_handlesGenericException() throws ConfluenceException {
        // Arrange
        ConfluenceUrl url = new ConfluenceUrl();
        url.setId(1L);
        url.setUrl("https://confluence.example.com/pages/123456");
        url.setTitle("Test Page");
        url.setActive(true);
        url.setHtmlContent(null); // No HTML content, so it will try to fetch
        
        ConfluenceUrl savedUrl = new ConfluenceUrl();
        savedUrl.setId(1L);
        savedUrl.setUrl("https://confluence.example.com/pages/123456");
        savedUrl.setTitle("Test Page");
        savedUrl.setActive(true);
        savedUrl.setHtmlContent(null);
        
        when(confluenceUrlRepository.save(any(ConfluenceUrl.class))).thenReturn(savedUrl);
        when(confluenceService.fetchConfluenceContent(savedUrl))
            .thenThrow(new RuntimeException("Unexpected error fetching content"));
        
        // Act
        ConfluenceUrl result = service.save(url);
        
        // Assert
        assertEquals(savedUrl, result);
        verify(confluenceService).fetchConfluenceContent(savedUrl);
        verify(confluenceService, never()).processContentIntoBlocks(any(ConfluenceUrl.class));
        verify(embeddingService, never()).generateAndSaveEmbedding(any(ContentBlock.class));
    }
}
