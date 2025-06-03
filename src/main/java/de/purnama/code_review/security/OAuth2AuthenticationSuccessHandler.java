package de.purnama.code_review.security;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler for successful OAuth2 authentication
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String DEFAULT_TARGET_URL = "/";
    private static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) 
            throws IOException, ServletException {

        // Debug authentication object
        logAuthenticationDetails(authentication);

        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to {}", targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Log detailed information about the authentication object for debugging
     */
    private void logAuthenticationDetails(Authentication authentication) {
        log.info("Authentication successful. Authentication class: {}", authentication.getClass().getName());
        log.info("Authentication name: {}", authentication.getName());
        log.info("Authentication authorities: {}", authentication.getAuthorities());

        Object principal = authentication.getPrincipal();

        // Handle OIDC authentication
        if (principal instanceof DefaultOidcUser) {
            DefaultOidcUser oidcUser = (DefaultOidcUser) principal;
            Map<String, Object> claims = oidcUser.getUserInfo().getClaims();
            log.info("Name: {}", claims.get("givenname") + " " + claims.get("familyname"));
            log.info("Email: {}", claims.get("email"));
            log.info("picture: {}", claims.get("picture"));
        }

        // Handle OAuth2 authentication
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2User oauth2User = ((OAuth2AuthenticationToken) authentication).getPrincipal();
            log.info("OAuth2 principal class: {}", oauth2User.getClass().getName());

            if (oauth2User instanceof DefaultOAuth2User) {
                DefaultOAuth2User defaultOAuth2User = (DefaultOAuth2User) oauth2User;
                Map<String, Object> attributes = defaultOAuth2User.getAttributes();

                log.info("OAuth2 User attributes keys: {}", attributes.keySet());

                // Log each attribute and its value
                attributes.forEach((key, value) -> {
                    if (value != null) {
                        log.info("OAuth2 User attribute - {}: {} ({})", key, value, value.getClass().getName());
                    } else {
                        log.info("OAuth2 User attribute - {}: null", key);
                    }
                });

                // Special attention to pictureUrl which seems to be causing issues
                log.info("Does attributes contain 'pictureUrl'? {}", attributes.containsKey("pictureUrl"));
                if (attributes.containsKey("pictureUrl")) {
                    Object pictureUrl = attributes.get("pictureUrl");
                    log.info("pictureUrl value: {}, class: {}", pictureUrl,
                            pictureUrl != null ? pictureUrl.getClass().getName() : "null");
                }
            }
        }
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        Optional<String> redirectUri = getRedirectUri(request);

        if (redirectUri.isPresent() && isAuthorizedRedirectUri(redirectUri.get())) {
            return redirectUri.get();
        }

        return DEFAULT_TARGET_URL;
    }

    private Optional<String> getRedirectUri(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String redirectUri = (String) session.getAttribute(REDIRECT_URI_PARAM_COOKIE_NAME);
            if (redirectUri != null) {
                session.removeAttribute(REDIRECT_URI_PARAM_COOKIE_NAME);
                return Optional.of(redirectUri);
            }
        }
        return Optional.empty();
    }

    private boolean isAuthorizedRedirectUri(String uri) {
        // A simple check to ensure URI starts with our application's base URL
        // In a production environment, you might want to check against a whitelist of allowed redirect URIs
        return uri.startsWith("/") && !uri.startsWith("//") && !uri.startsWith("/\\");
    }
}
