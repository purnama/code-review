package de.purnama.code_review.exception;

/**
 * CodeReviewException
 * 
 * Base exception class for the Code Review application.
 * All custom exceptions should extend this class.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
public class CodeReviewException extends Exception {
    
    public CodeReviewException(String message) {
        super(message);
    }
    
    public CodeReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
