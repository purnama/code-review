package de.purnama.code_review.model.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a file in a Git repository
 * Generic model that can be used with any Git provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitFile {
    private String name;
    private String path;
    private String content;
    private String url;
}
