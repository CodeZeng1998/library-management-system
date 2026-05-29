package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BookTagRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.BookSearchCriteria;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.CsvImportGuard;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ImportResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import com.codezeng.lms.security.PreventDuplicateSubmit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/books")
public class BookController {

    private static final String BOOK_IMPORT_BYTES = "BOOK_IMPORT_BYTES";
    private static final String BOOK_IMPORT_ERRORS = "BOOK_IMPORT_ERRORS";

    private final BookRepository bookRepository;
    private final BookCategoryRepository categoryRepository;
    private final BookTagRepository tagRepository;
    private final BookService bookService;
    private final CsvImportGuard csvImportGuard;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public BookController(BookRepository bookRepository,
                          BookCategoryRepository categoryRepository,
                          BookTagRepository tagRepository,
                          BookService bookService,
                          CsvImportGuard csvImportGuard,
                          DataScopeService dataScopeService,
                          I18nMessageService i18n) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.bookService = bookService;
        this.csvImportGuard = csvImportGuard;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOOK_VIEW')")
    public String list(BookSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "30") int size,
                       HttpServletRequest request,
                       Model model) {
        normalizeCriteria(criteria);
        int pageSize = normalizedPageSize(size);
        Page<Book> books = bookService.search(criteria, PageRequest.of(page, pageSize, sort(criteria.getSort())));
        model.addAttribute("books", books);
        model.addAttribute("criteria", criteria);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("tags", tagRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("locations", visibleLocations());
        model.addAttribute("suggestions", suggestions());
        model.addAttribute("queryString", queryString(criteria, pageSize));
        model.addAttribute("exportQueryString", exportQueryString(request));
        model.addAttribute("currentUrl", currentUrl(request));
        return "book/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    public String create(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("tags", tagRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("book", bookService.getVisible(id));
        model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
        model.addAttribute("tags", tagRepository.findByDeletedFalseOrderByNameAsc());
        return "book/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BOOK_EDIT')")
    @PreventDuplicateSubmit
    public String save(@ModelAttribute Book book,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) List<Long> tagIds,
                       @RequestParam(required = false) String tagNames,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            if (categoryId != null) {
                book.setCategory(categoryRepository.findById(categoryId).orElse(null));
            }
            book.setTags(bookService.resolveTags(tagIds, tagNames));
            bookService.save(book);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.book.saved"));
            return "redirect:/books";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("book", book);
            model.addAttribute("categories", categoryRepository.findByDeletedFalseOrderByNameAsc());
            model.addAttribute("tags", tagRepository.findByDeletedFalseOrderByNameAsc());
            model.addAttribute("error", ex.getMessage());
            return "book/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    @PreventDuplicateSubmit
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
    public String importCsv(@RequestParam MultipartFile file,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            csvImportGuard.validate(file);
            ImportResult result = bookService.importCsv(file);
            rememberErrorReport(session, BOOK_IMPORT_ERRORS, result);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books";
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAuthority('BOOK_IMPORT')")
    public String previewImport(@RequestParam MultipartFile file,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            csvImportGuard.validate(file);
            session.setAttribute(BOOK_IMPORT_BYTES, file.getBytes());
            ImportResult result = bookService.previewCsv(file);
            rememberErrorReport(session, BOOK_IMPORT_ERRORS, result);
            model.addAttribute("result", result);
            model.addAttribute("importTitle", "Book CSV import preview");
            model.addAttribute("confirmUrl", "/books/import/confirm");
            model.addAttribute("cancelUrl", "/books");
            model.addAttribute("templateUrl", "/books/import/template");
            model.addAttribute("errorReportUrl", "/books/import/errors");
            return "import/preview";
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
            return "redirect:/books";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/books";
        }
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAuthority('BOOK_IMPORT')")
    public String confirmImport(HttpSession session, RedirectAttributes redirectAttributes) {
        byte[] bytes = (byte[]) session.getAttribute(BOOK_IMPORT_BYTES);
        if (bytes == null) {
            redirectAttributes.addFlashAttribute("error", "No previewed CSV file found. Please upload and preview again.");
            return "redirect:/books";
        }
        try {
            ImportResult result = bookService.importCsv(bytes);
            rememberErrorReport(session, BOOK_IMPORT_ERRORS, result);
            session.removeAttribute(BOOK_IMPORT_BYTES);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        }
        return "redirect:/books";
    }

    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('BOOK_IMPORT')")
    public ResponseEntity<byte[]> importTemplate() {
        return csvResponse("book-import-template.csv", bookService.importTemplateCsv());
    }

    @GetMapping("/import/errors")
    @PreAuthorize("hasAuthority('BOOK_IMPORT')")
    public ResponseEntity<byte[]> importErrors(HttpSession session) {
        String csv = (String) session.getAttribute(BOOK_IMPORT_ERRORS);
        return csvResponse("book-import-errors.csv", csv == null ? "\uFEFFrow,status,message,values\n" : csv);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('BOOK_VIEW')")
    public ResponseEntity<byte[]> exportCsv(BookSearchCriteria criteria) {
        normalizeCriteria(criteria);
        return csvResponse("books.csv", bookService.exportCsv(criteria));
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    @PreventDuplicateSubmit
    public String batchDelete(@RequestParam(required = false) List<Long> ids,
                              RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", i18n.get("common.batch.none"));
            return "redirect:/books";
        }
        BookService.BatchOperationResult result = bookService.batchSoftDelete(ids);
        if (result.hasFailures()) {
            redirectAttributes.addFlashAttribute("error",
                    i18n.get("flash.book.batchDeletedWithFailures", result.successCount(), result.failureCount()));
        } else {
            redirectAttributes.addFlashAttribute("message",
                    i18n.get("flash.book.batchDeleted", result.successCount()));
        }
        return "redirect:/books";
    }

    @GetMapping("/trash")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    public String trash(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "30") int size,
                        Model model) {
        int pageSize = normalizedPageSize(size);
        model.addAttribute("books", bookService.trash(PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "updateTime"))));
        model.addAttribute("pageSize", pageSize);
        return "book/trash";
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    @PreventDuplicateSubmit
    public String restore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookService.restore(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.book.restored"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books/trash";
    }

    @PostMapping("/{id}/purge")
    @PreAuthorize("hasAuthority('BOOK_DELETE')")
    @PreventDuplicateSubmit
    public String purge(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookService.purge(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.book.purged"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books/trash";
    }

    private void rememberErrorReport(HttpSession session, String key, ImportResult result) {
        if (result.getFailureCount() > 0) {
            session.setAttribute(key, result.errorReportCsv());
        } else {
            session.removeAttribute(key);
        }
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
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
            if (book.getTags() != null) {
                book.getTags().forEach(tag -> values.add(tag.getName()));
            }
        });
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(20).toList();
    }

    private List<String> visibleLocations() {
        return dataScopeService.currentLocationPrefix()
                .map(bookRepository::findDistinctLocationsByPrefix)
                .orElseGet(bookRepository::findDistinctLocations);
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
        if (criteria.getTagIds() != null) {
            criteria.getTagIds().forEach(id -> builder.queryParam("tagIds", id));
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

    private String currentUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
    }

    private String exportQueryString(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }
}
