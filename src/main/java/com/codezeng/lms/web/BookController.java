package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.BookSearchCriteria;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ImportResult;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/books")
public class BookController {

    private final BookRepository bookRepository;
    private final BookCategoryRepository categoryRepository;
    private final BookService bookService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public BookController(BookRepository bookRepository,
                          BookCategoryRepository categoryRepository,
                          BookService bookService,
                          DataScopeService dataScopeService,
                          I18nMessageService i18n) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.bookService = bookService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOOK_VIEW')")
    public String list(BookSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "30") int size,
                       Model model) {
        normalizeCriteria(criteria);
        int pageSize = normalizedPageSize(size);
        Page<Book> books = bookService.search(criteria, PageRequest.of(page, pageSize, sort(criteria.getSort())));
        model.addAttribute("books", books);
        model.addAttribute("criteria", criteria);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("locations", bookRepository.findDistinctLocations());
        model.addAttribute("suggestions", suggestions());
        model.addAttribute("queryString", queryString(criteria, pageSize));
        return "book/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    public String create(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("book", bookRepository.findById(id).orElseThrow());
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    public String save(@ModelAttribute Book book,
                       @RequestParam(required = false) Long categoryId,
                       RedirectAttributes redirectAttributes) {
        if (categoryId != null) {
            book.setCategory(categoryRepository.findById(categoryId).orElse(null));
        }
        bookService.save(book);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.book.saved"));
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookService.softDelete(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.book.deleted"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books";
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('BOOK_IMPORT')")
    public String importCsv(@RequestParam MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            ImportResult result = bookService.importCsv(file);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        }
        return "redirect:/books";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('BOOK_VIEW')")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=books.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bookService.exportCsv().getBytes(StandardCharsets.UTF_8));
    }

    private void normalizeCriteria(BookSearchCriteria criteria) {
        if (criteria.getSort() == null || criteria.getSort().isBlank()) {
            criteria.setSort("relevance");
        }
        if (criteria.getView() == null || criteria.getView().isBlank()) {
            criteria.setView("list");
        }
        if (criteria.getTitleMatch() == null || criteria.getTitleMatch().isBlank()) {
            criteria.setTitleMatch("contains");
        }
        if (criteria.getAuthorMatch() == null || criteria.getAuthorMatch().isBlank()) {
            criteria.setAuthorMatch("contains");
        }
    }

    private Sort sort(String value) {
        return switch (value) {
            case "publishDateAsc" -> Sort.by(Sort.Direction.ASC, "publishDate").and(Sort.by(Sort.Direction.DESC, "createTime"));
            case "borrowHot" -> Sort.by(Sort.Direction.DESC, "borrowCount").and(Sort.by(Sort.Direction.DESC, "createTime"));
            case "titleAsc" -> Sort.by(Sort.Direction.ASC, "title");
            case "relevance" -> Sort.by(Sort.Direction.DESC, "availableQuantity").and(Sort.by(Sort.Direction.DESC, "borrowCount"));
            default -> Sort.by(Sort.Direction.DESC, "publishDate").and(Sort.by(Sort.Direction.DESC, "createTime"));
        };
    }

    private int normalizedPageSize(int size) {
        if (size <= 0) {
            return 30;
        }
        return Math.min(size, 100);
    }

    private List<String> suggestions() {
        List<String> values = new ArrayList<>();
        bookRepository.findAll(dataScopeService.bookScope(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "borrowCount"))).forEach(book -> {
            values.add(book.getTitle());
            values.add(book.getAuthor());
            if (book.getCategory() != null) {
                values.add(book.getCategory().getName());
            }
        });
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(20).toList();
    }

    private String queryString(BookSearchCriteria criteria, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "keyword", criteria.getKeyword());
        queryParam(builder, "title", criteria.getTitle());
        queryParam(builder, "titleMatch", criteria.getTitleMatch());
        queryParam(builder, "author", criteria.getAuthor());
        queryParam(builder, "authorMatch", criteria.getAuthorMatch());
        if (criteria.getCategoryIds() != null) {
            criteria.getCategoryIds().forEach(id -> builder.queryParam("categoryIds", id));
        }
        queryParam(builder, "publishYearFrom", criteria.getPublishYearFrom());
        queryParam(builder, "publishYearTo", criteria.getPublishYearTo());
        if (criteria.isAvailableOnly()) {
            builder.queryParam("availableOnly", true);
        }
        queryParam(builder, "location", criteria.getLocation());
        queryParam(builder, "sort", criteria.getSort());
        queryParam(builder, "view", criteria.getView());
        builder.queryParam("size", size);
        String query = builder.build().encode().toUriString();
        return query.startsWith("?") ? "&" + query.substring(1) : query;
    }

    private void queryParam(UriComponentsBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            builder.queryParam(name, text);
        }
    }
}
