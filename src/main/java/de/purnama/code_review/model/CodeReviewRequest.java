package de.purnama.code_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a request for automated code review,
 * containing repository URL as input.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewRequest {
    private String repositoryUrl;
}
