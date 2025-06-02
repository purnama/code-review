package de.purnama.code_review.security;

import java.util.Map;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import de.purnama.code_review.model.User.AuthProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FacebookOAuth2UserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public boolean supports(String registrationId) {
        return AuthProvider.FACEBOOK.name().equalsIgnoreCase(registrationId);
    }

    @Override
    public UserInfo extractUserInfo(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String id = attributes.getOrDefault("id", "").toString();
        String email = attributes.getOrDefault("email", "").toString();
        String name = attributes.getOrDefault("name", "").toString();
        String pictureUrl = null;

        // Facebook stores picture in a nested structure
        if (attributes.containsKey("picture")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pictureObj = (Map<String, Object>) attributes.get("picture");
            if (pictureObj.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pictureData = (Map<String, Object>) pictureObj.get("data");
                pictureUrl = pictureData.getOrDefault("url", "").toString();
            }
        }

        log.info("Facebook User info - id: {}, email: {}, name: {}, picture: {}", id, email, name, pictureUrl);
        return new UserInfo(id, email, name, pictureUrl, "id");
    }
}
