package de.purnama.code_review.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.extern.slf4j.Slf4j;

/**
 * HomeController
 * 
 * Simple controller that handles the application's home page redirections
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Slf4j
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // Redirect to the Confluence URLs listing page as the main entry point
        return "redirect:/confluence-urls";
    }

    // Debug endpoint to inspect authentication object
    @GetMapping("/debug-auth")
    @ResponseBody
    public String debugAuth(Authentication authentication) {
        if (authentication == null) {
            return "No authentication object found";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Authentication Class: " + authentication.getClass().getName() + "<br>");
        builder.append("Name: " + authentication.getName() + "<br>");
        builder.append("Authorities: " + authentication.getAuthorities() + "<br>");
        builder.append("Details: " + authentication.getDetails() + "<br>");

        if (authentication.getPrincipal() instanceof DefaultOAuth2User) {
            DefaultOAuth2User oauth2User = (DefaultOAuth2User) authentication.getPrincipal();
            builder.append("<h3>OAuth2 User Attributes:</h3>");
            for (String key : oauth2User.getAttributes().keySet()) {
                builder.append(key + ": " + oauth2User.getAttributes().get(key) + "<br>");
            }
        } else {
            builder.append("Principal Type: " + authentication.getPrincipal().getClass().getName() + "<br>");
            builder.append("Principal: " + authentication.getPrincipal() + "<br>");
        }

        log.debug("Authentication debug info: {}", builder.toString().replaceAll("<br>", "\n"));

        return builder.toString();
    }
}
