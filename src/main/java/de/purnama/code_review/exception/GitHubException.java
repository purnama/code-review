package de.purnama.code_review.exception;

/**
 * GitHubException
 * Exception thrown when there's an issue with GitHub operations.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class GitHubException extends CodeReviewException {
    
    public GitHubException(String message) {
        super(message);
    }
    
    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
