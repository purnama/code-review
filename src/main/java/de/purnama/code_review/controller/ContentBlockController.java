package de.purnama.code_review.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import de.purnama.code_review.model.ConfluenceUrl;
import de.purnama.code_review.model.ContentBlock;
import de.purnama.code_review.service.ConfluenceUrlService;
import de.purnama.code_review.service.ContentBlockService;
import lombok.RequiredArgsConstructor;

/**
 * 
 * Controller for managing content blocks used in code reviews
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Controller
@RequestMapping("/content-blocks")
@RequiredArgsConstructor
public class ContentBlockController {

    private final ContentBlockService contentBlockService;
    private final ConfluenceUrlService confluenceUrlService;

    @GetMapping("/{confluenceUrlId}")
    public String listBlocks(@PathVariable("confluenceUrlId") Long confluenceUrlId, Model model,
                             RedirectAttributes redirectAttributes) {

        Optional<ConfluenceUrl> urlOpt = confluenceUrlService.findByIdWithContentBlocks(confluenceUrlId);
        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        // Get content blocks directly from the ConfluenceUrl object that was eagerly loaded
        ConfluenceUrl confluenceUrl = urlOpt.get();
        model.addAttribute("confluenceUrl", confluenceUrl);
        model.addAttribute("contentBlocks", confluenceUrl.getContentBlocks());

        return "content-blocks/list";
    }

    @GetMapping("/{confluenceUrlId}/new")
    public String showNewForm(@PathVariable("confluenceUrlId") Long confluenceUrlId, Model model,
                              RedirectAttributes redirectAttributes) {

        var urlOpt = confluenceUrlService.findById(confluenceUrlId);
        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        ConfluenceUrl confluenceUrl = urlOpt.get();
        ContentBlock block = new ContentBlock();
        block.setConfluenceUrl(confluenceUrl);

        // Get the highest existing sequence and add 1, or start at 1
        List<ContentBlock> existingBlocks = confluenceUrl.getContentBlocks();
        int nextSequence = existingBlocks.isEmpty() ? 1 :
                existingBlocks.stream().mapToInt(ContentBlock::getSequence).max().orElse(0) + 1;

        block.setSequence(nextSequence);

        model.addAttribute("confluenceUrl", confluenceUrl);
        model.addAttribute("contentBlock", block);

        return "content-blocks/form";
    }

    @PostMapping("/{confluenceUrlId}")
    public String saveBlock(@PathVariable("confluenceUrlId") Long confluenceUrlId,
                            @ModelAttribute("contentBlock") ContentBlock contentBlock,
                            RedirectAttributes redirectAttributes) {

        var urlOpt = confluenceUrlService.findById(confluenceUrlId);
        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        // Ensure the block is associated with the correct URL
        contentBlock.setConfluenceUrl(urlOpt.get());

        contentBlockService.save(contentBlock);
        redirectAttributes.addFlashAttribute("message", "Content block saved successfully");

        return "redirect:/content-blocks/" + confluenceUrlId;
    }

    @GetMapping("/{confluenceUrlId}/edit/{id}")
    public String showEditForm(@PathVariable("confluenceUrlId") Long confluenceUrlId,
                               @PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {

        var urlOpt = confluenceUrlService.findById(confluenceUrlId);
        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        var blockOpt = contentBlockService.findById(id);
        if (blockOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Content block not found");
            return "redirect:/content-blocks/" + confluenceUrlId;
        }

        model.addAttribute("confluenceUrl", urlOpt.get());
        model.addAttribute("contentBlock", blockOpt.get());

        return "content-blocks/form";
    }

    @GetMapping("/{confluenceUrlId}/delete/{id}")
    public String deleteBlock(@PathVariable("confluenceUrlId") Long confluenceUrlId,
                              @PathVariable("id") Long id, RedirectAttributes redirectAttributes) {

        var urlOpt = confluenceUrlService.findById(confluenceUrlId);
        if (urlOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Confluence URL not found");
            return "redirect:/confluence-urls";
        }

        contentBlockService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Content block deleted successfully");

        return "redirect:/content-blocks/" + confluenceUrlId;
    }

    /**
     * List all content blocks regardless of which ConfluenceUrl they belong to
     */
    @GetMapping
    public String listAllBlocks(Model model) {
        List<ContentBlock> allBlocks = contentBlockService.findAll();
        model.addAttribute("contentBlocks", allBlocks);
        return "content-blocks/all";
    }
}