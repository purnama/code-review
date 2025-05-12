package de.purnama.code_review.exception;

/**
 * Exception thrown when there's an issue with the code review input.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class InvalidCodeReviewRequestException extends CodeReviewException {
    
    public InvalidCodeReviewRequestException(String message) {
        super(message);
    }
    
    public InvalidCodeReviewRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
