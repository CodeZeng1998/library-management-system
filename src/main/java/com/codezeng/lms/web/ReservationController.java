package com.codezeng.lms.web;

import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.service.ReservationService;
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
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationRecordRepository reservationRecordRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ReservationService reservationService;

    public ReservationController(
            ReservationRecordRepository reservationRecordRepository,
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationService reservationService) {
        this.reservationRecordRepository = reservationRecordRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationService = reservationService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reservations", reservationRecordRepository.findByDeletedFalse(PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "reservedAt"))));
        model.addAttribute("books", bookRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("title"))).getContent());
        model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
        return "reservation/list";
    }

    @PostMapping
    public String reserve(@RequestParam Long bookId,
                          @RequestParam Long readerId,
                          RedirectAttributes redirectAttributes) {
        try {
            reservationService.reserve(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", "预约成功");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reservationService.cancel(id);
        redirectAttributes.addFlashAttribute("message", "预约已取消");
        return "redirect:/reservations";
    }
}
