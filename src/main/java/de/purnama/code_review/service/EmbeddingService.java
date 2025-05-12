package de.purnama.code_review.service;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ContentBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Service for generating and managing vector embeddings for content blocks,
 * supporting semantic search and similarity calculations.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final ContentBlockRepository contentBlockRepository;
    private final EmbeddingModel embeddingModel;

    /**
     * Generates embeddings for a content block and saves it to the database
     */
    @Transactional
    public ContentBlock generateAndSaveEmbedding(ContentBlock contentBlock) {
        if (contentBlock.getContent() == null || contentBlock.getContent().isBlank()) {
            log.warn("Cannot generate embedding for empty content block");
            return contentBlock;
        }

        try {
            // Generate embedding using OpenAI's embedding model
            float[] vector = embeddingModel.embed(contentBlock.getContent());

            // Set the embedding on the content block
            contentBlock.setEmbedding(vector);

            // Save the content block with its embedding
            return contentBlockRepository.save(contentBlock);
        } catch (Exception e) {
            log.error("Error generating embedding for content block: {}", e.getMessage(), e);
            return contentBlock;
        }
    }

    /**
     * Finds content blocks similar to the given query text
     */
    public List<ContentBlock> findSimilarContent(String queryText, int limit) {
        try {
            // Generate embedding for the query text
            log.info("Generating embedding for query: {}", queryText);
            float[] queryEmbedding = embeddingModel.embed(queryText);

            // Find similar content blocks using pgvector similarity search
            log.info("Finding similar content blocks using pgvector");
            return contentBlockRepository.findSimilarContent(queryEmbedding, limit);
        } catch (Exception e) {
            log.error("Error finding similar content: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Generates embeddings for all content blocks that don't have embeddings
     */
    @Transactional
    public void generateEmbeddingsForAllContent() {
        List<ContentBlock> allBlocks = contentBlockRepository.findAll();

        for (ContentBlock block : allBlocks) {
            if (block.getEmbedding() == null && block.getContent() != null && !block.getContent().isBlank()) {
                generateAndSaveEmbedding(block);
                log.info("Generated embedding for content block ID: {}", block.getId());
            }
        }
    }
}