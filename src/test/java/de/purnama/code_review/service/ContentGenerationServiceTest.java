package de.purnama.code_review.service;

import de.purnama.code_review.exception.ConfluenceException;
import de.purnama.code_review.model.ConfluenceUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentGenerationServiceTest {
    @Mock ChatModel chatModel;
    @Mock ConfluenceService confluenceService;
    @InjectMocks ContentGenerationService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test void generateTitleAndDescription_success() {}
    @Test void generateTitleAndDescription_confluenceException() {}
    @Test void generateTitleAndDescription_nullOrEmptyContent() {}
    @Test void generateTitleAndDescription_longContent() {}
    @Test void generateTitleAndDescription_openAIException() {}
}

