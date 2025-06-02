package de.purnama.code_review.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OAuth2AuthenticationSuccessHandlerTest {

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

    // Create a test redirect strategy to capture the URL
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
    void onAuthenticationSuccess_shouldRedirectToDefault_whenNoRedirectUri() throws Exception {
        // Setup request mock to return null session (no redirect URI)
        when(request.getSession(false)).thenReturn(null);
        when(response.isCommitted()).thenReturn(false);

        // Mock DefaultOidcUser and its getUserInfo().getClaims()
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        var userInfo = mock(org.springframework.security.oauth2.core.oidc.OidcUserInfo.class);
        Map<String, Object> claims = new HashMap<>();
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("email", "john.doe@example.com");
        claims.put("picture", "http://example.com/pic.jpg");
        when(userInfo.getClaims()).thenReturn(claims);
        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        // Call the method under test
        handler.onAuthenticationSuccess(request, response, authentication);

        // Verify the redirect URL was set to the default
        assertEquals("/", redirectStrategy.getRedirectedUrl());
    }

    @Test
    void onAuthenticationSuccess_shouldNotRedirect_whenResponseIsCommitted() throws Exception {
        // Setup
        when(request.getSession(false)).thenReturn(null);
        when(response.isCommitted()).thenReturn(true);

        // Mock DefaultOidcUser and its getUserInfo().getClaims()
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        var userInfo = mock(org.springframework.security.oauth2.core.oidc.OidcUserInfo.class);
        Map<String, Object> claims = new HashMap<>();
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("email", "john.doe@example.com");
        claims.put("picture", "http://example.com/pic.jpg");
        when(userInfo.getClaims()).thenReturn(claims);
        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        // Call the method under test
        handler.onAuthenticationSuccess(request, response, authentication);

        // Verify redirect was never called due to committed response
        assertNull(redirectStrategy.getRedirectedUrl());
    }

    @Test
    void determineTargetUrl_shouldReturnSessionRedirectUri_whenAuthorized() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("redirect_uri")).thenReturn("/dashboard");

        String url = handler.determineTargetUrl(request, response, authentication);
        assertEquals("/dashboard", url);
        verify(session).removeAttribute("redirect_uri");
    }

    @Test
    void determineTargetUrl_shouldReturnDefault_whenUnauthorizedUri() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("redirect_uri")).thenReturn("//malicious");

        String url = handler.determineTargetUrl(request, response, authentication);
        assertEquals("/", url);
        verify(session).removeAttribute("redirect_uri");
    }

    @Test
    void determineTargetUrl_shouldReturnDefault_whenNoSession() {
        when(request.getSession(false)).thenReturn(null);
        String url = handler.determineTargetUrl(request, response, authentication);
        assertEquals("/", url);
    }

    // Additional test to cover isAuthorizedRedirectUri method
    @Test
    void isAuthorizedRedirectUri_shouldReturnTrue_forValidPaths() throws Exception {
        // Access private method via reflection
        Method method = OAuth2AuthenticationSuccessHandler.class.getDeclaredMethod("isAuthorizedRedirectUri", String.class);
        method.setAccessible(true);

        // Test valid local paths
        assertTrue((Boolean) method.invoke(handler, "/dashboard"));
        assertTrue((Boolean) method.invoke(handler, "/profile"));
    }

    @Test
    void isAuthorizedRedirectUri_shouldReturnFalse_forInvalidPaths() throws Exception {
        // Access private method via reflection
        Method method = OAuth2AuthenticationSuccessHandler.class.getDeclaredMethod("isAuthorizedRedirectUri", String.class);
        method.setAccessible(true);

        // Test invalid paths
        assertFalse((Boolean) method.invoke(handler, "//external-site.com"));
        assertFalse((Boolean) method.invoke(handler, "/\\malicious"));
    }

    // Helper methods for testing
    private static void assertTrue(boolean condition) {
        assertEquals(true, condition);
    }

    private static void assertFalse(boolean condition) {
        assertEquals(false, condition);
    }
}
