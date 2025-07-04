package de.purnama.code_review.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents content returned from GitHub API
 * This is used when fetching repository contents
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitContent {
    private String name;
    private String path;
    private String type;

    @JsonProperty("download_url")
    private String downloadUrl;

    private String url;
    private String htmlUrl;
}
