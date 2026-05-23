package com.codezeng.lms.web;

import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReservationRecordRepository reservationRecordRepository;

    public HomeController(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            ReservationRecordRepository reservationRecordRepository) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.reservationRecordRepository = reservationRecordRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("bookCount", bookRepository.countByDeletedFalse());
        model.addAttribute("readerCount", readerRepository.countByDeletedFalse());
        model.addAttribute("activeBorrowCount", borrowRecordRepository.countByStatusIn(List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE)));
        model.addAttribute("reservationCount", reservationRecordRepository.count());
        model.addAttribute("topBooks", bookRepository.findTop10ByDeletedFalseOrderByBorrowCountDesc());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
