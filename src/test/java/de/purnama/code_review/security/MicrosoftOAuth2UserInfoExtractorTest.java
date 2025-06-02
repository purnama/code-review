package de.purnama.code_review.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicrosoftOAuth2UserInfoExtractorTest {

    @InjectMocks
    private MicrosoftOAuth2UserInfoExtractor extractor;

    @Test
    void supports_withMicrosoftProvider_returnsTrue() {
        // Act
        boolean result = extractor.supports("microsoft");

        // Assert
        assertTrue(result);
    }

    @Test
    void supports_withNonMicrosoftProvider_returnsFalse() {
        // Act
        boolean result = extractor.supports("google");

        // Assert
        assertFalse(result);
    }

    @Test
    void extractUserInfo_withOidcUser_returnsCorrectUserInfo() {
        // Arrange
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        OidcUserInfo userInfo = mock(OidcUserInfo.class);
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "123456");
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        claims.put("picture", "https://example.com/picture.jpg");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        UserInfo extractedInfo = extractor.extractUserInfo(oidcUser);

        // Assert
        assertEquals("123456", extractedInfo.id());
        assertEquals("test@example.com", extractedInfo.email());
        assertEquals("Test User", extractedInfo.name());
        assertEquals("https://example.com/picture.jpg", extractedInfo.pictureUrl());
        assertEquals("sub", extractedInfo.nameAttributeKey());
    }

    @Test
    void extractUserInfo_withOidcUser_usesGivenNameAndFamilyName() {
        // Arrange
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        OidcUserInfo userInfo = mock(OidcUserInfo.class);
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "123456");
        claims.put("email", "test@example.com");
        claims.put("givenname", "John");
        claims.put("familyname", "Doe");
        claims.put("picture", "https://example.com/picture.jpg");

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        UserInfo extractedInfo = extractor.extractUserInfo(oidcUser);

        // Assert
        assertEquals("123456", extractedInfo.id());
        assertEquals("test@example.com", extractedInfo.email());
        assertEquals("John Doe", extractedInfo.name());
        assertEquals("https://example.com/picture.jpg", extractedInfo.pictureUrl());
        assertEquals("sub", extractedInfo.nameAttributeKey());
    }

    @Test
    void extractUserInfo_withOidcUserMissingAttributes_returnsEmptyStrings() {
        // Arrange
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        OidcUserInfo userInfo = mock(OidcUserInfo.class);
        Map<String, Object> claims = new HashMap<>();

        when(oidcUser.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getClaims()).thenReturn(claims);

        // Act
        UserInfo extractedInfo = extractor.extractUserInfo(oidcUser);

        // Assert
        assertEquals("", extractedInfo.id());
        assertEquals("", extractedInfo.email());
        assertEquals("", extractedInfo.name());
        assertEquals(null, extractedInfo.pictureUrl());
        assertEquals("sub", extractedInfo.nameAttributeKey());
    }

    @Test
    void extractUserInfo_withRegularOAuth2User_returnsCorrectUserInfo() {
        // Arrange
        OAuth2User oAuth2User = mock(OAuth2User.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "123456");
        attributes.put("email", "test@example.com");
        attributes.put("name", "Test User");
        attributes.put("picture", "https://example.com/picture.jpg");

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        // Act
        UserInfo userInfo = extractor.extractUserInfo(oAuth2User);

        // Assert
        assertEquals("123456", userInfo.id());
        assertEquals("test@example.com", userInfo.email());
        assertEquals("Test User", userInfo.name());
        assertEquals("https://example.com/picture.jpg", userInfo.pictureUrl());
        assertEquals("sub", userInfo.nameAttributeKey());
    }

    @Test
    void extractUserInfo_withRegularOAuth2UserMissingAttributes_returnsEmptyStrings() {
        // Arrange
        OAuth2User oAuth2User = mock(OAuth2User.class);
        Map<String, Object> attributes = new HashMap<>();

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        // Act
        UserInfo userInfo = extractor.extractUserInfo(oAuth2User);

        // Assert
        assertEquals("", userInfo.id());
        assertEquals("", userInfo.email());
        assertEquals("", userInfo.name());
        assertEquals(null, userInfo.pictureUrl());
        assertEquals("sub", userInfo.nameAttributeKey());
    }
}
