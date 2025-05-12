package de.purnama.code_review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.purnama.code_review.model.ConfluenceUrl;

/**
 * ConfluenceUrlRepository
 * 
 * Repository for managing ConfluenceUrl entities and related operations
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Repository
public interface ConfluenceUrlRepository extends JpaRepository<ConfluenceUrl, Long> {
    Optional<ConfluenceUrl> findByUrl(String url);

    boolean existsByUrl(String url);

    // Modified query with better handling to avoid "No results" error
    @Query(value = "SELECT DISTINCT cu FROM ConfluenceUrl cu LEFT JOIN FETCH cu.contentBlocks cb WHERE cu.id = :id")
    Optional<ConfluenceUrl> findByIdWithContentBlocks(@Param("id") Long id);
}