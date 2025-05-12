package de.purnama.code_review.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * Entity representing a Confluence page URL that contains code to be reviewed.
 * Stores metadata about the page and maintains a relationship with extracted content blocks.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "confluence_urls")
public class ConfluenceUrl {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "URL is required")
    @Column(nullable = false, unique = true)
    private String url;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "last_fetched")
    private LocalDateTime lastFetched;
    
    @Column(name = "active")
    @Builder.Default
    private boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "confluenceUrl", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContentBlock> contentBlocks = new ArrayList<>();
    
    // Store the HTML content temporarily (not persisted to database)
    @Transient
    private String htmlContent;
    
    // Page ID from Confluence
    @Transient
    private String pageId;
}
