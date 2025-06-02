package de.purnama.code_review.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Extended tests for OAuth2AuthenticationSuccessHandler to increase code coverage to 100%
 */
class OAuth2AuthenticationSuccessHandlerTestExtended {

    private OAuth2AuthenticationSuccessHandler handler;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Authentication authentication;
    @Mock
    private HttpSession session;
    @Mock
    private OAuth2AuthenticationToken oAuth2AuthenticationToken;

    private static class TestRedirectStrategy implements RedirectStrategy {
        private String redirectedUrl;

        @Override
        public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
            this.redirectedUrl = url;
        }

        public String getRedirectedUrl() {
            return redirectedUrl;
        }
    }

    private TestRedirectStrategy redirectStrategy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new OAuth2AuthenticationSuccessHandler();
        redirectStrategy = new TestRedirectStrategy();
        handler.setRedirectStrategy(redirectStrategy);
    }

    @Test
    void onAuthenticationSuccess_withOAuth2Token_shouldLogDetailsAndRedirect() throws Exception {
        // Setup
        when(request.getSession(false)).thenReturn(null);
        when(response.isCommitted()).thenReturn(false);

        // Create DefaultOidcUser for the main authentication
        DefaultOidcUser oidcUser = createOidcUser();
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        // Mock OAuth2AuthenticationToken scenario
        OAuth2AuthenticationToken token = mock(OAuth2AuthenticationToken.class);
        DefaultOAuth2User oauth2User = mock(DefaultOAuth2User.class);

        // Setup attributes for DefaultOAuth2User
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "Test User");
        attributes.put("email", "test@example.com");
        attributes.put("pictureUrl", "http://example.com/picture.jpg");

        // Set up the mocks to handle the instanceof check
        when(authentication instanceof OAuth2AuthenticationToken).thenReturn(true);
        when(authentication.getClass()).thenReturn((Class)OAuth2AuthenticationToken.class);
        doReturn(oauth2User).when(token).getPrincipal();
        doReturn(token).when(authentication).getPrincipal();
        doReturn(attributes).when(oauth2User).getAttributes();

        // Execute
        handler.onAuthenticationSuccess(request, response, authentication);

        // Verify
        assertEquals("/", redirectStrategy.getRedirectedUrl());
    }

    @Test
    void onAuthenticationSuccess_whenResponseIsCommitted_shouldNotRedirect() throws Exception {
        // Setup
        when(request.getSession(false)).thenReturn(null);
        when(response.isCommitted()).thenReturn(true); // Response already committed

        // Create DefaultOidcUser
        DefaultOidcUser oidcUser = createOidcUser();
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        // Execute
        handler.onAuthenticationSuccess(request, response, authentication);

        // No redirect should happen
        assertNull(redirectStrategy.getRedirectedUrl());
    }

    @Test
    void isAuthorizedRedirectUri_shouldAllowValidPaths() throws Exception {
        // Access private method via reflection
        Method method = OAuth2AuthenticationSuccessHandler.class
                .getDeclaredMethod("isAuthorizedRedirectUri", String.class);
        method.setAccessible(true);

        // Test valid paths
        assertTrue((Boolean) method.invoke(handler, "/dashboard"));
        assertTrue((Boolean) method.invoke(handler, "/profile"));
        assertTrue((Boolean) method.invoke(handler, "/settings"));
    }

    @Test
    void isAuthorizedRedirectUri_shouldRejectInvalidPaths() throws Exception {
        // Access private method via reflection
        Method method = OAuth2AuthenticationSuccessHandler.class
                .getDeclaredMethod("isAuthorizedRedirectUri", String.class);
        method.setAccessible(true);

        // Test invalid paths
        assertFalse((Boolean) method.invoke(handler, "//example.com"));
        assertFalse((Boolean) method.invoke(handler, "/\\malicious"));
        assertFalse((Boolean) method.invoke(handler, "http://external.com"));
    }

    // Helper methods

    private DefaultOidcUser createOidcUser() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("email", "john.doe@example.com");
        claims.put("picture", "http://example.com/pic.jpg");

        OidcUserInfo userInfo = new OidcUserInfo(claims);
        Collection<String> authorities = Collections.emptyList();
        OidcIdToken idToken = new OidcIdToken("tokenValue", Instant.now(),
                Instant.now().plusSeconds(3600), claims);

        return new DefaultOidcUser(Collections.emptyList(), idToken, userInfo);
    }
}
