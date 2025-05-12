package de.purnama.code_review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.model.ConfluenceUrl;

/**
 * ContentBlockRepository
 * 
 * Repository for managing ContentBlock entities
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Repository
public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {
    List<ContentBlock> findByConfluenceUrlOrderBySequenceAsc(ConfluenceUrl confluenceUrl);

    void deleteByConfluenceUrl(ConfluenceUrl confluenceUrl);

    // Using pgvector-hibernate's Vector type for the cosine similarity query
    // Cast the float[] embedding parameter to vector type for PostgreSQL
    @Query(value = "SELECT * FROM content_blocks WHERE embedding IS NOT NULL ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<ContentBlock> findSimilarContent(@Param("embedding") float[] embedding, @Param("limit") int limit);

    // Native query for findAll - useful as a fallback
    @Query(value = "SELECT * FROM content_blocks", nativeQuery = true)
    List<ContentBlock> findAllNative();
}