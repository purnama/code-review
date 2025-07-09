package de.purnama.code_review.service;

import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.service.git.GitProvider;
import de.purnama.code_review.service.git.GitProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceSingleFileTest {
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

    @Spy
    @InjectMocks
    private CodeReviewService codeReviewService;

    private final String TEST_REPO_URL = "https://github.com/test/repo/TestFile.java";
    private final String TEST_CODE = "public class TestFile { }";
    private final String TEST_GUIDELINE = "# Test Guideline\nFollow best practices.";
    private final String TEST_REVIEW = "This is a review.";
    private final String TEST_HTML = "<p>This is a review.</p>";

    @BeforeEach
    void setup() throws Exception {
        // Only set up the most common stubbing needed for all tests
        when(gitProviderFactory.getProvider(anyString())).thenReturn(gitProvider);
    }

    @Test
    void reviewSingleFile_ShouldReturnValidResponse_WhenNormal() throws Exception {
        when(gitProvider.fetchFileContent(anyString())).thenReturn(TEST_CODE);
        when(embeddingService.findSimilarContent(anyString(), anyInt()))
                .thenReturn(List.of(ContentBlock.builder().title("Test Guideline").content("Follow best practices.").build()));
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        when(markdownConverter.convertMarkdownToHtml(anyString())).thenReturn(TEST_HTML);
        ChatResponse chatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(chatResponse.getResult().getOutput().getText()).thenReturn(TEST_REVIEW);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        CodeReviewResponse response = invokeReviewSingleFile();
        assertNotNull(response);
        assertEquals(TEST_REVIEW, response.getReview());
        assertEquals(TEST_HTML, response.getHtmlReview());
        assertTrue(response.getGuidelines().get(0).contains("Test Guideline"));
        assertEquals(TEST_REPO_URL, response.getRepositoryUrl());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void reviewSingleFile_ShouldPropagateGitProviderException() throws Exception {
        when(gitProvider.fetchFileContent(anyString())).thenThrow(new GitProviderException("Git error"));
        assertThrows(GitProviderException.class, this::invokeReviewSingleFile);
    }

    @Test
    void reviewSingleFile_ShouldPropagateAIModelException_WhenLargeFileChunkingFails() throws Exception {
        when(gitProvider.fetchFileContent(anyString())).thenReturn(TEST_CODE);
        when(embeddingService.findSimilarContent(anyString(), anyInt()))
                .thenReturn(List.of(ContentBlock.builder().title("Test Guideline").content("Follow best practices.").build()));
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);
        when(openAIConfig.getFileChunkSize()).thenReturn(1); // Force chunking
        doReturn(CodeReviewResponse.builder().review("Error processing this chunk: fail").build())
                .when(codeReviewService).processLargeFileInChunks(anyString(), anyString(), anyString(), anyList());
        assertThrows(AIModelException.class, this::invokeReviewSingleFile);
    }

    @Test
    void reviewSingleFile_ShouldPropagateRuntimeException() throws Exception {
        when(gitProvider.fetchFileContent(anyString())).thenReturn(TEST_CODE);
        when(embeddingService.findSimilarContent(anyString(), anyInt()))
                .thenReturn(List.of(ContentBlock.builder().title("Test Guideline").content("Follow best practices.").build()));
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Unexpected error"));
        assertThrows(AIModelException.class, this::invokeReviewSingleFile);
    }

    private CodeReviewResponse invokeReviewSingleFile() throws Exception {
        var method = CodeReviewService.class.getDeclaredMethod("reviewSingleFile", String.class);
        method.setAccessible(true);
        try {
            return (CodeReviewResponse) method.invoke(codeReviewService, TEST_REPO_URL);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap and rethrow the real exception for proper test assertions
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            } else {
                throw e;
            }
        }
    }
}
