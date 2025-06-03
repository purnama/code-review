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

    @Test
    void findById_ShouldReturnContentBlock_WhenFound() {
        // Arrange
        Long blockId = 1L;
        ContentBlock expectedBlock = new ContentBlock();
        expectedBlock.setId(blockId);
        expectedBlock.setContent("Test content");

        when(contentBlockRepository.findById(blockId)).thenReturn(Optional.of(expectedBlock));

        // Act
        Optional<ContentBlock> result = contentBlockService.findById(blockId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedBlock, result.get());
        verify(contentBlockRepository).findById(blockId);
    }

    @Test
    void findById_ShouldReturnEmptyOptional_WhenNotFound() {
        // Arrange
        Long blockId = 1L;
        when(contentBlockRepository.findById(blockId)).thenReturn(Optional.empty());

        // Act
        Optional<ContentBlock> result = contentBlockService.findById(blockId);

        // Assert
        assertFalse(result.isPresent());
        verify(contentBlockRepository).findById(blockId);
    }

    @Test
    void save_ShouldReturnSavedContentBlock() {
        // Arrange
        ContentBlock blockToSave = new ContentBlock();
        blockToSave.setContent("Content to save");

        ContentBlock savedBlock = new ContentBlock();
        savedBlock.setId(1L);
        savedBlock.setContent("Content to save");

        when(contentBlockRepository.save(blockToSave)).thenReturn(savedBlock);

        // Act
        ContentBlock result = contentBlockService.save(blockToSave);

        // Assert
        assertEquals(savedBlock, result);
        verify(contentBlockRepository).save(blockToSave);
    }

    @Test
    void saveAll_ShouldReturnSavedContentBlocks() {
        // Arrange
        ContentBlock block1 = new ContentBlock();
        block1.setContent("Content 1");

        ContentBlock block2 = new ContentBlock();
        block2.setContent("Content 2");

        List<ContentBlock> blocksToSave = Arrays.asList(block1, block2);

        ContentBlock savedBlock1 = new ContentBlock();
        savedBlock1.setId(1L);
        savedBlock1.setContent("Content 1");

        ContentBlock savedBlock2 = new ContentBlock();
        savedBlock2.setId(2L);
        savedBlock2.setContent("Content 2");

        List<ContentBlock> savedBlocks = Arrays.asList(savedBlock1, savedBlock2);

        when(contentBlockRepository.saveAll(blocksToSave)).thenReturn(savedBlocks);

        // Act
        List<ContentBlock> result = contentBlockService.saveAll(blocksToSave);

        // Assert
        assertEquals(savedBlocks, result);
        verify(contentBlockRepository).saveAll(blocksToSave);
    }

    @Test
    void delete_ShouldCallRepositoryDeleteById() {
        // Arrange
        Long blockId = 1L;
        doNothing().when(contentBlockRepository).deleteById(blockId);

        // Act
        contentBlockService.delete(blockId);

        // Assert
        verify(contentBlockRepository).deleteById(blockId);
    }

    @Test
    void deleteByConfluenceUrl_ShouldCallRepositoryDeleteByConfluenceUrl() {
        // Arrange
        ConfluenceUrl confluenceUrl = new ConfluenceUrl();
        confluenceUrl.setId(1L);
        confluenceUrl.setUrl("https://confluence.example.com/page");

        doNothing().when(contentBlockRepository).deleteByConfluenceUrl(confluenceUrl);

        // Act
        contentBlockService.deleteByConfluenceUrl(confluenceUrl);

        // Assert
        verify(contentBlockRepository).deleteByConfluenceUrl(confluenceUrl);
    }
}
