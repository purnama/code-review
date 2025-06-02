package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
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

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleOAuth2UserInfoExtractor googleExtractor;

    @Mock
    private FacebookOAuth2UserInfoExtractor facebookExtractor;

    @Mock
    private MicrosoftOAuth2UserInfoExtractor microsoftExtractor;

    // Create a partial mock of the service
    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    private List<OAuth2UserInfoExtractor> extractorsList;
    private ClientRegistration clientRegistration;
    private OAuth2UserRequest oAuth2UserRequest;
    private OAuth2User oAuth2User;
    private Map<String, Object> attributes;
    private User existingUser;

    @BeforeEach
    void setUp() {
        // Initialize list of extractors
        extractorsList = new ArrayList<>();
        extractorsList.add(googleExtractor);
        extractorsList.add(facebookExtractor);
        extractorsList.add(microsoftExtractor);

        // Set the list in the service using reflection
        ReflectionTestUtils.setField(
            customOAuth2UserService, "userInfoExtractors", extractorsList
        );

        // Create client registration for Google
        clientRegistration = ClientRegistration.withRegistrationId("google")
            .clientId("client-id")
            .clientSecret("client-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost/login/oauth2/code/google")
            .scope("profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://www.googleapis.com/oauth2/v4/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("sub")
            .clientName("Google")
            .build();

        // Create OAuth2UserRequest
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "token-value",
            null,
            null
        );
        oAuth2UserRequest = new OAuth2UserRequest(clientRegistration, accessToken);

        // Create attributes
        attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");
        attributes.put("name", "Test User");
        attributes.put("picture", "https://example.com/picture.jpg");

        // Create OAuth2User
        oAuth2User = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub"
        );

        // Create existing user
        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("user@example.com");
        existingUser.setName("Existing User");
        existingUser.setProvider(AuthProvider.GOOGLE);
        existingUser.setProviderId("12345");
        existingUser.setPictureUrl("https://example.com/old-picture.jpg");
        Set<String> roles = new HashSet<>();
        roles.add("USER");
        existingUser.setRoles(roles);
    }

    @Test
    void loadUser_NewUser_ShouldRegisterNewUser() {
        CustomOAuth2UserService serviceSpy = spy(new CustomOAuth2UserService(userRepository, extractorsList));
        doReturn(oAuth2User).when(serviceSpy).callSuperLoadUser(any(OAuth2UserRequest.class));
        // Setup mocks
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(googleExtractor.supports("google")).thenReturn(true);
        when(googleExtractor.extractUserInfo(oAuth2User)).thenReturn(new UserInfo(
            "12345", "user@example.com", "Test User", "https://example.com/picture.jpg", "sub"
        ));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(1L); // Simulate DB setting ID
            return savedUser;
        });
        // Call the real method with our setup
        OAuth2User result = serviceSpy.loadUser(oAuth2UserRequest);
        // Verify
        assertNotNull(result);
        verify(userRepository, times(1)).save(argThat(user ->
            user.getEmail().equals("user@example.com") &&
            user.getName().equals("Test User") &&
            user.getProvider().equals(AuthProvider.GOOGLE)
        ));
    }

    @Test
    void loadUser_ExistingUser_ShouldUpdateExistingUser() {
        CustomOAuth2UserService serviceSpy = spy(new CustomOAuth2UserService(userRepository, extractorsList));
        doReturn(oAuth2User).when(serviceSpy).callSuperLoadUser(any(OAuth2UserRequest.class));
        // Setup mocks
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(googleExtractor.supports("google")).thenReturn(true);
        when(googleExtractor.extractUserInfo(oAuth2User)).thenReturn(new UserInfo(
            "12345", "user@example.com", "Updated User", "https://example.com/new-picture.jpg", "sub"
        ));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Act
        serviceSpy.loadUser(oAuth2UserRequest);
        // Verify
        verify(userRepository, times(1)).save(argThat(user ->
            user.getName().equals("Updated User") &&
            user.getPictureUrl().equals("https://example.com/new-picture.jpg")
        ));
    }

    @Test
    void loadUser_DifferentProvider_ShouldThrowException() {
        CustomOAuth2UserService serviceSpy = spy(new CustomOAuth2UserService(userRepository, extractorsList));
        doReturn(oAuth2User).when(serviceSpy).callSuperLoadUser(any(OAuth2UserRequest.class));
        // Setup mocks
        existingUser.setProvider(AuthProvider.FACEBOOK);
        lenient().when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        lenient().when(googleExtractor.supports("google")).thenReturn(true);
        lenient().when(googleExtractor.extractUserInfo(oAuth2User)).thenReturn(new UserInfo(
            "12345", "user@example.com", "Test User", "https://example.com/picture.jpg", "sub"
        ));
        // Act & Assert
        InternalAuthenticationServiceException ex = assertThrows(InternalAuthenticationServiceException.class, () -> {
            serviceSpy.loadUser(oAuth2UserRequest);
        });
        assertTrue(ex.getCause() instanceof OAuth2AuthenticationException);
    }
}

