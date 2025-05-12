package de.purnama.code_review.exception;

/**
 * AIModelException
 * 
 * Exception thrown when there's an issue with AI model interaction.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class AIModelException extends CodeReviewException {
    
    public AIModelException(String message) {
        super(message);
    }
    
    public AIModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
