package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
}
