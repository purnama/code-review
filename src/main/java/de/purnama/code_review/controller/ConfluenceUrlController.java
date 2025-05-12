package de.purnama.code_review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.service.ConfluenceUrlService;
import de.purnama.code_review.service.ContentBlockService;
import de.purnama.code_review.service.ContentGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * ConfluenceUrlController
 * Controller for managing Confluence URLs and related operations
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/confluence-urls")
@RequiredArgsConstructor
public class ConfluenceUrlController {

    private final ConfluenceUrlService confluenceUrlService;
    private final ContentBlockService contentBlockService;
    private final ContentGenerationService contentGenerationService;

    @GetMapping
    public String listUrls(Model model) {
        model.addAttribute("urls", confluenceUrlService.findAll());
        return "confluence-urls/list";
    }

    @GetMapping("/new")
    public String showNewForm(Model model) {
        model.addAttribute("confluenceUrl", new ConfluenceUrl());
        return "confluence-urls/form";
    }

    @PostMapping
    public String saveUrl(@Valid @ModelAttribute("confluenceUrl") ConfluenceUrl confluenceUrl,
                          BindingResult result,
                          RedirectAttributes redirectAttributes) {

        // Check if URL already exists
        if (confluenceUrl.getId() == null && confluenceUrlService.findByUrl(confluenceUrl.getUrl()).isPresent()) {
            result.rejectValue("url", "duplicate", "This URL already exists");
            return "confluence-urls/form";
        }

        if (result.hasErrors()) {
            return "confluence-urls/form";
        }

        // For new Confluence URLs, auto-generate title and description if they're empty
        if (confluenceUrl.getId() == null && (confluenceUrl.getTitle() == null || confluenceUrl.getTitle().isBlank())) {
            try {
                // Use OpenAI to generate title and description based on the actual content
                // This now directly modifies the confluenceUrl object instead of returning a map
                contentGenerationService.generateTitleAndDescription(confluenceUrl);
                log.info("Auto-generated title and description for URL: {}", confluenceUrl.getUrl());
            } catch (Exception e) {
                log.error("Error auto-generating metadata for URL: {}", confluenceUrl.getUrl(), e);
                redirectAttributes.addFlashAttribute("warning",
                        "Could not automatically generate title and description. Basic metadata has been saved.");
            }
        }

        // Save the URL (with auto-generated metadata if it was new)
        ConfluenceUrl savedUrl = confluenceUrlService.save(confluenceUrl);
        redirectAttributes.addFlashAttribute("message", "Confluence URL saved successfully");

        return "redirect:/confluence-urls";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        var urlOpt = confluenceUrlService.findById(id);

        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        model.addAttribute("confluenceUrl", urlOpt.get());
        return "confluence-urls/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteUrl(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        var urlOpt = confluenceUrlService.findById(id);

        if (urlOpt.isPresent()) {
            // First delete associated content blocks
            contentBlockService.deleteByConfluenceUrl(urlOpt.get());
            // Then delete the URL
            confluenceUrlService.delete(id);
            redirectAttributes.addFlashAttribute("message", "Confluence URL deleted successfully");
        } else {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
        }

        return "redirect:/confluence-urls";
    }

    @GetMapping("/toggle/{id}")
    public String toggleActive(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        boolean success = confluenceUrlService.toggleActive(id);

        if (success) {
            redirectAttributes.addFlashAttribute("message", "Confluence URL status updated");
        } else {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
        }

        return "redirect:/confluence-urls";
    }

    @GetMapping("/refresh/{id}")
    public String refreshContent(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        var urlOpt = confluenceUrlService.findById(id);

        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        try {
            // Trigger content refresh
            confluenceUrlService.refreshContent(id);
            redirectAttributes.addFlashAttribute("message", "Content refreshed successfully from Confluence");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to refresh content: " + e.getMessage());
        }

        return "redirect:/content-blocks/" + id;
    }
}