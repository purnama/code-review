package de.purnama.code_review.model.git;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Represents a Git repository
 */
@Data
public class GitRepository {
    private String owner;
    private String name;
    private String defaultBranch;
    private String url;
    private List<GitFile> files = new ArrayList<>();
}
