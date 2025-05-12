package de.purnama.code_review.handler;

import de.purnama.code_review.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for the application.
 * Maps exceptions to appropriate error pages and HTTP status codes.
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles any CodeReviewException not handled by more specific handlers
     */
    @ExceptionHandler(CodeReviewException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleCodeReviewException(CodeReviewException ex, Model model, HttpServletRequest request) {
        log.error("Code review error: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/error";
    }

    /**
     * Handles resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFoundException(ResourceNotFoundException ex, Model model, HttpServletRequest request) {
        log.error("Resource not found: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/404";
    }

    /**
     * Handles invalid code review requests
     */
    @ExceptionHandler(InvalidCodeReviewRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidCodeReviewRequestException(InvalidCodeReviewRequestException ex, Model model, HttpServletRequest request) {
        log.error("Invalid request: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/400";
    }

    /**
     * Handles GitHub-related exceptions
     */
    @ExceptionHandler({GitHubException.class, GitHubRateLimitException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleGitHubException(CodeReviewException ex, Model model, HttpServletRequest request) {
        log.error("GitHub error: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("error", "Service Unavailable");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/503";
    }

    /**
     * Handles AI model exceptions
     */
    @ExceptionHandler(AIModelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleAIModelException(AIModelException ex, Model model, HttpServletRequest request) {
        log.error("AI model error: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("error", "Service Unavailable");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/503";
    }

    /**
     * Handles Confluence-related exceptions
     */
    @ExceptionHandler(ConfluenceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleConfluenceException(ConfluenceException ex, Model model, HttpServletRequest request) {
        log.error("Confluence error: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("error", "Service Unavailable");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());

        return "error/503";
    }

    /**
     * Handles 404 errors
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoHandlerFoundException ex, Model model, HttpServletRequest request) {
        log.error("Page not found: {}", ex.getMessage());

        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("error", "Page Not Found");
        model.addAttribute("message", "The page you are looking for does not exist");
        model.addAttribute("path", request.getRequestURI());

        return "error/404";
    }

    /**
     * Fallback handler for any uncaught exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "An unexpected error occurred");
        model.addAttribute("path", request.getRequestURI());

        return "error/500";
    }
}