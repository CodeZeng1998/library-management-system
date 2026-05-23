package com.codezeng.lms.web;

import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ReservationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public ReservationController(
            ReservationRecordRepository reservationRecordRepository,
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationService reservationService,
            DataScopeService dataScopeService,
            I18nMessageService i18n) {
        this.reservationRecordRepository = reservationRecordRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationService = reservationService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reservations", reservationRecordRepository.findAll(dataScopeService.reservationScope(), PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "reservedAt"))));
        model.addAttribute("books", bookRepository.findAll(dataScopeService.bookScope(), PageRequest.of(0, 200, Sort.by("title"))).getContent());
        model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
        return "reservation/list";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String reserve(@RequestParam Long bookId,
                          @RequestParam Long readerId,
                          RedirectAttributes redirectAttributes) {
        try {
            reservationService.reserve(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.created"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reservationService.cancel(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.cancelled"));
        return "redirect:/reservations";
    }
}
