package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.ImportResult;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("books", bookService.search(keyword, PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        model.addAttribute("keyword", keyword);
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
}
