package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ContentBlockRepository;

@ExtendWith(MockitoExtension.class)
public class ContentBlockServiceTest {

    @Mock
    private ContentBlockRepository contentBlockRepository;

    @InjectMocks
    private ContentBlockService contentBlockService;

    @Test
    void findAll_ShouldReturnAllContentBlocks_WhenNativeQuerySucceeds() {
        // Arrange
        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent("Test content 1");

        ContentBlock block2 = new ContentBlock();
        block2.setId(2L);
        block2.setContent("Test content 2");

        List<ContentBlock> expectedBlocks = Arrays.asList(block1, block2);

        when(contentBlockRepository.findAllNative()).thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> actualBlocks = contentBlockService.findAll();

        // Assert
        assertEquals(expectedBlocks, actualBlocks);
        verify(contentBlockRepository).findAllNative();
        verify(contentBlockRepository, never()).findAll();
    }

    @Test
    void findAll_ShouldFallbackToJpaFindAll_WhenNativeQueryFails() {
        // Arrange
        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent("Test content 1");

        List<ContentBlock> expectedBlocks = Collections.singletonList(block1);

        when(contentBlockRepository.findAllNative()).thenThrow(new RuntimeException("Native query failed"));
        when(contentBlockRepository.findAll()).thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> actualBlocks = contentBlockService.findAll();

        // Assert
        assertEquals(expectedBlocks, actualBlocks);
        verify(contentBlockRepository).findAllNative();
        verify(contentBlockRepository).findAll();
    }

    @Test
    void findByConfluenceUrl_ShouldReturnAssociatedContentBlocks() {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setId(1L);
        confluenceUrl.setUrl("https://confluence.example.com/page");

        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent("Block 1");
        block1.setSequence(1);
        block1.setConfluenceUrl(confluenceUrl);

        ContentBlock block2 = new ContentBlock();
        block2.setId(2L);
        block2.setContent("Block 2");
        block2.setSequence(2);
        block2.setConfluenceUrl(confluenceUrl);

        List<ContentBlock> expectedBlocks = Arrays.asList(block1, block2);

        when(contentBlockRepository.findByConfluenceUrlOrderBySequenceAsc(confluenceUrl))
            .thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> actualBlocks = contentBlockService.findByConfluenceUrl(confluenceUrl);

        // Assert
        assertEquals(expectedBlocks, actualBlocks);
        verify(contentBlockRepository).findByConfluenceUrlOrderBySequenceAsc(confluenceUrl);
    }

    @Test
    void findByConfluenceUrl_ShouldReturnEmptyList_WhenNoBlocksExist() {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setId(1L);

        when(contentBlockRepository.findByConfluenceUrlOrderBySequenceAsc(confluenceUrl))
            .thenReturn(Collections.emptyList());

        // Act
        List<ContentBlock> actualBlocks = contentBlockService.findByConfluenceUrl(confluenceUrl);

        // Assert
        assertTrue(actualBlocks.isEmpty());
        verify(contentBlockRepository).findByConfluenceUrlOrderBySequenceAsc(confluenceUrl);
    }
}
