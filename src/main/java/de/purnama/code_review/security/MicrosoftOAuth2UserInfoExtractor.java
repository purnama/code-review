package de.purnama.code_review.security;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.purnama.code_review.model.User.AuthProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MicrosoftOAuth2UserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public boolean supports(String registrationId) {
        return AuthProvider.MICROSOFT.name().equalsIgnoreCase(registrationId);
    }

    @Override
    public UserInfo extractUserInfo(OAuth2User oAuth2User) {
        // Microsoft-specific attribute mapping - handle both OIDC and OAuth2
        if (oAuth2User instanceof DefaultOidcUser) {
            DefaultOidcUser oidcUser = (DefaultOidcUser) oAuth2User;
            Map<String, Object> claims = oidcUser.getUserInfo().getClaims();

            String id = claims.getOrDefault("sub", "").toString();
            String email = claims.getOrDefault("email", "").toString();

            // Combine given name and family name if available
            String givenName = (String) claims.getOrDefault("givenname", "");
            String familyName = (String) claims.getOrDefault("familyname", "");
            String name = StringUtils.hasText(givenName) || StringUtils.hasText(familyName) ?
                    (givenName + " " + familyName).trim() :
                    claims.getOrDefault("name", "").toString();

            String pictureUrl = (String) claims.getOrDefault("picture", null);

            log.info("OIDC User info - id: {}, email: {}, name: {}, picture: {}", id, email, name, pictureUrl);
            return new UserInfo(id, email, name, pictureUrl, "sub");
        } else {
            // Fall back to regular OAuth2 attributes
            Map<String, Object> attributes = oAuth2User.getAttributes();
            String id = attributes.getOrDefault("sub", "").toString();
            String email = attributes.getOrDefault("email", "").toString();
            String name = attributes.getOrDefault("name", "").toString();
            String pictureUrl = null;

            if (attributes.containsKey("picture")) {
                pictureUrl = attributes.get("picture").toString();
            }

            log.info("OAuth2 User info - id: {}, email: {}, name: {}, picture: {}", id, email, name, pictureUrl);
            return new UserInfo(id, email, name, pictureUrl, "sub");
        }
    }
}
