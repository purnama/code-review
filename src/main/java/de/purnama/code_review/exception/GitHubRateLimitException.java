package de.purnama.code_review.exception;

/**
 * GitHubRateLimitException
 * Exception thrown when GitHub API rate limit is exceeded
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class GitHubRateLimitException extends GitHubException {

    public GitHubRateLimitException(String message) {
        super(message);
    }

    public GitHubRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}