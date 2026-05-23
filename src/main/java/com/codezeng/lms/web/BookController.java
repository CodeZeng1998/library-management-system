package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.service.BookSearchCriteria;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.ImportResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public BookController(BookRepository bookRepository, BookCategoryRepository categoryRepository, BookService bookService) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.bookService = bookService;
    }

    @GetMapping
    public String list(BookSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        normalizeCriteria(criteria);
        Page<Book> books = bookService.search(criteria, PageRequest.of(page, 12, sort(criteria.getSort())));
        model.addAttribute("books", books);
        model.addAttribute("criteria", criteria);
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("locations", bookRepository.findDistinctLocations());
        model.addAttribute("suggestions", suggestions());
        model.addAttribute("queryString", queryString(criteria));
        return "book/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("book", bookRepository.findById(id).orElseThrow());
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @PostMapping
    public String save(@ModelAttribute Book book,
                       @RequestParam(required = false) Long categoryId,
                       RedirectAttributes redirectAttributes) {
        if (categoryId != null) {
            book.setCategory(categoryRepository.findById(categoryId).orElse(null));
        }
        bookService.save(book);
        redirectAttributes.addFlashAttribute("message", "图书信息已保存");
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookService.softDelete(id);
            redirectAttributes.addFlashAttribute("message", "图书已删除");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books";
    }

    @PostMapping("/import")
    public String importCsv(@RequestParam MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            ImportResult result = bookService.importCsv(file);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "导入失败：" + ex.getMessage());
        }
        return "redirect:/books";
    }

    @GetMapping("/export")
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

    private List<String> suggestions() {
        List<String> values = new ArrayList<>();
        bookRepository.findTop10ByDeletedFalseOrderByBorrowCountDesc().forEach(book -> {
            values.add(book.getTitle());
            values.add(book.getAuthor());
            if (book.getCategory() != null) {
                values.add(book.getCategory().getName());
            }
        });
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(20).toList();
    }

    private String queryString(BookSearchCriteria criteria) {
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
