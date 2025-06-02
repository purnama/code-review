package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CustomOAuth2UserTest {

    @Test
    void getName_WithDisplayName_ShouldReturnDisplayName() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");

        CustomOAuth2User user = new CustomOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub",
            "user@example.com",
            "Display Name"
        );

        // Act
        String name = user.getName();

        // Assert
        assertEquals("Display Name", name);
    }

    @Test
    void getName_WithoutDisplayName_ShouldReturnEmail() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");

        CustomOAuth2User user = new CustomOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub",
            "user@example.com",
            null
        );

        // Act
        String name = user.getName();

        // Assert
        assertEquals("user@example.com", name);
    }

    @Test
    void getEmail_ShouldReturnEmail() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");

        CustomOAuth2User user = new CustomOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub",
            "user@example.com",
            "Display Name"
        );

        // Act
        String email = user.getEmail();

        // Assert
        assertEquals("user@example.com", email);
    }

    @Test
    void getAttributes_ShouldReturnAttributes() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");
        attributes.put("name", "Test User");

        CustomOAuth2User user = new CustomOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub",
            "user@example.com",
            "Display Name"
        );

        // Act
        Map<String, Object> returnedAttributes = user.getAttributes();

        // Assert
        assertEquals(attributes, returnedAttributes);
        assertEquals("12345", returnedAttributes.get("sub"));
        assertEquals("user@example.com", returnedAttributes.get("email"));
        assertEquals("Test User", returnedAttributes.get("name"));
    }
}
