package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2UserInfoExtractorTest {

    @InjectMocks
    private GoogleOAuth2UserInfoExtractor extractor;

    @Test
    void supports_withGoogleProvider_returnsTrue() {
        // Act
        boolean result = extractor.supports("google");

        // Assert
        assertTrue(result);
    }

    @Test
    void supports_withNonGoogleProvider_returnsFalse() {
        // Act
        boolean result = extractor.supports("facebook");

        // Assert
        assertFalse(result);
    }

    @Test
    void extractUserInfo_withCompleteAttributes_returnsCorrectUserInfo() {
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
    void extractUserInfo_withMissingAttributes_returnsEmptyStrings() {
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
        assertEquals("", userInfo.pictureUrl());
        assertEquals("sub", userInfo.nameAttributeKey());
    }
}
