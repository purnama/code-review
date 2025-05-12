package de.purnama.code_review.exception;

/**
 * ConfluenceException
 * 
 * Exception thrown when there's an issue with Confluence operations
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class ConfluenceException extends CodeReviewException {
    
    public ConfluenceException(String message) {
        super(message);
    }
    
    public ConfluenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
