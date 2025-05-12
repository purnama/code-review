package de.purnama.code_review.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.exception.ConfluenceUrlException;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.repository.ConfluenceUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * Service for managing Confluence URL entities and their associated content blocks.
 * Provides operations for creating, retrieving, updating and deleting Confluence URLs
 * along with content extraction functionality.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfluenceUrlService {

    private final ConfluenceUrlRepository confluenceUrlRepository;
    private final ConfluenceService confluenceService;
    private final EmbeddingService embeddingService;

    public List<ConfluenceUrl> findAll() {
        return confluenceUrlRepository.findAll();
    }

    public Optional<ConfluenceUrl> findById(Long id) {
        return confluenceUrlRepository.findById(id);
    }

    public Optional<ConfluenceUrl> findByUrl(String url) {
        return confluenceUrlRepository.findByUrl(url);
    }

    @Transactional
    public ConfluenceUrl save(ConfluenceUrl confluenceUrl) {
        // For new URLs, set defaults using actual Confluence content
        if (confluenceUrl.getId() == null && (confluenceUrl.getTitle() == null || confluenceUrl.getTitle().isEmpty())) {
            fetchTitleFromConfluence(confluenceUrl);
        }

        // Save the URL first
        ConfluenceUrl savedUrl = confluenceUrlRepository.save(confluenceUrl);

        // Process content if needed
        processContentIfActive(savedUrl);

        return savedUrl;
    }

    /**
     * Fetch title from Confluence for a new URL
     */
    private void fetchTitleFromConfluence(ConfluenceUrl confluenceUrl) {
        try {
            // Fetch content from Confluence
            confluenceService.fetchConfluenceContent(confluenceUrl);
        } catch (ConfluenceException e) {
            log.error("Failed to fetch content from Confluence: {}", e.getMessage());
            confluenceUrl.setTitle("Untitled Confluence Page");
        } catch (Exception e) {
            log.error("Unexpected error fetching content from Confluence: {}", e.getMessage(), e);
            confluenceUrl.setTitle("Untitled Confluence Page");
        }
    }

    /**
     * Process content if the URL is active
     */
    private void processContentIfActive(ConfluenceUrl savedUrl) {
        if (savedUrl.isActive() && savedUrl.getHtmlContent() != null) {
            processExistingHtmlContent(savedUrl);
        } else if (savedUrl.isActive()) {
            fetchAndProcessContent(savedUrl);
        }
    }

    /**
     * Process existing HTML content into content blocks
     */
    private void processExistingHtmlContent(ConfluenceUrl savedUrl) {
        try {
            // If we already have the HTML content (from a prior fetchConfluenceContent call)
            // Process it directly into content blocks
            List<ContentBlock> contentBlocks = confluenceService.processContentIntoBlocks(savedUrl);
            log.info("Processed {} content blocks from Confluence URL: {}", contentBlocks.size(), savedUrl.getUrl());

            // Generate embeddings for each content block
            generateEmbeddingsForBlocks(contentBlocks);

            // Update the last fetched timestamp
            savedUrl.setLastFetched(LocalDateTime.now());
            confluenceUrlRepository.save(savedUrl);
        } catch (ConfluenceException e) {
            log.error("Error processing Confluence URL content: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing Confluence URL content: {}", e.getMessage(), e);
        }
    }

    /**
     * Fetch and process content for an active URL
     */
    private void fetchAndProcessContent(ConfluenceUrl savedUrl) {
        try {
            // Fetch content from Confluence
            ConfluenceUrl updatedUrl = confluenceService.fetchConfluenceContent(savedUrl);

            // Process into content blocks
            List<ContentBlock> contentBlocks = confluenceService.processContentIntoBlocks(updatedUrl);
            log.info("Processed {} content blocks from Confluence URL: {}", contentBlocks.size(), savedUrl.getUrl());

            // Generate embeddings for each content block
            generateEmbeddingsForBlocks(contentBlocks);

            // Update the last fetched timestamp
            savedUrl.setLastFetched(LocalDateTime.now());
            confluenceUrlRepository.save(savedUrl);
        } catch (ConfluenceException e) {
            log.error("Error fetching or processing Confluence content: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching or processing Confluence content: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for content blocks
     */
    private void generateEmbeddingsForBlocks(List<ContentBlock> contentBlocks) {
        for (ContentBlock block : contentBlocks) {
            embeddingService.generateAndSaveEmbedding(block);
            log.debug("Generated embedding for content block ID: {}", block.getId());
        }
    }

    @Transactional
    public void delete(Long id) {
        confluenceUrlRepository.deleteById(id);
    }

    @Transactional
    public boolean toggleActive(Long id) {
        Optional<ConfluenceUrl> optionalUrl = confluenceUrlRepository.findById(id);
        if (optionalUrl.isPresent()) {
            ConfluenceUrl url = optionalUrl.get();
            url.setActive(!url.isActive());
            confluenceUrlRepository.save(url);
            return true;
        }
        return false;
    }

    @Transactional
    public void updateLastFetched(Long id) {
        Optional<ConfluenceUrl> optionalUrl = confluenceUrlRepository.findById(id);
        if (optionalUrl.isPresent()) {
            ConfluenceUrl url = optionalUrl.get();
            url.setLastFetched(LocalDateTime.now());
            confluenceUrlRepository.save(url);
        }
    }

    /**
     * Manually trigger content refresh from Confluence
     */
    @Transactional
    public void refreshContent(Long id) {
        Optional<ConfluenceUrl> optionalUrl = confluenceUrlRepository.findById(id);
        if (optionalUrl.isPresent()) {
            ConfluenceUrl url = optionalUrl.get();
            refreshUrlContent(url);
        } else {
            throw new ConfluenceUrlException("Confluence URL not found with ID: " + id);
        }
    }

    /**
     * Refresh content for a URL
     */
    private void refreshUrlContent(ConfluenceUrl url) {
        try {
            // Fetch content from Confluence
            ConfluenceUrl updatedUrl = confluenceService.fetchConfluenceContent(url);

            // Process into content blocks
            List<ContentBlock> contentBlocks = confluenceService.processContentIntoBlocks(updatedUrl);
            log.info("Refreshed {} content blocks from Confluence URL: {}", contentBlocks.size(), url.getUrl());

            // Generate embeddings for each content block
            generateEmbeddingsForBlocks(contentBlocks);

            // Update the last fetched timestamp
            url.setLastFetched(LocalDateTime.now());
            confluenceUrlRepository.save(url);
        } catch (ConfluenceException e) {
            log.error("Failed to refresh content from Confluence: {}", e.getMessage());
            throw new ConfluenceUrlException("Failed to refresh content: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error refreshing Confluence content: {}", e.getMessage(), e);
            throw new ConfluenceUrlException("Failed to refresh content: " + e.getMessage(), e);
        }
    }

    /**
     * Find a ConfluenceUrl by ID and eagerly load its content blocks
     */
    @Transactional(readOnly = true)
    public Optional<ConfluenceUrl> findByIdWithContentBlocks(Long id) {
        return confluenceUrlRepository.findByIdWithContentBlocks(id);
    }
}