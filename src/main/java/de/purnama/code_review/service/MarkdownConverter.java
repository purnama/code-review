package de.purnama.code_review.service;

import java.util.Arrays;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * Service for converting Markdown to HTML
 *
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Component
public class MarkdownConverter {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownConverter() {
        // Setup extensions for GitHub Flavored Markdown
        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                AutolinkExtension.create(),
                HeadingAnchorExtension.create()
        );

        // Create parser with extensions
        parser = Parser.builder()
                .extensions(extensions)
                .build();

        // Create renderer with extensions
        renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
    }

    /**
     * Convert markdown text to HTML
     *
     * @param markdown The markdown text to convert
     * @return HTML string
     */
    public String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        // Parse markdown to AST
        Node document = parser.parse(markdown);
        
        // Render AST to HTML
        return renderer.render(document);
    }
}
