package de.purnama.code_review.exception;

/**
 * Exception thrown when a request is interrupted
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class RequestInterruptedException extends CodeReviewException {

    public RequestInterruptedException(String message) {
        super(message);
    }

    public RequestInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}