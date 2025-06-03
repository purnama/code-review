package de.purnama.code_review.service;

import de.purnama.code_review.config.GitHubConfig;
import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.CodeReviewException;
import de.purnama.code_review.exception.GitHubException;
import de.purnama.code_review.exception.InvalidCodeReviewRequestException;
import de.purnama.code_review.model.CodeReviewRequest;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private GitHubConfig githubConfig;

    @Mock
    private OpenAIConfig openAIConfig;

    @Mock
    private WebClient githubWebClient;

    @Mock
    private MarkdownConverter markdownConverter;

    // Don't directly mock these interfaces with generics
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private CodeReviewService codeReviewService;

    @BeforeEach
    void setUp() {
        // Initialize the WebClient mocks with proper type erasure
        this.requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        this.requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        this.responseSpec = mock(WebClient.ResponseSpec.class);
    }

    @Test
    void reviewCode_ShouldReviewSingleFile_WhenPathIsInUrl() throws Exception {
        // Arrange
        String githubUrl = "https://github.com/username/repo/blob/main/src/file.java";
        String fileContent = "public class Test { }";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setGithubUrl(githubUrl);

        // Set configuration values specific to this test
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        // Setting up mocks for GitHub API call
        mockGitHubApiCall(githubUrl, fileContent);

        // Setting up mocks for embedding service
        ContentBlock block = new ContentBlock();
        block.setTitle("Code Standard");
        block.setContent("Follow best practices");
        List<ContentBlock> relevantBlocks = Collections.singletonList(block);
        when(embeddingService.findSimilarContent(anyString(), anyInt())).thenReturn(relevantBlocks);

        // Setting up mocks for AI model
        String reviewText = "This code looks good!";
        mockAIModelResponse(reviewText);

        // Setting up mock for markdown converter
        String htmlReview = "<p>This code looks good!</p>";
        when(markdownConverter.convertMarkdownToHtml(reviewText)).thenReturn(htmlReview);

        // Act
        CodeReviewResponse response = codeReviewService.reviewCode(request);

        // Assert
        assertNotNull(response);
        assertEquals(reviewText, response.getReview());
        assertEquals(htmlReview, response.getHtmlReview());
        assertEquals(githubUrl, response.getGithubUrl());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void reviewCode_ShouldThrowInvalidRequestException_WhenInvalidUrl() {
        // Arrange
        String invalidUrl = "not-a-valid-github-url";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setGithubUrl(invalidUrl);

        // Act & Assert
        assertThrows(InvalidCodeReviewRequestException.class, () -> codeReviewService.reviewCode(request));
    }

    @Test
    void reviewCode_ShouldHandleGitHubException_WhenGitHubApiCallFails() {
        // Arrange
        String githubUrl = "https://github.com/username/repo/blob/main/src/file.java";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setGithubUrl(githubUrl);

        // Setting up GitHub API to throw an exception
        when(githubWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("GitHub API error"));

        // Act & Assert
        assertThrows(GitHubException.class, () -> codeReviewService.reviewCode(request));
    }

    @Test
    void reviewCode_ShouldHandleAIModelException_WhenAiModelFails() {
        // Arrange
        String githubUrl = "https://github.com/username/repo/blob/main/src/file.java";
        String fileContent = "public class Test { }";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setGithubUrl(githubUrl);

        // Setting up mocks for GitHub API call
        mockGitHubApiCall(githubUrl, fileContent);

        // Setting up mocks for embedding service
        ContentBlock block = new ContentBlock();
        block.setTitle("Code Standard");
        block.setContent("Follow best practices");
        List<ContentBlock> relevantBlocks = Collections.singletonList(block);
        when(embeddingService.findSimilarContent(anyString(), anyInt())).thenReturn(relevantBlocks);

        // Setting up AI model to throw an exception
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI model error"));

        // Act & Assert
        assertThrows(AIModelException.class, () -> codeReviewService.reviewCode(request));
    }

    @Test
    void reviewCode_ShouldHandleGenericException_AsCodeReviewException() {
        // Arrange
        String githubUrl = "https://github.com/username/repo/blob/main/src/file.java";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setGithubUrl(githubUrl);

        // Setting up GitHub API to throw an uncategorized exception
        when(githubWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new NullPointerException("Unexpected error"));

        // Act & Assert
        Exception exception = assertThrows(CodeReviewException.class, () -> codeReviewService.reviewCode(request));
        assertTrue(exception.getMessage().contains("Failed to perform code review") ||
                   exception.getMessage().contains("Unexpected error"),
                   "Exception message should mention 'Failed to perform code review' or contain the original error message");
    }

    // Test the utility methods directly using reflection to ensure code coverage without creating large files

    @Test
    void findIdealChunkBoundary_ShouldFindBlankLine() {
        // Arrange
        String code = "public class Test {\n\n    public void method() {\n        // code\n    }\n}";
        int start = 0;
        int end = 20;

        // Act - using reflection to access private method
        int result = (int) ReflectionTestUtils.invokeMethod(
            codeReviewService,
            "findIdealChunkBoundary",
            code, start, end
        );

        // Assert
        assertTrue(result > 0);
        assertTrue(result <= end);
    }

    @Test
    void splitCodeIntoChunks_ShouldNotSplitSmallCode() {
        // Arrange
        String code = "Small code snippet";
        int chunkSize = 100;

        // Act - using reflection to access private method
        List<String> chunks = (List<String>) ReflectionTestUtils.invokeMethod(
            codeReviewService,
            "splitCodeIntoChunks",
            code, chunkSize
        );

        // Assert
        assertEquals(1, chunks.size());
        assertEquals(code, chunks.get(0));
    }

    @Test
    void splitCodeIntoChunks_ShouldSplitLargeCode() {
        // Arrange - use a tiny sample that won't cause memory issues
        String code = "first part\nsecond part";
        int chunkSize = 5;  // Tiny size to force splitting

        // Act - using reflection to access private method
        List<String> chunks = (List<String>) ReflectionTestUtils.invokeMethod(
            codeReviewService,
            "splitCodeIntoChunks",
            code, chunkSize
        );

        // Assert
        assertTrue(chunks.size() > 1);
    }

    @Test
    void isInterruptionException_ShouldDetectInterruptions() {
        // True cases
        assertTrue((boolean) ReflectionTestUtils.invokeMethod(
            codeReviewService, "isInterruptionException", new InterruptedException()));

        assertTrue((boolean) ReflectionTestUtils.invokeMethod(
            codeReviewService, "isInterruptionException",
            new RuntimeException("Operation timed out")));

        // False cases
        assertFalse((boolean) ReflectionTestUtils.invokeMethod(
            codeReviewService, "isInterruptionException", new RuntimeException("Other error")));

        assertFalse((boolean) ReflectionTestUtils.invokeMethod(
            codeReviewService, "isInterruptionException", (Exception)null));
    }

    // Helper methods to set up mocks

    private void mockGitHubApiCall(String url, String responseContent) {
        when(githubWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseContent));
    }

    private void mockAIModelResponse(String responseText) {
        // Create a mock response that matches the Spring AI model's expected structure
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);

        // Create a proper AssistantMessage as the output - this is what Spring AI 1.0.0 expects
        AssistantMessage assistantMessage = new AssistantMessage(responseText);

        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
