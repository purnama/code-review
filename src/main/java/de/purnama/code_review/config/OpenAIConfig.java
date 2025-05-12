package de.purnama.code_review.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * OpenAIConfig
 * Configuration class for OpenAI API integration, handling configuration for both
 * embedding and chat completion models.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Configuration
public class OpenAIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${code.review.max-files:10}")
    private int maxFilesToReview;

    @Value("${code.review.content-blocks-limit:10}")
    private int contentBlocksLimit;

    @Value("${code.review.api-timeout-seconds:120}")
    private int apiTimeoutSeconds;

    @Value("${code.review.file-chunk-size:5000}")
    private int fileChunkSize;

    @Bean
    public OpenAiApi openAiApi() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(Duration.ofSeconds(30));
        clientHttpRequestFactory.setConnectionRequestTimeout(Duration.ofSeconds(apiTimeoutSeconds));
        // Create a RestClient with extended timeout settings
        return OpenAiApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(clientHttpRequestFactory))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        // Use the text-embedding-3-small model
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-3-small")
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

    @Bean
    public ChatModel chatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model("gpt-4.1")
                .temperature(1.0)
                .maxTokens(32768)
                .topP(1.0)
                .build();

        // Build the model with more robust retry configuration
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .retryTemplate(createRetryTemplate())
                .build();
    }

    @Bean
    public RetryTemplate createRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(Duration.ofMillis(1000), 2, Duration.ofSeconds(10))
                .retryOn(Exception.class)
                .build();
    }

    public int getMaxFilesToReview() {
        return maxFilesToReview;
    }

    public int getContentBlocksLimit() {
        return contentBlocksLimit;
    }

    public int getApiTimeoutSeconds() {
        return apiTimeoutSeconds;
    }

    public int getFileChunkSize() {
        return fileChunkSize;
    }
}