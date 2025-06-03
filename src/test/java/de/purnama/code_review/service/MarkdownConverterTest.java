package de.purnama.code_review.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarkdownConverterTest {

    private MarkdownConverter markdownConverter;

    @BeforeEach
    void setUp() {
        markdownConverter = new MarkdownConverter();
    }

    @Test
    void convertMarkdownToHtml_WithBasicMarkdown_ShouldReturnCorrectHtml() {
        // Arrange
        String markdown = "# Heading\n\nThis is a paragraph with **bold** and *italic* text.";

        // Act
        String html = markdownConverter.convertMarkdownToHtml(markdown);

        // Assert
        assertTrue(html.contains("<h1 id=\"heading\">Heading</h1>"));
        assertTrue(html.contains("<p>This is a paragraph with <strong>bold</strong> and <em>italic</em> text.</p>"));
    }

    @Test
    void convertMarkdownToHtml_WithTable_ShouldRenderTable() {
        // Arrange
        String markdown = "| Header 1 | Header 2 |\n| -------- | -------- |\n| Cell 1   | Cell 2   |";

        // Act
        String html = markdownConverter.convertMarkdownToHtml(markdown);

        // Assert
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Header 1</th>"));
        assertTrue(html.contains("<td>Cell 1</td>"));
    }

    @Test
    void convertMarkdownToHtml_WithAutolink_ShouldConvertLinks() {
        // Arrange
        String markdown = "Visit https://example.com for more information.";

        // Act
        String html = markdownConverter.convertMarkdownToHtml(markdown);

        // Assert
        assertTrue(html.contains("<a href=\"https://example.com\">https://example.com</a>"));
    }

    @Test
    void convertMarkdownToHtml_WithNullInput_ShouldReturnEmptyString() {
        // Arrange
        String markdown = null;

        // Act
        String html = markdownConverter.convertMarkdownToHtml(markdown);

        // Assert
        assertEquals("", html);
    }

    @Test
    void convertMarkdownToHtml_WithEmptyInput_ShouldReturnEmptyString() {
        // Arrange
        String markdown = "";

        // Act
        String html = markdownConverter.convertMarkdownToHtml(markdown);

        // Assert
        assertEquals("", html);
    }
}
