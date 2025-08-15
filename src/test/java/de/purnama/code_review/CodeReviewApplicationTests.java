package de.purnama.code_review;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CodeReviewApplicationTests
 * 
 * Main application test class that connects to a real database
 * for integration testing.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@SpringBootTest
@ContextConfiguration(initializers = CodeReviewApplicationTests.Initializer.class)
@Testcontainers
@ActiveProfiles("test")
class CodeReviewApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("code_review")
            .withUsername("postgres")
            .withPassword("password")
            .withCommand("postgres", "-c", "max_connections=300", "-c", "shared_buffers=1GB");

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword()
            ).applyTo(context.getEnvironment());
        }
    }

    @Test
    void contextLoads() {
        // This test will use a real database
    }
}
