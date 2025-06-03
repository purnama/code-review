package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Test class focused on improving coverage for logAuthenticationDetails method in OAuth2AuthenticationSuccessHandler
 */
@ExtendWith(MockitoExtension.class)
public class OAuth2AuthenticationSuccessHandlerLogDetailsTest {

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler handler;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2AuthenticationToken oAuth2AuthenticationToken;

    private Method logAuthenticationDetailsMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Get access to the private logAuthenticationDetails method
        logAuthenticationDetailsMethod = OAuth2AuthenticationSuccessHandler.class.getDeclaredMethod(
                "logAuthenticationDetails", Authentication.class);
        logAuthenticationDetailsMethod.setAccessible(true);
    }

    @Test
    void logAuthenticationDetails_WithDefaultOidcUser_ShouldLogAllClaims() throws Exception {
        // Create OidcIdToken
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("email", "john.doe@example.com");
        claims.put("picture", "http://example.com/pic.jpg");

        OidcIdToken idToken = new OidcIdToken("tokenValue", Instant.now(),
                                Instant.now().plusSeconds(3600), claims);

        // Create OidcUserInfo
        OidcUserInfo userInfo = new OidcUserInfo(claims);

        // Create DefaultOidcUser
        Collection<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        DefaultOidcUser oidcUser = new DefaultOidcUser(authorities, idToken, userInfo);

        // Mock Authentication to return DefaultOidcUser
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getName()).thenReturn("john.doe@example.com");
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Invoke the private method
        logAuthenticationDetailsMethod.invoke(handler, authentication);

        // No assertions needed as we're just verifying it doesn't throw exceptions
        // The method only logs information
    }

    @Test
    void logAuthenticationDetails_WithDefaultOAuth2User_ShouldLogAllAttributes() throws Exception {
        // Create OAuth2AuthenticationToken with DefaultOAuth2User
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "123");
        attributes.put("name", "Test User");
        attributes.put("email", "test@example.com");
        attributes.put("pictureUrl", "https://example.com/pic.jpg");

        Collection<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(authorities, attributes, "name");

        // Mock OAuth2AuthenticationToken
        when(oAuth2AuthenticationToken.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2AuthenticationToken.getName()).thenReturn("Test User");
        when(oAuth2AuthenticationToken.getAuthorities()).thenReturn((Collection) authorities);

        // Invoke the private method
        logAuthenticationDetailsMethod.invoke(handler, oAuth2AuthenticationToken);

        // No assertions needed as we're just verifying it doesn't throw exceptions
        // The method only logs information
    }

    @Test
    void logAuthenticationDetails_WithDefaultOAuth2User_WithNullPictureUrl_ShouldHandleGracefully() throws Exception {
        // Create OAuth2AuthenticationToken with DefaultOAuth2User having null pictureUrl
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "123");
        attributes.put("name", "Test User");
        attributes.put("email", "test@example.com");
        attributes.put("pictureUrl", null); // Explicitly null pictureUrl

        Collection<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(authorities, attributes, "name");

        // Mock OAuth2AuthenticationToken
        when(oAuth2AuthenticationToken.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2AuthenticationToken.getName()).thenReturn("Test User");
        when(oAuth2AuthenticationToken.getAuthorities()).thenReturn((Collection) authorities);

        // Invoke the private method
        logAuthenticationDetailsMethod.invoke(handler, oAuth2AuthenticationToken);

        // No assertions needed as we're just verifying it doesn't throw exceptions
        // The method only logs information
    }
}
