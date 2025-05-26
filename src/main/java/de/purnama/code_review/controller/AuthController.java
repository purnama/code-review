package de.purnama.code_review.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for authentication-related endpoints
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Display login page
     * 
     * @param model Model for the view
     * @param error Error message if any
     * @param redirectUri URI to redirect to after successful login
     * @param request HTTP request
     * @return The login view
     */
    @GetMapping("/login")
    public String login(Model model, 
                       @RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                       HttpServletRequest request) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return "redirect:/";
        }
        
        // Store redirect URI in session for use after authentication
        if (redirectUri != null && !redirectUri.isEmpty()) {
            HttpSession session = request.getSession(true);
            session.setAttribute(REDIRECT_URI_PARAM_COOKIE_NAME, redirectUri);
        }
        
        if (error != null) {
            model.addAttribute("error", "Authentication error. Please try again.");
        }

        // Add all available OAuth2 providers to the model
        model.addAttribute("microsoftActive", clientRegistrationRepository.findByRegistrationId("microsoft") != null);
        model.addAttribute("googleActive", clientRegistrationRepository.findByRegistrationId("google") != null);
        model.addAttribute("facebookActive", clientRegistrationRepository.findByRegistrationId("facebook") != null);
        
        return "auth/login";
    }

    /**
     * Handle logout
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param redirectAttributes Redirect attributes
     * @return Redirect to home page
     */
    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, 
                        RedirectAttributes redirectAttributes) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        
        redirectAttributes.addFlashAttribute("message", "You have been logged out successfully.");
        return "redirect:/";
    }

    /**
     * Access denied page
     * 
     * @return Access denied view
     */
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
}
