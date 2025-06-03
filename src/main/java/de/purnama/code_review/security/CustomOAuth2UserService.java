package de.purnama.code_review.security;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
    private final List<OAuth2UserInfoExtractor> userInfoExtractors;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = callSuperLoadUser(userRequest);
        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            log.error("Exception while processing OAuth2 user", ex);
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        }
    }

    // Extracted for testability
    protected OAuth2User callSuperLoadUser(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        // Extract provider
        AuthProvider provider = getProvider(userRequest);
        
        // Extract OAuth2 attributes based on provider
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        UserInfo userInfo = extractUserInfo(registrationId, oAuth2User);

        // Check if user already exists
        Optional<User> userOptional = userRepository.findByEmail(userInfo.email());
        User user;

        if (userOptional.isPresent()) {
            // If user exists, update their information
            user = userOptional.get();
            
            // Check if the user is using the same provider
            if (!user.getProvider().equals(provider)) {
                throw new OAuth2AuthenticationException(
                    new OAuth2Error("provider_mismatch",
                    "You're signed up with " + user.getProvider() + ". Please use that to login.",
                    null));
            }

            user = updateExistingUser(user, userInfo);
        } else {
            // Otherwise, register a new user
            user = registerNewUser(userRequest, userInfo, provider);
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);  // Save only once and reassign
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

    private UserInfo extractUserInfo(String registrationId, OAuth2User oAuth2User) {
        if (oAuth2User == null || registrationId == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_data", "User data or registration ID is missing", null));
        }

        return userInfoExtractors.stream()
            .filter(extractor -> extractor.supports(registrationId))
            .findFirst()
            .orElseThrow(() -> new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider",
                "Unsupported OAuth2 provider: " + registrationId, null)))
            .extractUserInfo(oAuth2User);
    }

    private User registerNewUser(OAuth2UserRequest userRequest, UserInfo userInfo, AuthProvider provider) {
        if (userInfo == null || userInfo.email() == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info", "Email is required to register a new user", null));
        }

        User user = new User();
        user.setProvider(provider);
        user.setProviderId(userInfo.id() != null ? userInfo.id() : "");
        user.setEmail(userInfo.email());
        user.setName(userInfo.name() != null ? userInfo.name() : "");
        user.setPictureUrl(userInfo.pictureUrl());  // Can be null

        // Assign default roles
        Set<String> roles = new HashSet<>();
        roles.add("USER");
        user.setRoles(roles);

        return user;
    }

    private User updateExistingUser(User user, UserInfo userInfo) {
        if (user == null || userInfo == null) {
            return user;  // Return the unchanged user
        }

        // Only update if values are provided and not null
        if (StringUtils.hasText(userInfo.name())) {
            user.setName(userInfo.name());
        }
        
        if (StringUtils.hasText(userInfo.pictureUrl())) {
            user.setPictureUrl(userInfo.pictureUrl());
        }

        return user;
    }
}
