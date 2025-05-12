package de.purnama.code_review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import de.purnama.code_review.exception.CodeReviewException;
import de.purnama.code_review.model.CodeReviewRequest;
import de.purnama.code_review.model.CodeReviewResponse;
import de.purnama.code_review.service.CodeReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CodeReviewController
 * Controller for handling code review requests and displaying results
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
@Slf4j
public class CodeReviewController {

    private final CodeReviewService codeReviewService;

    @GetMapping
    public String showReviewForm(Model model) {
        model.addAttribute("reviewRequest", new CodeReviewRequest());
        return "review/form";
    }

    /**
     * Process a code review request
     * The exceptions will be handled by GlobalExceptionHandler
     */
    @PostMapping
    public String performReview(
            @ModelAttribute("reviewRequest") CodeReviewRequest request,
            Model model) throws CodeReviewException {

        log.info("Received code review request: {}", request);

        // Perform the review - this will throw appropriate exceptions when needed
        CodeReviewResponse response = codeReviewService.reviewCode(request);

        model.addAttribute("reviewResponse", response);
        model.addAttribute("requestDetails", request);
        return "review/result";
    }
}