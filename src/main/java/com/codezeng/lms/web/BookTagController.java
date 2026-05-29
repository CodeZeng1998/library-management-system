package com.codezeng.lms.web;

import com.codezeng.lms.domain.BookTag;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.BookTagService;
import com.codezeng.lms.service.I18nMessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/tags")
public class BookTagController {

    private final BookTagService tagService;
    private final I18nMessageService i18n;

    public BookTagController(BookTagService tagService, I18nMessageService i18n) {
        this.tagService = tagService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<BookTag> tags = tagService.search(keyword,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("tags", tags);
        model.addAttribute("tag", new BookTag());
        model.addAttribute("usageCounts", usageCounts(tags));
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, pageSize));
        return "tag/list";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String keyword) {
        return csvResponse("book-tags.csv", tagService.exportCsv(keyword));
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public String edit(@PathVariable Long id,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<BookTag> tags = tagService.search(keyword,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("tags", tags);
        model.addAttribute("tag", tagService.getEditable(id));
        model.addAttribute("usageCounts", usageCounts(tags));
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, pageSize));
        return "tag/list";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    @PreventDuplicateSubmit
    public String save(@ModelAttribute BookTag tag,
                       @RequestParam(required = false) String keyword,
                       RedirectAttributes redirectAttributes) {
        try {
            tagService.save(tag);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.tag.saved"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToList(keyword);
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    @PreventDuplicateSubmit
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String keyword,
                         RedirectAttributes redirectAttributes) {
        try {
            tagService.softDelete(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.tag.deleted"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToList(keyword);
    }

    private Map<Long, Long> usageCounts(Page<BookTag> tags) {
        return tagService.usageCounts(tags.getContent());
    }

    private String redirectToList(String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/tags");
        if (keyword != null && !keyword.isBlank()) {
            builder.queryParam("keyword", keyword);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }

    private String queryString(String keyword, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        if (keyword != null && !keyword.isBlank()) {
            builder.queryParam("keyword", keyword);
        }
        builder.queryParam("size", size);
        String query = builder.build().encode().toUriString();
        return query.startsWith("?") ? "&" + query.substring(1) : query;
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
