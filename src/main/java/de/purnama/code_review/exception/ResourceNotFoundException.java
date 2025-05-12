package de.purnama.code_review.exception;

/**
 * Exception thrown when a resource is not found.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class ResourceNotFoundException extends CodeReviewException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
