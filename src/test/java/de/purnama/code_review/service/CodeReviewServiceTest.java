package de.purnama.code_review.service;

import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.*;
import de.purnama.code_review.model.CodeReviewRequest;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.service.git.GitProvider;
import de.purnama.code_review.service.git.GitProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private OpenAIConfig openAIConfig;

    @Mock
    private MarkdownConverter markdownConverter;

    @Mock
    private GitProviderFactory gitProviderFactory;

    @Mock
    private GitProvider gitProvider;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    @InjectMocks
    private CodeReviewService codeReviewService;

    @BeforeEach
    void setUp() {
        // No setup is needed here - we'll set up mocks in each test as required
    }

    @Test
    void reviewCode_ShouldReviewSingleFile_WhenPathIsInUrl() throws Exception {
        // Arrange
        String repositoryUrl = "https://github.com/username/repo/blob/main/src/file.java";
        String fileContent = "public class Test { }";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setRepositoryUrl(repositoryUrl);

        // Setup the git provider factory and provider
        when(gitProviderFactory.getProvider(repositoryUrl)).thenReturn(gitProvider);

        // Set up repository info with path
        Map<String, String> repoInfo = new HashMap<>();
        repoInfo.put("owner", "username");
        repoInfo.put("repo", "repo");
        repoInfo.put("branch", "main");
        repoInfo.put("path", "src/file.java");
        when(gitProvider.extractRepositoryInfoFromUrl(repositoryUrl)).thenReturn(repoInfo);

        // Set up file content
        when(gitProvider.fetchFileContent(repositoryUrl)).thenReturn(fileContent);

        // Set configuration values specific to this test
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        // Setting up mocks for embedding service
        ContentBlock block = new ContentBlock();
        block.setTitle("Code Standard");
        block.setContent("Follow best practices");
        List<ContentBlock> relevantBlocks = Collections.singletonList(block);
        when(embeddingService.findSimilarContent(anyString(), anyInt())).thenReturn(relevantBlocks);

        // Setting up mocks for AI model
        String reviewText = "This code looks good!";
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(reviewText);

        // Setting up mock for markdown converter
        String htmlReview = "<p>This code looks good!</p>";
        when(markdownConverter.convertMarkdownToHtml(reviewText)).thenReturn(htmlReview);

        // Act
        CodeReviewResponse response = codeReviewService.reviewCode(request);

        // Assert
        assertNotNull(response);
        assertEquals(reviewText, response.getReview());
        assertEquals(htmlReview, response.getHtmlReview());
        assertEquals(repositoryUrl, response.getRepositoryUrl());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void reviewCode_ShouldThrowInvalidRequestException_WhenInvalidUrl() throws Exception {
        // Arrange
        String invalidUrl = "not-a-valid-github-url";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setRepositoryUrl(invalidUrl);

        // Mock git provider to return null or empty values rather than throw exception
        when(gitProviderFactory.getProvider(invalidUrl)).thenReturn(gitProvider);
        Map<String, String> emptyInfo = new HashMap<>();
        when(gitProvider.extractRepositoryInfoFromUrl(invalidUrl)).thenReturn(emptyInfo);

        // Act & Assert
        assertThrows(InvalidCodeReviewRequestException.class, () -> codeReviewService.reviewCode(request));
    }

    @Test
    void reviewCode_ShouldHandleGitProviderException_WhenGitApiCallFails() throws Exception {
        // Arrange
        String repositoryUrl = "https://github.com/username/repo/blob/main/src/file.java";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setRepositoryUrl(repositoryUrl);

        // Setup git provider factory
        when(gitProviderFactory.getProvider(repositoryUrl)).thenReturn(gitProvider);

        // Setup repository info
        Map<String, String> repoInfo = new HashMap<>();
        repoInfo.put("owner", "username");
        repoInfo.put("repo", "repo");
        repoInfo.put("branch", "main");
        repoInfo.put("path", "src/file.java");
        when(gitProvider.extractRepositoryInfoFromUrl(repositoryUrl)).thenReturn(repoInfo);

        // Setting up GitProvider to throw an exception
        when(gitProvider.fetchFileContent(repositoryUrl))
            .thenThrow(new GitProviderException("Git API error"));

        // Act & Assert
        assertThrows(GitProviderException.class, () -> codeReviewService.reviewCode(request));
    }

    @Test
    void reviewCode_ShouldHandleAIModelException_WhenAiModelFails() throws Exception {
        // Arrange
        String repositoryUrl = "https://github.com/username/repo/blob/main/src/file.java";
        String fileContent = "public class Test { }";
        CodeReviewRequest request = new CodeReviewRequest();
        request.setRepositoryUrl(repositoryUrl);

        // Setup git provider factory
        when(gitProviderFactory.getProvider(repositoryUrl)).thenReturn(gitProvider);

        // Setup repository info
        Map<String, String> repoInfo = new HashMap<>();
        repoInfo.put("owner", "username");
        repoInfo.put("repo", "repo");
        repoInfo.put("branch", "main");
        repoInfo.put("path", "src/file.java");
        when(gitProvider.extractRepositoryInfoFromUrl(repositoryUrl)).thenReturn(repoInfo);

        // Setup file content
        when(gitProvider.fetchFileContent(repositoryUrl)).thenReturn(fileContent);

        // Setting up mocks for embedding service
        ContentBlock block = new ContentBlock();
        block.setTitle("Code Standard");
        block.setContent("Follow best practices");
        List<ContentBlock> relevantBlocks = Collections.singletonList(block);
        when(embeddingService.findSimilarContent(anyString(), anyInt())).thenReturn(relevantBlocks);

        // Set configuration values
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        // Setting up AI model to throw an exception
        RuntimeException aiModelError = new RuntimeException("AI model error");
        when(chatModel.call(any(Prompt.class))).thenThrow(aiModelError);

        // Act & Assert - Expect the actual RuntimeException to propagate
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> codeReviewService.reviewCode(request));
        assertEquals("AI model error", thrown.getMessage());
    }

    @Test
    void testSplitCodeIntoChunks_basicScenarios() throws Exception {
        String codeSmall = "int a = 1;";
        String codeExact = "int a = 1;\nint b = 2;";
        String codeLarge = "int a = 1;\n\nint b = 2;\n\nint c = 3;\n\nint d = 4;";
        int chunkSize = 10;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "splitCodeIntoChunks", String.class, int.class);
        method.setAccessible(true);

        // Small code, should return one chunk
        List<String> resultSmall = (List<String>) method.invoke(codeReviewService, codeSmall, chunkSize);
        assertEquals(1, resultSmall.size());
        assertEquals(codeSmall, resultSmall.get(0));

        // Code exactly chunk size, should return one chunk
        List<String> resultExact = (List<String>) method.invoke(codeReviewService, codeExact, codeExact.length());
        assertEquals(1, resultExact.size());
        assertEquals(codeExact, resultExact.get(0));

        // Large code, should return multiple chunks
        List<String> resultLarge = (List<String>) method.invoke(codeReviewService, codeLarge, chunkSize);
        assertTrue(resultLarge.size() > 1);
        assertEquals(codeLarge.replaceAll("\\n", ""), String.join("", resultLarge).replaceAll("\\n", ""));
    }

    @Test
    void processIndividualChunk_shouldReturnReviewTextOnSuccess() throws AIModelException {
        String repoUrl = "repo";
        String chunk = "code chunk";
        String guidelines = "guidelines";
        int chunkNumber = 1, totalChunks = 1;
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("review text");
        String result = codeReviewService.processIndividualChunk(repoUrl, chunk, guidelines, chunkNumber, totalChunks);
        assertEquals("review text", result);
    }

    @Test
    void processIndividualChunk_shouldThrowAIModelException_OnNullResponse() throws AIModelException {
        String repoUrl = "repo";
        String chunk = "code chunk";
        String guidelines = "guidelines";
        int chunkNumber = 1, totalChunks = 1;
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        // Act & Assert - Expect AIModelException for null response
        AIModelException exception = assertThrows(AIModelException.class, () ->
            codeReviewService.processIndividualChunk(repoUrl, chunk, guidelines, chunkNumber, totalChunks));

        assertTrue(exception.getMessage().contains("AI model returned null response for file: " + repoUrl));
    }
}
