package de.purnama.code_review.security;

/**
 * Record to standardize user information extracted from various OAuth2 providers
 */
public record UserInfo(
    String id,
    String email,
    String name,
    String pictureUrl,
    String nameAttributeKey
) {}
