package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.RequestInterruptedException;
import de.purnama.code_review.service.git.GitProviderFactory;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceGenerateAIReviewTest {

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
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    private CodeReviewService codeReviewService;

    private String testPrompt;
    private String fileId;
    private String expectedReview;

    @BeforeEach
    void setUp() {
        // Create the real CodeReviewService with mocked dependencies
        codeReviewService = new CodeReviewService(
                embeddingService,
                chatModel,
                openAIConfig,
                markdownConverter,
                gitProviderFactory
        );

        testPrompt = "Test prompt content";
        fileId = "TestFile.java";
        expectedReview = "This is a test review";
    }

    @Test
    void generateAIReview_ShouldReturnReviewText_WhenSuccessful() throws Exception {
        // Arrange
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(expectedReview);

        // Act
        String result = codeReviewService.generateAIReview(testPrompt, fileId);

        // Assert
        assertEquals(expectedReview, result);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void generateAIReview_ShouldThrowRuntimeException_WhenOperationTimesOut() throws Exception {
        // Arrange - ChatModel throws a timeout exception
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Operation timed out"));

        // Act & Assert - Expect the RuntimeException to be thrown directly
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> codeReviewService.generateAIReview(testPrompt, fileId));

        assertEquals("Operation timed out", exception.getMessage());
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void generateAIReview_ShouldThrowException_WhenAllRetriesFail() {
        // Arrange - ChatModel will throw RuntimeException
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Failed attempt"));

        // Act & Assert - Expect the actual RuntimeException to be thrown
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> codeReviewService.generateAIReview(testPrompt, fileId));

        assertEquals("Failed attempt", exception.getMessage());
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void generateAIReview_ShouldHandleNullResponse() throws Exception {
        // Arrange - ChatModel returns null response
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        // Act & Assert - Expect AIModelException for null response
        AIModelException exception = assertThrows(AIModelException.class,
            () -> codeReviewService.generateAIReview(testPrompt, fileId));

        assertTrue(exception.getMessage().contains("AI model returned null response for file: TestFile.java"));
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void isInterruptionException_ShouldIdentifyInterruptionExceptions() {
        // Test the helper method directly using reflection
        boolean result1 = (boolean) ReflectionTestUtils.invokeMethod(
                codeReviewService, "isInterruptionException", new InterruptedException());
        boolean result2 = (boolean) ReflectionTestUtils.invokeMethod(
                codeReviewService, "isInterruptionException", new RuntimeException("Operation timed out"));
        boolean result3 = (boolean) ReflectionTestUtils.invokeMethod(
                codeReviewService, "isInterruptionException", new RuntimeException("Other error"));
        boolean result4 = (boolean) ReflectionTestUtils.invokeMethod(
                codeReviewService, "isInterruptionException", (Exception)null);

        assertTrue(result1, "Should identify InterruptedException");
        assertTrue(result2, "Should identify RuntimeException with timeout message");
        assertFalse(result3, "Should not identify unrelated exceptions");
        assertFalse(result4, "Should handle null exception");
    }
}
