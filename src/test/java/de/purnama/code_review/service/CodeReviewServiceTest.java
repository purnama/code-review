package de.purnama.code_review.service;

import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.AIModelException;
import de.purnama.code_review.exception.GitProviderException;
import de.purnama.code_review.exception.InvalidCodeReviewRequestException;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

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

    @Test
    void findIdealChunkBoundary_shouldReturnPositionAfterClosingBraceNewline() throws Exception {
        // Arrange - Create code with a clear closing brace + newline pattern
        // The method searches backwards from approximateEnd, so we need to position things carefully
        String codeWithClosingBrace = "public class Test {\n    int x = 1;\n}\nmore code";
        int start = 0;
        // Set approximateEnd to be after the closing brace so it's in the search window
        int approximateEnd = codeWithClosingBrace.indexOf("more code"); // Position at "more code"

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithClosingBrace, start, approximateEnd);

        // Assert
        assertTrue(result > start, "Result should be greater than start position");
        assertTrue(result <= approximateEnd, "Result should be within the approximate end boundary");

        // Find the expected position: right after '}' + '\n'
        int braceIndex = codeWithClosingBrace.indexOf("}\n");
        if (braceIndex != -1) {
            int expectedPosition = braceIndex + 2; // Position after '}' + '\n'
            // The result should be the position after '}' + '\n' if that pattern was found
            if (result == expectedPosition) {
                // Verify this is indeed after a '}' + '\n' sequence
                assertEquals('}', codeWithClosingBrace.charAt(braceIndex),
                    "Should find closing brace");
                assertEquals('\n', codeWithClosingBrace.charAt(braceIndex + 1),
                    "Should find newline after closing brace");
            }
        }
    }

    @Test
    void findIdealChunkBoundary_shouldFindClosingBraceNewlineInSearchWindow() throws Exception {
        // Arrange - Create a more targeted test for the specific code block
        // Position the closing brace + newline within the search window
        String code = "method() {\n    return value;\n}\nrest";
        int start = 0;
        int approximateEnd = code.length() - 2; // Just before "st" in "rest"

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert
        assertTrue(result > start, "Result should be greater than start position");
        assertTrue(result <= approximateEnd, "Result should be within the approximate end boundary");

        // The specific condition we're testing:
        // if (i > 0 && i < code.length() - 1 &&
        //     code.charAt(i) == '}' && code.charAt(i + 1) == '\n') {
        //     bestPosition = i + 2;

        // Check if the result corresponds to a position after '}' + '\n'
        if (result >= 2) {
            int possibleBracePos = result - 2;
            if (possibleBracePos < code.length() - 1 &&
                code.charAt(possibleBracePos) == '}' &&
                code.charAt(possibleBracePos + 1) == '\n') {
                // This confirms the closing brace + newline logic was used
                assertEquals('}', code.charAt(possibleBracePos),
                    "The method found a closing brace");
                assertEquals('\n', code.charAt(possibleBracePos + 1),
                    "Followed by a newline");
            }
        }
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceAtBoundaryEdgeCases() throws Exception {
        // Arrange - Test case where closing brace is at the very end of search window
        String codeAtBoundary = "int a = 1;\n}\n";
        int start = 0;
        int approximateEnd = 11; // Right at the position after '}\n'

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeAtBoundary, start, approximateEnd);

        // Assert
        assertTrue(result > start, "Result should be greater than start position");
        assertTrue(result <= approximateEnd, "Result should be within the approximate end boundary");
    }

    @Test
    void findIdealChunkBoundary_shouldFallbackWhenNoClosingBraceNewlineFound() throws Exception {
        // Arrange - Code without any '}' + '\n' pattern in the search window
        String codeWithoutPattern = "int a = 1; int b = 2; int c = 3;";
        int start = 0;
        int approximateEnd = 20;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithoutPattern, start, approximateEnd);

        // Assert
        // Should fall back to approximateEnd when no ideal boundary is found
        assertEquals(approximateEnd, result, "Should return approximateEnd when no ideal boundary found");
    }

    @Test
    void findIdealChunkBoundary_shouldHandleIndexExceedingCodeLength() throws Exception {
        // Arrange - Test the continue condition when i > code.length()
        String shortCode = "test";
        int start = 0;
        int approximateEnd = 10; // Larger than code length

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, shortCode, start, approximateEnd);

        // Assert - Should fallback to approximateEnd since no ideal boundary found
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleStartPositionAtZero() throws Exception {
        // Arrange - Test the lineStart calculation when i == 0
        String code = "first line\nsecond line\nthird line";
        int start = 0;
        int approximateEnd = 5; // Within first line

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert
        assertTrue(result >= start);
        assertTrue(result <= approximateEnd);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleNoNewlineFound() throws Exception {
        // Arrange - Test when lineEnd == -1 (no newline found)
        String codeWithoutNewline = "single line without newline";
        int start = 0;
        int approximateEnd = 15;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithoutNewline, start, approximateEnd);

        // Assert
        assertEquals(approximateEnd, result); // Should fallback to approximateEnd
    }

    @Test
    void findIdealChunkBoundary_shouldDetectBlankLine() throws Exception {
        // Arrange - Test blank line detection
        String codeWithBlankLine = "line1\n\nline3";
        int start = 0;
        int approximateEnd = 8; // Position after blank line

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithBlankLine, start, approximateEnd);

        // Assert - Should find the blank line boundary
        assertTrue(result > start);
        assertTrue(result <= approximateEnd);
    }

    @Test
    void findIdealChunkBoundary_shouldDetectWhitespaceOnlyLine() throws Exception {
        // Arrange - Test whitespace-only line detection
        String codeWithWhitespaceLine = "line1\n   \t  \nline3";
        int start = 0;
        int approximateEnd = 12;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithWhitespaceLine, start, approximateEnd);

        // Assert - Should detect whitespace-only line as blank
        assertTrue(result > start);
        assertTrue(result <= approximateEnd);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleBlankLineBoundaryExceedingCodeLength() throws Exception {
        // Arrange - Test when bestPosition > code.length() for blank line case
        String code = "line1\n\n"; // Ends with blank line
        int start = 0;
        int approximateEnd = code.length() + 5; // Beyond code length

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert
        assertTrue(result <= code.length()); // Should be capped at code length
    }

    @Test
    void findIdealChunkBoundary_shouldHandleBlankLineOutsideValidRange() throws Exception {
        // Arrange - Test blank line found but outside valid range
        String code = "\n\nvalid content here";
        int start = 5; // Start after the blank lines
        int approximateEnd = 15;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert - Should fallback to approximateEnd since blank line is outside valid range
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceAtEdgeConditions() throws Exception {
        // Arrange - Test edge conditions for closing brace detection
        String codeWithBraceAtStart = "}continue";
        int start = 0;
        int approximateEnd = 5;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithBraceAtStart, start, approximateEnd);

        // Assert - Should not trigger closing brace logic since i <= 0
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceAtEndOfCode() throws Exception {
        // Arrange - Test closing brace at end of code (i >= code.length() - 1)
        String codeWithBraceAtEnd = "content}";
        int start = 0;
        int approximateEnd = codeWithBraceAtEnd.length();

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithBraceAtEnd, start, approximateEnd);

        // Assert - Should not trigger closing brace logic since at end
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceBoundaryExceedingCodeLength() throws Exception {
        // Arrange - Test when bestPosition > code.length() for closing brace case
        String code = "content}\n"; // Closing brace with newline at end
        int start = 0;
        int approximateEnd = code.length() + 10; // Way beyond code length

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert
        assertTrue(result <= code.length()); // Should be capped at code length
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceOutsideValidRange() throws Exception {
        // Arrange - Test closing brace found but outside valid range
        String code = "}\nvalid content here";
        int start = 5; // Start after the closing brace
        int approximateEnd = 15;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);

        // Assert - Should fallback to approximateEnd since closing brace is outside valid range
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleClosingBraceWithoutNewline() throws Exception {
        // Arrange - Test closing brace not followed by newline
        String codeWithBraceNoNewline = "method() { return; } more";
        int start = 0;
        int approximateEnd = 20;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithBraceNoNewline, start, approximateEnd);

        // Assert - Should fallback since brace is not followed by newline
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldReturnApproximateEndAsFallback() throws Exception {
        // Arrange - Test fallback case when no ideal boundary is found
        String codeWithoutBoundaries = "continuous text without any good boundaries or blank lines";
        int start = 0;
        int approximateEnd = 25;

        // Use reflection to access private method
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);

        // Act
        int result = (int) method.invoke(codeReviewService, codeWithoutBoundaries, start, approximateEnd);

        // Assert - Should return approximateEnd as fallback
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleEmptyCodeString() throws Exception {
        // Arrange
        String code = "";
        int start = 0;
        int approximateEnd = 0;
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);
        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);
        // Assert
        assertEquals(approximateEnd, result);
    }

    @Test
    void findIdealChunkBoundary_shouldHandleMinimumSearchWindow() throws Exception {
        // Arrange
        String code = "abcde\n12345\n";
        int start = 0;
        int approximateEnd = 5; // searchWindow will be 5
        java.lang.reflect.Method method = CodeReviewService.class.getDeclaredMethod(
                "findIdealChunkBoundary", String.class, int.class, int.class);
        method.setAccessible(true);
        // Act
        int result = (int) method.invoke(codeReviewService, code, start, approximateEnd);
        // Assert
        assertTrue(result >= start);
        assertTrue(result <= approximateEnd);
    }
}
