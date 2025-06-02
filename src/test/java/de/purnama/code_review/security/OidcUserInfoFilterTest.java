package de.purnama.code_review.security;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcUserInfoFilterTest {

    @InjectMocks
    private OidcUserInfoFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private DefaultOidcUser oidcUser;

    @Mock
    private OidcUserInfo userInfo;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        // Don't stub here what's not needed for all tests
        // Only set up the session stub which is used by all tests
        when(request.getSession()).thenReturn(session);
    }

    @Test
    void doFilterInternal_WhenUserIsAuthenticated_ShouldStoreUserInfoInSession() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("email", "john.doe@example.com");
        claims.put("picture", "https://example.com/pic.jpg");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session).setAttribute("userName", "John Doe");
        verify(session).setAttribute("userEmail", "john.doe@example.com");
        verify(session).setAttribute("userPictureUrl", "https://example.com/pic.jpg");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenOnlyGivenNamePresent_ShouldUseOnlyGivenName() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("givenname", "John");
        claims.put("email", "john@example.com");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session).setAttribute("userName", "John");
        verify(session).setAttribute("userEmail", "john@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenOnlyFamilyNamePresent_ShouldUseOnlyFamilyName() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("familyname", "Doe");
        claims.put("email", "doe@example.com");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session).setAttribute("userName", "Doe");
        verify(session).setAttribute("userEmail", "doe@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenNoNamePresent_ShouldUseEmail() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "anonymous@example.com");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session).setAttribute("userName", "anonymous@example.com");
        verify(session).setAttribute("userEmail", "anonymous@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenUserIsNotAuthenticated_ShouldJustContinueFilterChain() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenPrincipalIsNotOidcUser_ShouldJustContinueFilterChain() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new Object()); // Not an OIDC user

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenAuthenticationIsNull_ShouldJustContinueFilterChain() throws ServletException, IOException {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }
}
