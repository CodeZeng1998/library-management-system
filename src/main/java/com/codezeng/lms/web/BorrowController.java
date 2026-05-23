package com.codezeng.lms.web;

import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.service.BorrowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/borrow")
public class BorrowController {

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowService borrowService;

    public BorrowController(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            BorrowService borrowService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.borrowService = borrowService;
    }

    @GetMapping
    public String records(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("records", borrowRecordRepository.findByDeletedFalse(PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        return "borrow/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("books", bookRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("title"))).getContent());
        model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
        return "borrow/form";
    }

    @PostMapping
    public String borrow(@RequestParam Long bookId,
                         @RequestParam Long readerId,
                         RedirectAttributes redirectAttributes) {
        try {
            borrowService.borrowBook(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", "借书成功");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/borrow";
    }

    @PostMapping("/{id}/return")
    public String returnBook(@PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean damaged,
                             @RequestParam(defaultValue = "false") boolean lost,
                             RedirectAttributes redirectAttributes) {
        borrowService.returnBook(id, damaged, lost);
        redirectAttributes.addFlashAttribute("message", "还书处理完成");
        return "redirect:/borrow";
    }

    @PostMapping("/{id}/renew")
    public String renew(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            borrowService.renew(id);
            redirectAttributes.addFlashAttribute("message", "续借成功");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/borrow";
    }

    @PostMapping("/overdue-scan")
    public String overdueScan(RedirectAttributes redirectAttributes) {
        borrowService.markOverdueRecords();
        redirectAttributes.addFlashAttribute("message", "逾期扫描已完成");
        return "redirect:/borrow";
    }
}
