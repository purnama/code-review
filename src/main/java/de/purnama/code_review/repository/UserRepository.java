package de.purnama.code_review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.purnama.code_review.model.User;

/**
 * Repository for User entities
 */
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find a user by email
     * 
     * @param email The email to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find a user by provider and providerId
     * 
     * @param provider The authentication provider
     * @param providerId The ID from the provider
     * @return Optional containing the user if found
     */
    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);
    
    /**
     * Check if a user with the given email exists
     * 
     * @param email The email to check
     * @return true if a user with this email exists
     */
    boolean existsByEmail(String email);
}
