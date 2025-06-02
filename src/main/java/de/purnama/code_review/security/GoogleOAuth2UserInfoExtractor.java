package de.purnama.code_review.security;

import java.util.Map;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import de.purnama.code_review.model.User.AuthProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GoogleOAuth2UserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public boolean supports(String registrationId) {
        return AuthProvider.GOOGLE.name().equalsIgnoreCase(registrationId);
    }

    @Override
    public UserInfo extractUserInfo(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String id = attributes.getOrDefault("sub", "").toString();
        String email = attributes.getOrDefault("email", "").toString();
        String name = attributes.getOrDefault("name", "").toString();
        String pictureUrl = attributes.getOrDefault("picture", "").toString();

        log.info("Google User info - id: {}, email: {}, name: {}, picture: {}", id, email, name, pictureUrl);
        return new UserInfo(id, email, name, pictureUrl, "sub");
    }
}
