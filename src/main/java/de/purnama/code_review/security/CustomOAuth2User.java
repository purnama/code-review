package de.purnama.code_review.security;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Custom OAuth2User implementation that allows customizing the name attribute
 */
public class CustomOAuth2User extends DefaultOAuth2User {

    private final String email;
    private final String displayName;

    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities,
                           Map<String, Object> attributes,
                           String nameAttributeKey,
                           String email,
                           String displayName) {
        super(authorities, attributes, nameAttributeKey);
        this.email = email;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        // Override to return the user's display name instead of the nameAttributeKey's value
        return displayName != null ? displayName : email;
    }

    public String getEmail() {
        return email;
    }
}
