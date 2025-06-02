package de.purnama.code_review.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.RedirectStrategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    // Create a testable subclass that overrides the private method completely
    static class TestableOAuth2AuthenticationSuccessHandler extends OAuth2AuthenticationSuccessHandler {
        // No override needed; remove logAuthenticationDetails
    }

    private TestableOAuth2AuthenticationSuccessHandler successHandler;

    @Mock
    private RedirectStrategy redirectStrategy;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Authentication authentication;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        successHandler = new TestableOAuth2AuthenticationSuccessHandler();
        successHandler.setRedirectStrategy(redirectStrategy);

        // Create request, response, and session
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);

        // Setup OAuth2 authentication with a regular OAuth2User instead of OidcUser to avoid casting issues
        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_USER");

        // Create attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "user-123");
        attributes.put("name", "Test User");
        attributes.put("email", "user@example.com");
        attributes.put("picture", "https://example.com/picture.jpg");

        // Create OIDC ID Token and UserInfo
        OidcIdToken idToken = new OidcIdToken("tokenValue", null, null, attributes);
        OidcUserInfo userInfo = new OidcUserInfo(attributes);

        // Use DefaultOidcUser for OIDC authentication
        DefaultOidcUser oidcUser = new DefaultOidcUser(authorities, idToken, userInfo, "sub");

        authentication = new OAuth2AuthenticationToken(oidcUser, authorities, "google");
    }
}
