package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.purnama.code_review.model.git.GitFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.RequestInterruptedException;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceMethodTests {

    @Mock
    private ChatModel chatModel;

    @Mock
    private MarkdownConverter markdownConverter;

    @Mock
    private OpenAIConfig openAIConfig;

    @Spy
    @InjectMocks
    private CodeReviewService codeReviewService;

    private GitFile testFile;
    private String testGithubUrl;
    private String testGuidelines;
    private StringBuilder testReviewOutput;
    private final String REVIEW_TEXT = "This is a test review with suggestions for improvement.";

    @BeforeEach
    void setUp() {
        // Set up test data
        testFile = GitFile.builder()
                .name("TestFile.java")
                .path("src/test/TestFile.java")
                .content("public class TestFile { void testMethod() { } }")
                .build();

        testGithubUrl = "https://github.com/testowner/testrepo";
        testGuidelines = "# Test Guidelines\nFollow these test guidelines";
        testReviewOutput = new StringBuilder();
    }

    /**
     * Setup a complete execution chain for the test that will return
     * our predetermined review text from the ChatResponse
     */
    private void setupSuccessfulAIResponse() {
        try {
            doReturn(REVIEW_TEXT).when(codeReviewService).generateAIReview(anyString(), anyString());
        } catch (Exception e) {
            fail("Failed to set up mock: " + e.getMessage());
        }
    }

    // Only test the processRepositoryFile method which we can properly stub
    @Test
    void processRepositoryFile_ShouldAppendReviewToStringBuilder_WhenSuccessful() throws AIModelException, RequestInterruptedException {
        // Arrange
        setupSuccessfulAIResponse();

        // Act
        codeReviewService.processRepositoryFile(testFile, testGithubUrl, testGuidelines, testReviewOutput);

        // Assert
        String result = testReviewOutput.toString();
        assertTrue(result.startsWith("## File: " + testFile.getPath()));
        assertTrue(result.contains(REVIEW_TEXT));
    }

    @Test
    void processRepositoryFile_ShouldHandleExceptionGracefully_WhenReviewFails() throws AIModelException, RequestInterruptedException {
        // Arrange
        Exception testException = new AIModelException("Test review generation failed");

        // Mock generateAIReview to throw exception
        doThrow(testException).when(codeReviewService).generateAIReview(anyString(), anyString());

        // Act
        codeReviewService.processRepositoryFile(testFile, testGithubUrl, testGuidelines, testReviewOutput);

        // Assert
        String result = testReviewOutput.toString();
        assertTrue(result.startsWith("## File: " + testFile.getPath()));
        assertTrue(result.contains("Error reviewing this file:"));
        assertTrue(result.contains("Test review generation failed"));
    }

    @Test
    void processRepositoryFile_ShouldCreateCorrectPrompt() throws AIModelException, RequestInterruptedException {
        // Arrange
        final String[] capturedPromptHolder = new String[1]; // Use array to capture value

        // Use doAnswer to capture the prompt parameter
        doAnswer(invocation -> {
            capturedPromptHolder[0] = invocation.getArgument(0);
            return "Mock review";
        }).when(codeReviewService).generateAIReview(anyString(), anyString());

        // Act
        codeReviewService.processRepositoryFile(testFile, testGithubUrl, testGuidelines, testReviewOutput);

        // Assert
        assertNotNull(capturedPromptHolder[0]);
        assertTrue(capturedPromptHolder[0].contains(testGithubUrl));
        assertTrue(capturedPromptHolder[0].contains(testGuidelines));
        assertTrue(capturedPromptHolder[0].contains(testFile.getContent()));
    }
}
