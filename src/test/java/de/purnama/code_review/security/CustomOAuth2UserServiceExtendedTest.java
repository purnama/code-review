package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import de.purnama.code_review.model.User;
import de.purnama.code_review.model.User.AuthProvider;
import de.purnama.code_review.repository.UserRepository;

/**
 * Extended test suite for CustomOAuth2UserService to reach 100% code coverage
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceExtendedTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleOAuth2UserInfoExtractor googleExtractor;

    @Mock
    private FacebookOAuth2UserInfoExtractor facebookExtractor;

    @Mock
    private MicrosoftOAuth2UserInfoExtractor microsoftExtractor;

    @Spy
    @InjectMocks
    private CustomOAuth2UserService userService;

    private OAuth2UserRequest oAuth2UserRequest;
    private OAuth2User oAuth2User;
    private ClientRegistration clientRegistration;

    @BeforeEach
    void setUp() {
        // Setup basic ClientRegistration
        clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("clientId")
                .clientSecret("clientSecret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/oauth2/callback")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();

        // Setup OAuth2AccessToken
        Set<String> scopes = new HashSet<>(Arrays.asList("openid", "profile", "email"));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                null,
                null,
                scopes);

        // Setup OAuth2UserRequest
        oAuth2UserRequest = new OAuth2UserRequest(clientRegistration, accessToken);

        // Setup default OAuth2User
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "123456");
        attributes.put("name", "Test User");
        attributes.put("email", "test@example.com");
        attributes.put("picture", "https://example.com/picture.jpg");

        oAuth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub");
    }

    @Test
    void loadUser_ShouldHandleGenericException_AndWrapItInInternalAuthenticationServiceException() {
        // Arrange
        doReturn(oAuth2User).when(userService).callSuperLoadUser(any());
        // Use ReflectionTestUtils to set a field that will cause exception
        ReflectionTestUtils.setField(userService, "userInfoExtractors", null);

        // Act & Assert
        Exception exception = assertThrows(InternalAuthenticationServiceException.class, () -> {
            userService.loadUser(oAuth2UserRequest);
        });

        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    void loadUser_ShouldThrowOAuth2AuthenticationException_OnProviderMismatch() {
        // Arrange - Setup the extractors
        List<OAuth2UserInfoExtractor> extractors = Arrays.asList(googleExtractor, facebookExtractor, microsoftExtractor);
        ReflectionTestUtils.setField(userService, "userInfoExtractors", extractors);

        // Setup a user with FACEBOOK provider
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("test@example.com");
        existingUser.setProvider(AuthProvider.FACEBOOK);
        existingUser.setProviderId("facebook-12345");
        existingUser.setName("Existing User");
        existingUser.setCreatedAt(LocalDateTime.now());

        // Setup extractors with only necessary stubs
        UserInfo userInfo = new UserInfo("123456", "test@example.com", "Test User", "https://example.com/picture.jpg", "sub");
        when(googleExtractor.extractUserInfo(any())).thenReturn(userInfo);
        when(googleExtractor.supports(any())).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

        // Change the registration ID to Google to create the provider mismatch
        ClientRegistration googleRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("clientId")
                .clientSecret("clientSecret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/oauth2/callback")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();

        OAuth2UserRequest googleRequest = new OAuth2UserRequest(googleRegistration,
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", null, null,
                new HashSet<>(Arrays.asList("openid", "profile", "email"))));

        // Setup callSuperLoadUser to return oAuth2User
        doReturn(oAuth2User).when(userService).callSuperLoadUser(any());

        // Act & Assert - This should throw an exception due to provider mismatch
        Exception exception = assertThrows(InternalAuthenticationServiceException.class, () -> {
            userService.loadUser(googleRequest);
        });
        assertTrue(exception.getMessage().contains("You're signed up with FACEBOOK"));
    }
}

