# Authentication Setup Guide

## Overview

This document explains how to set up OAuth2 authentication in the Code Review AI application. The application is designed with the Open/Closed principle, making it easy to add new authentication providers without modifying existing code.

Currently implemented providers:
- Microsoft

Ready to implement providers (structure in place):
- Google
- Facebook

## Prerequisites

1. You need to register your application with the identity provider to obtain OAuth2 credentials.
2. Environment variables or direct configuration in `application.properties` for the client ID and secret.

## Microsoft OAuth2 Setup

1. Register a new application in the [Microsoft Azure Portal](https://portal.azure.com/#blade/Microsoft_AAD_RegisteredApps/ApplicationsListBlade)

2. Create a new registration:
   - Name: Code Review AI (or your preferred name)
   - Supported account types: Accounts in any organizational directory and personal Microsoft accounts
   - Redirect URI: 
     - For local development: http://localhost:8080/login/oauth2/code/microsoft
     - For production: https://your-app-domain.com/login/oauth2/code/microsoft

3. After registration, note the following:
   - Application (client) ID - This is your `MICROSOFT_CLIENT_ID`
   - Create a new client secret - This is your `MICROSOFT_CLIENT_SECRET`

4. Set these values in your environment variables or `application.properties`:
   ```
   MICROSOFT_CLIENT_ID=your-client-id
   MICROSOFT_CLIENT_SECRET=your-client-secret
   ```

5. For local development, you can use the `start-local.sh` script:
   ```bash
   # Create your local script from the example
   cp start-local.sh.example start-local.sh
   
   # Edit the script to include your actual client ID and secret
   nano start-local.sh
   
   # Make it executable and run
   chmod +x start-local.sh
   ./start-local.sh
   ```
   
   This script is automatically ignored by Git to prevent accidentally committing your secrets.

## Adding a New Provider

The application is designed to easily accommodate new identity providers. Here's how to add a new one:

### 1. Update the AuthProvider Enum

Add the new provider to the AuthProvider enum in `User.java`:

```java
public enum AuthProvider {
    MICROSOFT,
    GOOGLE,
    FACEBOOK,
    NEW_PROVIDER  // Add your new provider here
}
```

### 2. Add Provider-Specific User Information Extraction

Extend the `CustomOAuth2UserService` to extract user information from the new provider's OAuth2 response:

```java
private UserInfo extractNewProviderUserInfo(OAuth2User oAuth2User) {
    // Extract and map attributes from the OAuth2User to our UserInfo object
    Map<String, Object> attributes = oAuth2User.getAttributes();
    String id = attributes.getOrDefault("id", "").toString();
    String email = attributes.getOrDefault("email", "").toString();
    String name = attributes.getOrDefault("name", "").toString();
    String pictureUrl = attributes.getOrDefault("picture", "").toString();

    return new UserInfo(id, email, name, pictureUrl, "sub");  // Adjust nameAttributeKey as needed
}
```

Then add the new provider to the `extractUserInfo` method switch statement:

```java
private UserInfo extractUserInfo(AuthProvider provider, OAuth2User oAuth2User) {
    return switch (provider) {
        case MICROSOFT -> extractMicrosoftUserInfo(oAuth2User);
        case GOOGLE -> extractGoogleUserInfo(oAuth2User);
        case FACEBOOK -> extractFacebookUserInfo(oAuth2User);
        case NEW_PROVIDER -> extractNewProviderUserInfo(oAuth2User);
    };
}
```

### 3. Configure the New Provider

Add the configuration in `application.properties`:

```properties
# New Provider OAuth2 Configuration
spring.security.oauth2.client.registration.new-provider.client-id=${NEW_PROVIDER_CLIENT_ID}
spring.security.oauth2.client.registration.new-provider.client-secret=${NEW_PROVIDER_CLIENT_SECRET}
spring.security.oauth2.client.registration.new-provider.scope=appropriate-scopes
spring.security.oauth2.client.registration.new-provider.client-name=New Provider
spring.security.oauth2.client.registration.new-provider.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.registration.new-provider.authorization-grant-type=authorization_code
spring.security.oauth2.client.provider.new-provider.authorization-uri=https://new-provider.com/oauth2/authorize
spring.security.oauth2.client.provider.new-provider.token-uri=https://new-provider.com/oauth2/token
spring.security.oauth2.client.provider.new-provider.user-info-uri=https://new-provider.com/oauth2/userinfo
spring.security.oauth2.client.provider.new-provider.user-name-attribute=id
```

### 4. Update the Login Page

Add the new provider's login button to the login page in `login.html`:

```html
<div th:if="${newProviderActive}" class="mt-3">
    <a href="/oauth2/authorization/new-provider" class="btn oauth-button new-provider-button">
        <i class="bi bi-new-provider-icon provider-icon"></i> Continue with New Provider
    </a>
</div>
```

And update the controller to detect the new provider:

```java
model.addAttribute("newProviderActive", clientRegistrationRepository.findByRegistrationId("new-provider") != null);
```

## Testing the Authentication Flow

1. Start the application with the appropriate environment variables set:
   ```bash
   # Using the start-local.sh script (recommended for development)
   ./start-local.sh
   
   # Or manually setting environment variables
   export MICROSOFT_CLIENT_ID="your-id"
   export MICROSOFT_CLIENT_SECRET="your-secret"
   ./mvnw spring-boot:run
   ```
   
2. Visit the login page at `/login`.
3. Click on a provider button to initiate the OAuth2 flow.
4. After successful authentication, you should be redirected back to the application.

## Security Considerations

- Always use HTTPS in production
- Store client secrets securely, preferably using environment variables or a secure vault
- Implement proper CSRF protection
- Consider adding rate limiting for authentication attempts
