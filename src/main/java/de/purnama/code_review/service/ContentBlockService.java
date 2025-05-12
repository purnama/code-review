package de.purnama.code_review.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ContentBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Service for managing ContentBlock entities, including CRUD operations
 * and associations with Confluence URLs.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentBlockService {

    private final ContentBlockRepository contentBlockRepository;

    /**
     * Find all content blocks
     *
     * @return List of all content blocks
     */
    @Transactional(readOnly = true)
    public List<ContentBlock> findAll() {
        try {
            // First try the native query which should be more compatible with pgvector
            return contentBlockRepository.findAllNative();
        } catch (Exception e) {
            log.warn("Native query failed, falling back to JPA findAll(): {}", e.getMessage());
            // Fall back to standard JPA method if native query fails
            return contentBlockRepository.findAll();
        }
    }

    @Transactional(readOnly = true)
    public List<ContentBlock> findByConfluenceUrl(ConfluenceUrl confluenceUrl) {
        return contentBlockRepository.findByConfluenceUrlOrderBySequenceAsc(confluenceUrl);
    }

    @Transactional(readOnly = true)
    public Optional<ContentBlock> findById(Long id) {
        return contentBlockRepository.findById(id);
    }

    @Transactional
    public ContentBlock save(ContentBlock contentBlock) {
        return contentBlockRepository.save(contentBlock);
    }

    @Transactional
    public List<ContentBlock> saveAll(List<ContentBlock> contentBlocks) {
        return contentBlockRepository.saveAll(contentBlocks);
    }

    @Transactional
    public void delete(Long id) {
        contentBlockRepository.deleteById(id);
    }

    @Transactional
    public void deleteByConfluenceUrl(ConfluenceUrl confluenceUrl) {
        contentBlockRepository.deleteByConfluenceUrl(confluenceUrl);
    }
}