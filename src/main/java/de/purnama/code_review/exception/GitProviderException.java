package de.purnama.code_review.exception;

/**
 * Exception thrown when there's an issue with any Git provider
 */
public class GitProviderException extends Exception {

    public GitProviderException(String message) {
        super(message);
    }

    public GitProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
