package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ContentBlockRepository;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceTest {

    @Mock
    private ContentBlockRepository contentBlockRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void generateAndSaveEmbedding_ShouldGenerateAndSaveEmbedding_WhenContentExists() {
        // Arrange
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setId(1L);
        contentBlock.setContent("This is some test content for embedding generation");

        float[] expectedEmbedding = new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f };
        ContentBlock savedContentBlock = new ContentBlock();
        savedContentBlock.setId(1L);
        savedContentBlock.setContent(contentBlock.getContent());
        savedContentBlock.setEmbedding(expectedEmbedding);

        when(embeddingModel.embed(contentBlock.getContent())).thenReturn(expectedEmbedding);
        when(contentBlockRepository.save(any(ContentBlock.class))).thenReturn(savedContentBlock);

        // Act
        ContentBlock result = embeddingService.generateAndSaveEmbedding(contentBlock);

        // Assert
        assertNotNull(result);
        assertArrayEquals(expectedEmbedding, result.getEmbedding());
        verify(embeddingModel).embed(contentBlock.getContent());
        verify(contentBlockRepository).save(contentBlock);
    }

    @Test
    void generateAndSaveEmbedding_ShouldNotGenerateEmbedding_WhenContentIsNull() {
        // Arrange
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setId(1L);
        contentBlock.setContent(null);

        // Act
        ContentBlock result = embeddingService.generateAndSaveEmbedding(contentBlock);

        // Assert
        assertSame(contentBlock, result);
        verifyNoInteractions(embeddingModel);
        verifyNoInteractions(contentBlockRepository);
    }

    @Test
    void generateAndSaveEmbedding_ShouldNotGenerateEmbedding_WhenContentIsBlank() {
        // Arrange
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setId(1L);
        contentBlock.setContent("   ");

        // Act
        ContentBlock result = embeddingService.generateAndSaveEmbedding(contentBlock);

        // Assert
        assertSame(contentBlock, result);
        verifyNoInteractions(embeddingModel);
        verifyNoInteractions(contentBlockRepository);
    }

    @Test
    void generateAndSaveEmbedding_ShouldReturnOriginalBlock_WhenEmbeddingGenerationFails() {
        // Arrange
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setId(1L);
        contentBlock.setContent("This content will cause an embedding error");

        when(embeddingModel.embed(contentBlock.getContent())).thenThrow(new RuntimeException("Embedding generation failed"));

        // Act
        ContentBlock result = embeddingService.generateAndSaveEmbedding(contentBlock);

        // Assert
        assertSame(contentBlock, result);
        verify(embeddingModel).embed(contentBlock.getContent());
        verifyNoInteractions(contentBlockRepository);
    }

    @Test
    void findSimilarContent_ShouldReturnSimilarContentBlocks_WhenQueryIsValid() {
        // Arrange
        String queryText = "Test query for finding similar content";
        int limit = 5;

        float[] queryEmbedding = new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f };

        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent("Similar content 1");

        ContentBlock block2 = new ContentBlock();
        block2.setId(2L);
        block2.setContent("Similar content 2");

        List<ContentBlock> expectedBlocks = Arrays.asList(block1, block2);

        when(embeddingModel.embed(queryText)).thenReturn(queryEmbedding);
        when(contentBlockRepository.findSimilarContent(queryEmbedding, limit)).thenReturn(expectedBlocks);

        // Act
        List<ContentBlock> result = embeddingService.findSimilarContent(queryText, limit);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedBlocks, result);
        verify(embeddingModel).embed(queryText);
        verify(contentBlockRepository).findSimilarContent(queryEmbedding, limit);
    }

    @Test
    void findSimilarContent_ShouldReturnEmptyList_WhenExceptionOccurs() {
        // Arrange
        String queryText = "Test query that will cause an exception";
        int limit = 5;

        when(embeddingModel.embed(queryText)).thenThrow(new RuntimeException("Error generating embedding"));

        // Act
        List<ContentBlock> result = embeddingService.findSimilarContent(queryText, limit);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(embeddingModel).embed(queryText);
        verifyNoInteractions(contentBlockRepository);
    }

    @Test
    void generateEmbeddingsForAllContent_ShouldGenerateEmbeddingsForBlocksWithoutEmbeddings() {
        // Arrange
        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent("Content with no embedding");
        block1.setEmbedding(null);

        ContentBlock block2 = new ContentBlock();
        block2.setId(2L);
        block2.setContent("Content with existing embedding");
        block2.setEmbedding(new float[] { 0.1f, 0.2f, 0.3f });

        ContentBlock block3 = new ContentBlock();
        block3.setId(3L);
        block3.setContent("Another content with no embedding");
        block3.setEmbedding(null);

        List<ContentBlock> allBlocks = Arrays.asList(block1, block2, block3);

        float[] embedding1 = new float[] { 0.4f, 0.5f, 0.6f };
        float[] embedding3 = new float[] { 0.7f, 0.8f, 0.9f };

        when(contentBlockRepository.findAll()).thenReturn(allBlocks);
        when(embeddingModel.embed(block1.getContent())).thenReturn(embedding1);
        when(embeddingModel.embed(block3.getContent())).thenReturn(embedding3);

        // We need to capture the saves to verify the embeddings were set
        when(contentBlockRepository.save(any(ContentBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        embeddingService.generateEmbeddingsForAllContent();

        // Assert
        verify(contentBlockRepository).findAll();
        verify(embeddingModel).embed(block1.getContent());
        verify(embeddingModel, never()).embed(block2.getContent());
        verify(embeddingModel).embed(block3.getContent());

        verify(contentBlockRepository).save(argThat(block ->
            block.getId().equals(1L) && Arrays.equals(block.getEmbedding(), embedding1)));
        verify(contentBlockRepository).save(argThat(block ->
            block.getId().equals(3L) && Arrays.equals(block.getEmbedding(), embedding3)));
        verify(contentBlockRepository, times(2)).save(any(ContentBlock.class));
    }

    @Test
    void generateEmbeddingsForAllContent_ShouldSkipBlocksWithEmptyContent() {
        // Arrange
        ContentBlock block1 = new ContentBlock();
        block1.setId(1L);
        block1.setContent(null);
        block1.setEmbedding(null);

        ContentBlock block2 = new ContentBlock();
        block2.setId(2L);
        block2.setContent("");
        block2.setEmbedding(null);

        ContentBlock block3 = new ContentBlock();
        block3.setId(3L);
        block3.setContent("   ");
        block3.setEmbedding(null);

        List<ContentBlock> allBlocks = Arrays.asList(block1, block2, block3);

        when(contentBlockRepository.findAll()).thenReturn(allBlocks);

        // Act
        embeddingService.generateEmbeddingsForAllContent();

        // Assert
        verify(contentBlockRepository).findAll();
        verifyNoInteractions(embeddingModel);
        verifyNoMoreInteractions(contentBlockRepository);
    }
}
