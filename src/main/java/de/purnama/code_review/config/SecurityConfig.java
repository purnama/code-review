package de.purnama.code_review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import de.purnama.code_review.security.CustomOAuth2UserService;
import de.purnama.code_review.security.OAuth2AuthenticationSuccessHandler;
import de.purnama.code_review.security.OidcUserInfoFilter;
import lombok.RequiredArgsConstructor;

/**
 * Configuration for Spring Security
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OidcUserInfoFilter oidcUserInfoFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for now
            .authorizeHttpRequests(authorize -> authorize
                // Public resources
                .requestMatchers("/", "/login", "/error", "/webjars/**", "/css/**", "/js/**", "/images/**").permitAll()
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/access-denied")
            )
            // Add our custom filter to extract and store OIDC user info
            .addFilterAfter(oidcUserInfoFilter, BasicAuthenticationFilter.class);

        return http.build();
    }
}
