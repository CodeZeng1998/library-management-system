package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.service.BookService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
}
