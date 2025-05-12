package de.purnama.code_review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController
 * 
 * Simple controller that handles the application's home page redirections
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // Redirect to the Confluence URLs listing page as the main entry point
        return "redirect:/confluence-urls";
    }
}