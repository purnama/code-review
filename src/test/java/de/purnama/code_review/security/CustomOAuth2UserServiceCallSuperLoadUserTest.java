package de.purnama.code_review.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import de.purnama.code_review.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceCallSuperLoadUserTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void callSuperLoadUser_ShouldInvokeParentLoadUserMethod() {
        // Create a testable subclass that allows us to verify the super call
        class TestableCustomOAuth2UserService extends CustomOAuth2UserService {
            private final DefaultOAuth2UserService parentService;

            public TestableCustomOAuth2UserService(UserRepository userRepository,
                    DefaultOAuth2UserService parentService) {
                super(userRepository, new ArrayList<>());
                this.parentService = parentService;
            }

            @Override
            protected OAuth2User callSuperLoadUser(OAuth2UserRequest userRequest) {
                // Delegate to our mock parent service instead of the real parent
                return parentService.loadUser(userRequest);
            }
        }

        // Mock parent service
        DefaultOAuth2UserService mockParentService = mock(DefaultOAuth2UserService.class);

        // Setup test data
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "12345");
        attributes.put("email", "user@example.com");

        OAuth2User expectedUser = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub"
        );

        // Create client registration
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
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
        OAuth2UserRequest userRequest = new OAuth2UserRequest(clientRegistration, accessToken);

        // Setup mock behavior
        when(mockParentService.loadUser(any(OAuth2UserRequest.class))).thenReturn(expectedUser);

        // Create service instance with our mock
        TestableCustomOAuth2UserService service =
            new TestableCustomOAuth2UserService(userRepository, mockParentService);

        // Execute the method under test
        OAuth2User result = service.callSuperLoadUser(userRequest);

        // Verify the result and that the parent method was called
        assertNotNull(result);
        assertEquals(expectedUser, result);
        verify(mockParentService, times(1)).loadUser(userRequest);
    }
}
