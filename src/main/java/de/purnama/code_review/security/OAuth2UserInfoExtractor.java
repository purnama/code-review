package de.purnama.code_review.security;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Interface for extracting user information from OAuth2 providers.
 * Each provider (Google, Microsoft, Facebook, etc.) should implement this interface
 * to handle their specific data structures.
 */
public interface OAuth2UserInfoExtractor {

    /**
     * Determines if this extractor supports the given provider
     *
     * @param provider the authentication provider
     * @return true if this extractor supports the provider
     */
    boolean supports(String registrationId);

    /**
     * Extracts user information from the OAuth2User object
     *
     * @param oAuth2User the OAuth2User object containing provider-specific user data
     * @return standardized UserInfo record with extracted data
     */
    UserInfo extractUserInfo(OAuth2User oAuth2User);
}
