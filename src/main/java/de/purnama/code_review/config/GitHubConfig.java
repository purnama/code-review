package de.purnama.code_review.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * GitHubConfig
 * Configuration for GitHub API access
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Configuration
@ConfigurationProperties(prefix = "github")
@Data
public class GitHubConfig {

    /**
     * GitHub personal access token (optional)
     * When provided, it increases API rate limits significantly
     */
    private String token;

    /**
     * Returns a WebClient specifically configured for GitHub API calls
     */
    @Bean(name = "githubWebClient")
    public WebClient githubWebClient() {
        return WebClient.builder()
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }
}