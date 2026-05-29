package com.codezeng.lms.web;

import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.BookCategoryService;
import com.codezeng.lms.service.I18nMessageService;
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

@Controller
@RequestMapping("/categories")
public class BookCategoryController {

    private final BookCategoryService categoryService;
    private final I18nMessageService i18n;

    public BookCategoryController(BookCategoryService categoryService, I18nMessageService i18n) {
        this.categoryService = categoryService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        model.addAttribute("categories", categoryService.search(keyword,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.ASC, "name"))));
        model.addAttribute("category", new BookCategory());
        model.addAttribute("parents", categoryService.parents(null));
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, pageSize));
        return "category/list";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String keyword) {
        return csvResponse("book-categories.csv", categoryService.exportCsv(keyword));
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String edit(@PathVariable Long id,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        BookCategory category = categoryService.getEditable(id);
        model.addAttribute("categories", categoryService.search(keyword,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.ASC, "name"))));
        model.addAttribute("category", category);
        model.addAttribute("parents", categoryService.parents(category.getId()));
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, pageSize));
        return "category/list";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @PreventDuplicateSubmit
    public String save(@ModelAttribute BookCategory category,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false) String keyword,
                       RedirectAttributes redirectAttributes) {
        try {
            categoryService.save(category, parentId);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.category.saved"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToList(keyword);
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @PreventDuplicateSubmit
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String keyword,
                         RedirectAttributes redirectAttributes) {
        try {
            categoryService.softDelete(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.category.deleted"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToList(keyword);
    }

    private String redirectToList(String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/categories");
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
