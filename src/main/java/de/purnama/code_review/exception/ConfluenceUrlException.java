package de.purnama.code_review.exception;

/**
 * ConfluenceUrlException
 * Exception for Confluence URL related errors
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class ConfluenceUrlException extends RuntimeException {
    
    public ConfluenceUrlException(String message) {
        super(message);
    }
    
    public ConfluenceUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
