package de.purnama.code_review.model;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * Model representing the result of an automated code review,
 * containing both markdown and HTML formatted review content.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewResponse {
    private String review;          // Markdown content
    private String htmlReview;      // HTML converted content 
    private List<String> guidelines;
    private LocalDateTime timestamp;
    private String githubUrl;
}