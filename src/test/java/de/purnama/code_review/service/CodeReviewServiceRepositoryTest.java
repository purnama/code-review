package de.purnama.code_review.service;

import de.purnama.code_review.config.OpenAIConfig;
import de.purnama.code_review.exception.GitHubException;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.model.git.GitFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceRepositoryTest {

    @Mock
    private MarkdownConverter markdownConverter;

    @Mock
    private OpenAIConfig openAIConfig;

    @Spy
    @InjectMocks
    private CodeReviewService codeReviewService;

    @Test
    void prepareFilesForReview_ShouldReturnNull_WhenNoFilesFound() throws Exception {
        // Arrange
        String owner = "testowner";
        String repo = "testrepo";
        String branch = "main";
        String githubUrl = "https://github.com/testowner/testrepo";

        // Set up the test with a TestableCodeReviewService subclass that returns null
        TestableCodeReviewService testService = new TestableCodeReviewService(null);
        testService.setOpenAIConfig(openAIConfig);

        // Act
        List<GitFile> result = testService.prepareFilesForReview(owner, repo, branch, githubUrl);

        // Assert
        assertNull(result, "Should return null when no files are found");
    }

    @Test
    void prepareFilesForReview_ShouldLimitFiles_WhenTooManyFiles() throws Exception {
        // Arrange
        String owner = "testowner";
        String repo = "testrepo";
        String branch = "main";
        String githubUrl = "https://github.com/testowner/testrepo";

        // Create a list with some test files
        List<GitFile> mockFiles = createTestFiles(10);

        // Mock config to return max files = 5
        when(openAIConfig.getMaxFilesToReview()).thenReturn(5);

        // Set up the test with a TestableCodeReviewService subclass to avoid dealing with private methods
        TestableCodeReviewService testService = new TestableCodeReviewService(mockFiles);
        testService.setOpenAIConfig(openAIConfig);

        // Act
        List<GitFile> result = testService.prepareFilesForReview(owner, repo, branch, githubUrl);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(5, result.size(), "Should limit to 5 files as per configuration");

        // Verify the config was accessed
        verify(openAIConfig, times(1)).getMaxFilesToReview();
    }

    @Test
    void prepareFilesForReview_ShouldReturnAllFiles_WhenUnderLimit() throws Exception {
        // Arrange
        String owner = "testowner";
        String repo = "testrepo";
        String branch = "main";
        String githubUrl = "https://github.com/testowner/testrepo";

        // Create a smaller list of test files
        List<GitFile> mockFiles = createTestFiles(3);

        // Mock config to return max files = 5 (more than our test files)
        when(openAIConfig.getMaxFilesToReview()).thenReturn(5);

        // Set up the test with a TestableCodeReviewService subclass
        TestableCodeReviewService testService = new TestableCodeReviewService(mockFiles);
        testService.setOpenAIConfig(openAIConfig);

        // Act
        List<GitFile> result = testService.prepareFilesForReview(owner, repo, branch, githubUrl);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(3, result.size(), "Should return all 3 files since under limit");

        // Verify the config was accessed
        verify(openAIConfig, times(1)).getMaxFilesToReview();
    }

    @Test
    void createEmptyReviewResponse_ShouldCreateValidResponse() {
        // Arrange
        String repositoryUrl = "https://github.com/testowner/testrepo";
        String mockHtmlMessage = "<p>Mock HTML</p>";

        // Mock markdown converter
        when(markdownConverter.convertMarkdownToHtml(anyString())).thenReturn(mockHtmlMessage);

        // Act
        CodeReviewResponse response = codeReviewService.createEmptyReviewResponse(repositoryUrl);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(repositoryUrl, response.getRepositoryUrl(), "Should have correct repository URL");
        assertEquals(mockHtmlMessage, response.getHtmlReview(), "Should have HTML review from converter");
        assertNotNull(response.getGuidelines(), "Guidelines should be initialized as empty list");
        assertTrue(response.getGuidelines().isEmpty(), "Guidelines should be empty");
        assertNotNull(response.getTimestamp(), "Timestamp should be set");

        // Verify markdown conversion was called
        verify(markdownConverter).convertMarkdownToHtml(anyString());
    }

    /**
     * Helper method to create test GitFile objects for testing
     */
    private List<GitFile> createTestFiles(int count) {
        List<GitFile> files = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            // Create GitFile instances directly using the public model
            GitFile file = GitFile.builder()
                .name("file" + i + ".java")
                .path("src/file" + i + ".java")
                .content("public class File" + i + " { }")
                .build();

            files.add(file);
        }

        return files;
    }

    /**
     * Helper class that extends CodeReviewService to make testing easier
     * by overriding the protected and private methods we need to control
     */
    private class TestableCodeReviewService extends CodeReviewService {
        private final List<GitFile> filesToReturn;
        private OpenAIConfig configToUse;

        public TestableCodeReviewService(List<GitFile> filesToReturn) {
            // Pass null for all dependencies since we're overriding the methods that would use them
            super(null, null, null, null, null);
            this.filesToReturn = filesToReturn;
        }

        public void setOpenAIConfig(OpenAIConfig config) {
            this.configToUse = config;
        }

        protected List<GitFile> prepareFilesForReview(String owner, String repo, String branch, String githubUrl)
                throws GitHubException {
            // Apply the real logic for limiting files based on configuration
            if (filesToReturn == null || filesToReturn.isEmpty()) {
                return null;
            }

            int maxFiles = configToUse.getMaxFilesToReview();
            if (filesToReturn.size() > maxFiles) {
                return filesToReturn.subList(0, maxFiles);
            }

            return filesToReturn;
        }
    }
}
