package de.purnama.code_review.security;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.purnama.code_review.model.User;
import de.purnama.code_review.model.User.AuthProvider;
import de.purnama.code_review.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom OAuth2 user service that processes user information from OAuth2 providers
 * and integrates it with our user database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            log.error("Exception while processing OAuth2 user", ex);
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        // Extract provider
        AuthProvider provider = getProvider(userRequest);
        
        // Extract OAuth2 attributes based on provider
        UserInfo userInfo = extractUserInfo(provider, oAuth2User);
        
        // Check if user already exists
        Optional<User> userOptional = userRepository.findByEmail(userInfo.email());
        User user;

        if (userOptional.isPresent()) {
            // If user exists, update their information
            user = userOptional.get();
            
            // Check if the user is using the same provider
            if (!user.getProvider().equals(provider)) {
                throw new OAuth2AuthenticationException(
                    "You're signed up with " + user.getProvider() + ". Please use that to login.");
            }

            updateExistingUser(user, userInfo);
        } else {
            // Otherwise, register a new user
            user = registerNewUser(userRequest, userInfo, provider);
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);  // Explicitly save and reassign to ensure we have the updated entity
        log.info("User saved/updated in database: id={}, email={}, provider={}, name={}",
                user.getId(), user.getEmail(), user.getProvider(), user.getName());

        // Create authorities from user roles
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));

        // Return the original OAuth2User but with our database ID attached
        // This preserves all the original information but makes our user ID available
        if (oAuth2User instanceof OidcUser) {
            // Handle OIDC users (Microsoft, Google)
            return oAuth2User;
        } else {
            // Handle regular OAuth2 users
            Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
            attributes.put("id", user.getId());

            return new DefaultOAuth2User(
                authorities,
                attributes,
                userInfo.nameAttributeKey()
            );
        }
    }

    private AuthProvider getProvider(OAuth2UserRequest userRequest) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        return AuthProvider.valueOf(registrationId);
    }

    private UserInfo extractUserInfo(AuthProvider provider, OAuth2User oAuth2User) {
        return switch (provider) {
            case MICROSOFT -> extractMicrosoftUserInfo(oAuth2User);
            case GOOGLE -> extractGoogleUserInfo(oAuth2User);
            case FACEBOOK -> extractFacebookUserInfo(oAuth2User);
        };
    }

    private UserInfo extractMicrosoftUserInfo(OAuth2User oAuth2User) {
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

    private UserInfo extractGoogleUserInfo(OAuth2User oAuth2User) {
        // Google-specific attribute mapping (for future implementation)
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String id = attributes.getOrDefault("sub", "").toString();
        String email = attributes.getOrDefault("email", "").toString();
        String name = attributes.getOrDefault("name", "").toString();
        String pictureUrl = attributes.getOrDefault("picture", "").toString();

        return new UserInfo(id, email, name, pictureUrl, "sub");
    }

    private UserInfo extractFacebookUserInfo(OAuth2User oAuth2User) {
        // Facebook-specific attribute mapping (for future implementation)
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

        return new UserInfo(id, email, name, pictureUrl, "id");
    }

    private User registerNewUser(OAuth2UserRequest userRequest, UserInfo userInfo, AuthProvider provider) {
        User user = new User();
        user.setProvider(provider);
        user.setProviderId(userInfo.id());
        user.setEmail(userInfo.email());
        user.setName(userInfo.name());
        user.setPictureUrl(userInfo.pictureUrl());
        
        // Assign default roles
        Set<String> roles = new HashSet<>();
        roles.add("USER");
        user.setRoles(roles);

        return userRepository.save(user);
    }

    private void updateExistingUser(User user, UserInfo userInfo) {
        // Only update if values are provided
        if (StringUtils.hasText(userInfo.name())) {
            user.setName(userInfo.name());
        }
        
        if (StringUtils.hasText(userInfo.pictureUrl())) {
            user.setPictureUrl(userInfo.pictureUrl());
        }
    }

    /**
     * Internal class to hold user information extracted from OAuth2 providers
     */
    private record UserInfo(
        String id,
        String email,
        String name,
        String pictureUrl,
        String nameAttributeKey
    ) {}
}
