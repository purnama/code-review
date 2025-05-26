package de.purnama.code_review.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that processes OIDC user claims and stores them in the user's session
 * for easy access in Thymeleaf templates
 */
@Slf4j
@Component
public class OidcUserInfoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof DefaultOidcUser) {

            DefaultOidcUser oidcUser = (DefaultOidcUser) authentication.getPrincipal();
            Map<String, Object> claims = oidcUser.getUserInfo().getClaims();

            HttpSession session = request.getSession();

            // Extract and combine name information
            String givenName = (String) claims.getOrDefault("givenname", "");
            String familyName = (String) claims.getOrDefault("familyname", "");
            String email = (String) claims.getOrDefault("email", "");
            String pictureUrl = (String) claims.getOrDefault("picture", "");

            // Set full name, prefer combined given name and family name, fallback to email
            String fullName = givenName.isEmpty() && familyName.isEmpty()
                    ? email
                    : (givenName + " " + familyName).trim();

            // Store in session for easy access in Thymeleaf
            session.setAttribute("userName", fullName);
            session.setAttribute("userEmail", email);
            session.setAttribute("userPictureUrl", pictureUrl);

            log.debug("Set user info in session: name={}, email={}", fullName, email);
        }

        filterChain.doFilter(request, response);
    }
}
