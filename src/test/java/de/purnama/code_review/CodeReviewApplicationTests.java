package de.purnama.code_review;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * CodeReviewApplicationTests
 * 
 * Main application test class that connects to a real database
 * for integration testing.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@SpringBootTest
@ActiveProfiles("test")
class CodeReviewApplicationTests {

	@Test
	void contextLoads() {
		// This test will use a real database
	}
}
