package de.purnama.code_review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration for Confluence API access
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "confluence")
public class ConfluenceConfig {
    private String baseUrl;
    private String username;
    private String apiToken;
    private String spaceKey;
}