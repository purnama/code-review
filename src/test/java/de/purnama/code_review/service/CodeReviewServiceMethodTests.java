package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.model.git.GitFile;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private GitProviderFactory gitProviderFactory;

    @Mock
    private GitProvider gitProvider;

    @Spy
    @InjectMocks
    private CodeReviewService codeReviewService;

    private GitFile testFile;
    private String testGithubUrl;
    private String testGuidelines;
    private StringBuilder testReviewOutput;
    private final String REVIEW_TEXT = "This is a test review with suggestions for improvement.";

    // Test constants for refactored methods
    private static final String TEST_REPOSITORY_URL = "https://github.com/testowner/testrepo";
    private static final String TEST_OWNER = "testowner";
    private static final String TEST_REPO = "testrepo";
    private static final String TEST_BRANCH = "main";

    @BeforeEach
    void setUp() throws GitProviderException {
        // Set up test data
        testFile = GitFile.builder()
                .name("TestFile.java")
                .path("src/test/TestFile.java")
                .content("public class TestFile { void testMethod() { } }")
                .build();

        testGithubUrl = "https://github.com/testowner/testrepo";
        testGuidelines = "# Test Guidelines\nFollow these test guidelines";
        testReviewOutput = new StringBuilder();

        // Note: Mock behaviors are now set up individually in each test method
        // to avoid unnecessary stubbing warnings
    }

    // Tests for refactored methods

    @Test
    void fetchRepositoryFilesForReview_ShouldReturnFiles_WhenSuccessful() throws Exception {
        // Arrange
        when(gitProviderFactory.getProvider(anyString())).thenReturn(gitProvider);
        when(openAIConfig.getMaxFilesToReview()).thenReturn(10);

        List<GitFile> expectedFiles = Arrays.asList(
                createTestGitFile("src/main/Main.java", "public class Main {}"),
                createTestGitFile("src/test/Test.java", "public class Test {}")
        );
        when(gitProvider.fetchRepositoryFiles(TEST_OWNER, TEST_REPO, TEST_BRANCH, 10))
                .thenReturn(expectedFiles);

        // Act
        List<GitFile> result = invokePrivateMethod("fetchRepositoryFilesForReview",
                TEST_OWNER, TEST_REPO, TEST_BRANCH, TEST_REPOSITORY_URL);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("src/main/Main.java", result.get(0).getPath());
        assertEquals("src/test/Test.java", result.get(1).getPath());

        verify(gitProviderFactory).getProvider(TEST_REPOSITORY_URL);
        verify(gitProvider).fetchRepositoryFiles(TEST_OWNER, TEST_REPO, TEST_BRANCH, 10);
    }

    @Test
    void fetchRepositoryFilesForReview_ShouldThrowException_WhenGitProviderFails() throws Exception {
        // Arrange
        when(gitProviderFactory.getProvider(anyString())).thenReturn(gitProvider);
        when(openAIConfig.getMaxFilesToReview()).thenReturn(10);

        when(gitProvider.fetchRepositoryFiles(TEST_OWNER, TEST_REPO, TEST_BRANCH, 10))
                .thenThrow(new GitProviderException("Failed to fetch files"));

        // Act & Assert
        assertThrows(GitProviderException.class, () -> {
            invokePrivateMethod("fetchRepositoryFilesForReview",
                    TEST_OWNER, TEST_REPO, TEST_BRANCH, TEST_REPOSITORY_URL);
        });
    }

    @Test
    void findRelevantGuidelines_ShouldReturnFormattedGuidelines_WhenSuccessful() throws Exception {
        // Arrange
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        List<GitFile> testFiles = Arrays.asList(
                createTestGitFile("Main.java", "public class Main { public static void main(String[] args) {} }"),
                createTestGitFile("Utils.java", "public class Utils { public static String format() {} }")
        );

        List<ContentBlock> mockContentBlocks = Arrays.asList(
                createTestContentBlock("Java Naming Conventions", "Use camelCase for method names"),
                createTestContentBlock("Class Structure", "Public methods should be documented")
        );

        when(embeddingService.findSimilarContent(anyString(), eq(5))).thenReturn(mockContentBlocks);

        // Act
        List<String> result = invokePrivateMethod("findRelevantGuidelines", testFiles);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("# Java Naming Conventions"));
        assertTrue(result.get(0).contains("Use camelCase for method names"));
        assertTrue(result.get(1).contains("# Class Structure"));
        assertTrue(result.get(1).contains("Public methods should be documented"));

        verify(embeddingService).findSimilarContent(anyString(), eq(5));
    }

    @Test
    void findRelevantGuidelines_ShouldReturnEmptyList_WhenNoContentBlocks() throws Exception {
        // Arrange
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        List<GitFile> testFiles = Arrays.asList(createTestGitFile("Test.java", "test content"));
        when(embeddingService.findSimilarContent(anyString(), eq(5))).thenReturn(Collections.emptyList());

        // Act
        List<String> result = invokePrivateMethod("findRelevantGuidelines", testFiles);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findRelevantGuidelines_ShouldCombineCodeContent_Correctly() throws Exception {
        // Arrange
        when(openAIConfig.getContentBlocksLimit()).thenReturn(5);

        List<GitFile> testFiles = Arrays.asList(
                createTestGitFile("File1.java", "content1"),
                createTestGitFile("File2.java", "content2")
        );

        when(embeddingService.findSimilarContent(eq("content1\n\ncontent2"), eq(5)))
                .thenReturn(Collections.emptyList());

        // Act
        invokePrivateMethod("findRelevantGuidelines", testFiles);

        // Assert
        verify(embeddingService).findSimilarContent(eq("content1\n\ncontent2"), eq(5));
    }

    @Test
    void processAllRepositoryFiles_ShouldGenerateReview_WhenSuccessful() throws Exception {
        // Arrange
        List<GitFile> testFiles = Arrays.asList(
                createTestGitFile("Main.java", "public class Main {}"),
                createTestGitFile("Utils.java", "public class Utils {}")
        );

        List<String> guidelines = Arrays.asList("Guideline 1", "Guideline 2");

        // Create a fresh spy instance instead of spying on the mock
        CodeReviewService realService = new CodeReviewService(embeddingService, chatModel, openAIConfig, markdownConverter, gitProviderFactory);
        CodeReviewService spyService = spy(realService);
        doNothing().when(spyService).processRepositoryFile(any(GitFile.class), anyString(), anyString(), any(StringBuilder.class));

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(spyService, "processAllRepositoryFiles",
                testFiles, TEST_REPOSITORY_URL, guidelines);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Code Review Summary"));
        assertTrue(result.contains("The following files were reviewed:"));

        verify(spyService, times(2)).processRepositoryFile(any(GitFile.class), eq(TEST_REPOSITORY_URL), anyString(), any(StringBuilder.class));
    }

    @Test
    void processAllRepositoryFiles_ShouldHandleEmptyFilesList() throws Exception {
        // Arrange
        List<GitFile> emptyFiles = Collections.emptyList();
        List<String> guidelines = Arrays.asList("Guideline 1");

        // Act
        String result = invokePrivateMethod("processAllRepositoryFiles",
                emptyFiles, TEST_REPOSITORY_URL, guidelines);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Code Review Summary"));
        assertTrue(result.contains("The following files were reviewed:"));
    }

    @Test
    void buildProjectReviewResponse_ShouldCreateCompleteResponse_WhenSuccessful() throws Exception {
        // Arrange
        String testReview = "# Test Review\nThis is a test review.";
        List<String> testGuidelines = Arrays.asList("Guideline 1", "Guideline 2");
        String expectedHtml = "<h1>Test Review</h1><p>This is a test review.</p>";

        when(markdownConverter.convertMarkdownToHtml(testReview)).thenReturn(expectedHtml);

        // Act
        CodeReviewResponse result = invokePrivateMethod("buildProjectReviewResponse",
                testReview, testGuidelines, TEST_REPOSITORY_URL);

        // Assert
        assertNotNull(result);
        assertEquals(testReview, result.getReview());
        assertEquals(expectedHtml, result.getHtmlReview());
        assertEquals(testGuidelines, result.getGuidelines());
        assertEquals(TEST_REPOSITORY_URL, result.getRepositoryUrl());
        assertNotNull(result.getTimestamp());
        assertTrue(result.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));

        verify(markdownConverter).convertMarkdownToHtml(testReview);
    }

    @Test
    void buildProjectReviewResponse_ShouldHandleEmptyReview() throws Exception {
        // Arrange
        String emptyReview = "";
        List<String> guidelines = Collections.emptyList();
        String expectedHtml = "";

        when(markdownConverter.convertMarkdownToHtml(emptyReview)).thenReturn(expectedHtml);

        // Act
        CodeReviewResponse result = invokePrivateMethod("buildProjectReviewResponse",
                emptyReview, guidelines, TEST_REPOSITORY_URL);

        // Assert
        assertNotNull(result);
        assertEquals(emptyReview, result.getReview());
        assertEquals(expectedHtml, result.getHtmlReview());
        assertTrue(result.getGuidelines().isEmpty());
        assertEquals(TEST_REPOSITORY_URL, result.getRepositoryUrl());
    }

    @Test
    void buildProjectReviewResponse_ShouldHandleNullGuidelines() throws Exception {
        // Arrange
        String testReview = "Test review";
        List<String> nullGuidelines = null;
        String expectedHtml = "<p>Test review</p>";

        when(markdownConverter.convertMarkdownToHtml(testReview)).thenReturn(expectedHtml);

        // Act
        CodeReviewResponse result = invokePrivateMethod("buildProjectReviewResponse",
                testReview, nullGuidelines, TEST_REPOSITORY_URL);

        // Assert
        assertNotNull(result);
        assertEquals(testReview, result.getReview());
        assertEquals(expectedHtml, result.getHtmlReview());
        assertNull(result.getGuidelines());
        assertEquals(TEST_REPOSITORY_URL, result.getRepositoryUrl());
    }

    // Helper methods for refactored method tests

    private GitFile createTestGitFile(String path, String content) {
        return GitFile.builder()
                .path(path)
                .content(content)
                .build();
    }

    private ContentBlock createTestContentBlock(String title, String content) {
        return ContentBlock.builder()
                .title(title)
                .content(content)
                .build();
    }

    /**
     * Helper method to invoke private methods using reflection
     * Properly handles checked exceptions by wrapping them
     */
    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(String methodName, Object... args) throws Exception {
        try {
            return (T) ReflectionTestUtils.invokeMethod(codeReviewService, methodName, args);
        } catch (RuntimeException e) {
            // ReflectionTestUtils wraps checked exceptions in RuntimeExceptions
            // Unwrap and rethrow the original exception if it's a checked exception we expect
            if (e.getCause() instanceof GitProviderException) {
                throw (GitProviderException) e.getCause();
            }
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Helper method to invoke private methods on a specific service instance using reflection
     */
    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(CodeReviewService service, String methodName, Object... args) throws Exception {
        try {
            return (T) ReflectionTestUtils.invokeMethod(service, methodName, args);
        } catch (RuntimeException e) {
            // ReflectionTestUtils wraps checked exceptions in RuntimeExceptions
            // Unwrap and rethrow the original exception if it's a checked exception we expect
            if (e.getCause() instanceof GitProviderException) {
                throw (GitProviderException) e.getCause();
            }
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
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
    void processRepositoryFile_ShouldThrowException_WhenReviewFails() throws AIModelException, RequestInterruptedException {
        // Arrange
        AIModelException testException = new AIModelException("Test review generation failed");

        // Mock generateAIReview to throw exception
        doThrow(testException).when(codeReviewService).generateAIReview(anyString(), anyString());

        // Act & Assert - Expect the exception to propagate
        AIModelException thrown = assertThrows(AIModelException.class, () ->
            codeReviewService.processRepositoryFile(testFile, testGithubUrl, testGuidelines, testReviewOutput));

        assertEquals("Test review generation failed", thrown.getMessage());

        // Only the header should have been added before the exception was thrown
        String result = testReviewOutput.toString();
        assertTrue(result.startsWith("## File: " + testFile.getPath()));
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

    // Tests for chunk processing methods (refactored from processLargeFileInChunks)

    @Test
    void processFileChunks_ShouldProcessSingleChunk_WhenContentIsSmall() throws Exception {
        // Arrange
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        String smallContent = "public class Small { }";
        String guidelines = "Follow Java conventions";

        doReturn("Reviewed chunk content").when(codeReviewService)
                .processIndividualChunk(anyString(), anyString(), anyString(), anyInt(), anyInt());

        // Act
        CodeReviewService.ChunkProcessingResult result = codeReviewService.processFileChunks(
                TEST_REPOSITORY_URL, smallContent, guidelines);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalChunks());
        assertTrue(result.getReview().contains("1 chunks"));
        assertTrue(result.getReview().contains("Chunk 1 of 1"));
        assertTrue(result.getReview().contains("Reviewed chunk content"));

        verify(codeReviewService).processIndividualChunk(
                eq(TEST_REPOSITORY_URL), eq(smallContent), eq(guidelines), eq(1), eq(1));
    }

    @Test
    void processFileChunks_ShouldProcessMultipleChunks_WhenContentIsLarge() throws Exception {
        // Arrange - Use a more realistic chunk size to avoid infinite loops
        when(openAIConfig.getFileChunkSize()).thenReturn(30); // Smaller but reasonable chunk size
        String largeContent = "public class Large {\n    public void method1() {}\n    public void method2() {}\n}"; // 75 characters
        String guidelines = "Follow Java conventions";

        doReturn("Reviewed chunk 1").when(codeReviewService)
                .processIndividualChunk(eq(TEST_REPOSITORY_URL), anyString(), eq(guidelines), eq(1), anyInt());
        doReturn("Reviewed chunk 2").when(codeReviewService)
                .processIndividualChunk(eq(TEST_REPOSITORY_URL), anyString(), eq(guidelines), eq(2), anyInt());
        doReturn("Reviewed chunk 3").when(codeReviewService)
                .processIndividualChunk(eq(TEST_REPOSITORY_URL), anyString(), eq(guidelines), eq(3), anyInt());

        // Act
        CodeReviewService.ChunkProcessingResult result = codeReviewService.processFileChunks(
                TEST_REPOSITORY_URL, largeContent, guidelines);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalChunks() > 1);
        assertTrue(result.getTotalChunks() <= 5); // Reasonable upper bound to prevent infinite loops
        assertTrue(result.getReview().contains("Chunk 1 of"));
        assertTrue(result.getReview().contains("Reviewed chunk 1"));
        assertTrue(result.getReview().contains("Final Summary"));

        // Verify we don't have an excessive number of chunks
        verify(codeReviewService, atMost(5)).processIndividualChunk(
                eq(TEST_REPOSITORY_URL), anyString(), eq(guidelines), anyInt(), anyInt());
    }

    @Test
    void processChunkSafely_ShouldReturnReview_WhenProcessingSucceeds() throws Exception {
        // Arrange
        String chunk = "public class Test {}";
        String expectedReview = "This is a good class";

        doReturn(expectedReview).when(codeReviewService)
                .processIndividualChunk(TEST_REPOSITORY_URL, chunk, testGuidelines, 1, 1);

        // Act
        String result = codeReviewService.processChunkSafely(
                TEST_REPOSITORY_URL, chunk, testGuidelines, 1, 1);

        // Assert
        assertEquals(expectedReview, result);
    }

    @Test
    void processChunkSafely_ShouldReturnErrorMessage_WhenAIModelException() throws Exception {
        // Arrange
        String chunk = "public class Test {}";
        doThrow(new AIModelException("AI model failed")).when(codeReviewService)
                .processIndividualChunk(TEST_REPOSITORY_URL, chunk, testGuidelines, 1, 1);

        // Act
        String result = codeReviewService.processChunkSafely(
                TEST_REPOSITORY_URL, chunk, testGuidelines, 1, 1);

        // Assert
        assertTrue(result.contains("Error processing this chunk"));
        assertTrue(result.contains("AI model failed"));
    }

    @Test
    void createInitialReviewBuilder_ShouldCreateWithProperCapacity() {
        // Act
        StringBuilder builder = codeReviewService.createInitialReviewBuilder(5);

        // Assert
        assertNotNull(builder);
        String content = builder.toString();
        assertTrue(content.contains("# Code Review Summary"));
        assertTrue(content.contains("5 chunks"));
        // Verify capacity calculation (should be 5 * 1000 = 5000)
        assertTrue(builder.capacity() >= 5000);
    }

    @Test
    void createInitialReviewBuilder_ShouldLimitCapacity_WhenTotalChunksIsLarge() {
        // Act - Use a very large number of chunks
        StringBuilder builder = codeReviewService.createInitialReviewBuilder(200);

        // Assert
        assertNotNull(builder);
        // Verify capacity is capped at 100000
        assertTrue(builder.capacity() <= 100000);
    }

    @Test
    void createInitialReviewBuilder_ShouldHandleNegativeChunks() {
        // Act
        StringBuilder builder = codeReviewService.createInitialReviewBuilder(-5);

        // Assert
        assertNotNull(builder);
        // With -5 chunks, Math.abs(-5) * 1000 = 5000, so capacity should be >= 5000
        assertTrue(builder.capacity() >= 5000);
    }

    @Test
    void appendChunkResult_ShouldFormatChunkCorrectly() {
        // Arrange
        StringBuilder review = new StringBuilder();
        String chunkResult = "This chunk looks good";

        // Act
        codeReviewService.appendChunkResult(review, chunkResult, 2, 5);

        // Assert
        String result = review.toString();
        assertTrue(result.contains("## Chunk 2 of 5"));
        assertTrue(result.contains("This chunk looks good"));
        assertTrue(result.endsWith("\n\n"));
    }

    @Test
    void createFinalSummary_ShouldReturnProperSummary() {
        // Act
        String summary = codeReviewService.createFinalSummary();

        // Assert
        assertNotNull(summary);
        assertTrue(summary.contains("# Final Summary"));
        assertTrue(summary.contains("processing a large file in chunks")); // Fixed: match actual implementation
        assertTrue(summary.contains("individual chunk analyses"));
    }

    @Test
    void performPeriodicMemoryCleanup_ShouldBeCallable() {
        // This test just ensures the method doesn't throw exceptions
        // In a real test environment, you might want to mock System.gc()

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            codeReviewService.performPeriodicMemoryCleanup(2); // Even number, triggers GC
            codeReviewService.performPeriodicMemoryCleanup(3); // Odd number, no GC
        });
    }

    @Test
    void buildChunkedReviewResponse_ShouldCreateCompleteResponse() {
        // Arrange
        CodeReviewService.ChunkProcessingResult result =
                new CodeReviewService.ChunkProcessingResult("Test review content", 3);
        List<String> guidelines = Arrays.asList("Guideline 1", "Guideline 2");
        String expectedHtml = "<p>Test review content</p>";

        when(markdownConverter.convertMarkdownToHtml("Test review content")).thenReturn(expectedHtml);

        // Act
        CodeReviewResponse response = codeReviewService.buildChunkedReviewResponse(
                result, guidelines, TEST_REPOSITORY_URL);

        // Assert
        assertNotNull(response);
        assertEquals("Test review content", response.getReview());
        assertEquals(expectedHtml, response.getHtmlReview());
        assertEquals(guidelines, response.getGuidelines());
        assertEquals(TEST_REPOSITORY_URL, response.getRepositoryUrl());
        assertNotNull(response.getTimestamp());
        assertTrue(response.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));

        verify(markdownConverter).convertMarkdownToHtml("Test review content");
    }

    @Test
    void chunkProcessingResult_ShouldStoreDataCorrectly() {
        // Arrange
        String review = "Test review";
        int totalChunks = 5;

        // Act
        CodeReviewService.ChunkProcessingResult result =
                new CodeReviewService.ChunkProcessingResult(review, totalChunks);

        // Assert
        assertEquals(review, result.getReview());
        assertEquals(totalChunks, result.getTotalChunks());
    }

    // Integration test for the main chunk processing flow
    @Test
    void processLargeFileInChunks_ShouldWorkEndToEnd_InTestMode() throws Exception {
        // Arrange
        String largeContent = "public class Large {\n".repeat(100); // Create large content
        List<String> guidelines = Arrays.asList("Test guideline");
        String expectedHtml = "<h1>Test Mode Review</h1>";

        when(markdownConverter.convertMarkdownToHtml(anyString())).thenReturn(expectedHtml);

        // Enable test mode
        codeReviewService.setTestMode(true);

        // Act
        CodeReviewResponse response = invokePrivateMethod("processLargeFileInChunks",
                TEST_REPOSITORY_URL, largeContent, "formatted guidelines", guidelines);

        // Assert
        assertNotNull(response);
        assertTrue(response.getReview().contains("Test Mode Review"));
        assertTrue(response.getReview().contains("test mode with reduced memory usage"));
        assertEquals(expectedHtml, response.getHtmlReview());
        assertEquals(guidelines, response.getGuidelines());
        assertEquals(TEST_REPOSITORY_URL, response.getRepositoryUrl());

        // Reset test mode
        codeReviewService.setTestMode(false);
    }

    @Test
    void processLargeFileInChunks_ShouldWorkEndToEnd_InProductionMode() throws Exception {
        // Arrange
        String largeContent = "public class Large {\n    // This is a large file\n".repeat(50); // Create large content
        List<String> guidelines = Arrays.asList("Test guideline 1", "Test guideline 2");
        String formattedGuidelines = "Formatted guidelines";
        String expectedHtml = "<h1>Production Review</h1>";

        // Mock the chunk processing
        CodeReviewService.ChunkProcessingResult mockResult =
            new CodeReviewService.ChunkProcessingResult("Production review content", 2);

        // Use lenient stubbing to avoid unnecessary stubbing warnings
        lenient().when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        lenient().when(markdownConverter.convertMarkdownToHtml("Production review content")).thenReturn(expectedHtml);

        // Create a real service instance and spy on it
        CodeReviewService realService = new CodeReviewService(embeddingService, chatModel, openAIConfig, markdownConverter, gitProviderFactory);
        CodeReviewService spyService = spy(realService);
        doReturn(mockResult).when(spyService).processFileChunks(TEST_REPOSITORY_URL, largeContent, formattedGuidelines);

        // Ensure test mode is disabled (production mode)
        spyService.setTestMode(false);

        // Act
        CodeReviewResponse response = invokePrivateMethod(spyService, "processLargeFileInChunks",
                TEST_REPOSITORY_URL, largeContent, formattedGuidelines, guidelines);

        // Assert
        assertNotNull(response);
        assertEquals("Production review content", response.getReview());
        assertEquals(expectedHtml, response.getHtmlReview());
        assertEquals(guidelines, response.getGuidelines());
        assertEquals(TEST_REPOSITORY_URL, response.getRepositoryUrl());
        assertNotNull(response.getTimestamp());

        // Verify that the production path was taken
        verify(spyService).processFileChunks(TEST_REPOSITORY_URL, largeContent, formattedGuidelines);
        verify(spyService).buildChunkedReviewResponse(mockResult, guidelines, TEST_REPOSITORY_URL);
        verify(markdownConverter).convertMarkdownToHtml("Production review content");
    }

    @Test
    void processFileChunks_ShouldPropagateRuntimeException() throws AIModelException {
        // Arrange
        when(openAIConfig.getFileChunkSize()).thenReturn(1000);
        String content = "public class Test {}";
        String guidelines = "Some guidelines";
        // Simulate a RuntimeException in processIndividualChunk
        doAnswer(invocation -> { throw new RuntimeException("Unexpected error"); })
                .when(codeReviewService)
                .processIndividualChunk(anyString(), anyString(), anyString(), anyInt(), anyInt());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            codeReviewService.processFileChunks(TEST_REPOSITORY_URL, content, guidelines)
        );
    }
}
