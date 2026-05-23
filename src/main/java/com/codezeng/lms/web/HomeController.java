package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final DataScopeService dataScopeService;

    public HomeController(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            ReservationRecordRepository reservationRecordRepository,
            DataScopeService dataScopeService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Book> visibleBooks = bookRepository.findAll(dataScopeService.bookScope());
        List<BorrowRecord> visibleRecords = borrowRecordRepository.findAll(dataScopeService.borrowRecordScope());
        long activeBorrowCount = visibleRecords.stream()
                .filter(record -> record.getStatus() == BorrowStatus.BORROWED || record.getStatus() == BorrowStatus.OVERDUE)
                .count();
        List<BorrowRecord> overdueRecords = visibleRecords.stream()
                .filter(record -> record.getStatus() == BorrowStatus.OVERDUE
                        || (record.getStatus() == BorrowStatus.BORROWED && record.getDueDate().isBefore(LocalDate.now())))
                .sorted(Comparator.comparing(BorrowRecord::getDueDate))
                .limit(8)
                .toList();
        List<DashboardPoint> trend = borrowTrend(visibleRecords);
        List<DashboardItem> categoryShare = categoryShare(visibleBooks);

        model.addAttribute("bookCount", visibleBooks.size());
        model.addAttribute("readerCount", readerRepository.countByDeletedFalse());
        model.addAttribute("activeBorrowCount", activeBorrowCount);
        model.addAttribute("reservationCount", reservationRecordRepository.count(dataScopeService.reservationScope()));
        model.addAttribute("topBooks", visibleBooks.stream()
                .sorted(Comparator.comparingLong(Book::getBorrowCount).reversed())
                .limit(8)
                .toList());
        model.addAttribute("recentBooks", bookRepository.findAll(dataScopeService.bookScope(), PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createTime"))).getContent());
        model.addAttribute("overdueRecords", overdueRecords);
        model.addAttribute("borrowTrend", trend);
        model.addAttribute("categoryShare", categoryShare);
        model.addAttribute("readerActivity", readerActivity(visibleRecords));
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    private List<DashboardPoint> borrowTrend(List<BorrowRecord> records) {
        LocalDate start = LocalDate.now().minusDays(6);
        Map<LocalDate, Long> counts = records.stream()
                .filter(record -> !record.getBorrowDate().isBefore(start))
                .collect(Collectors.groupingBy(BorrowRecord::getBorrowDate, LinkedHashMap::new, Collectors.counting()));
        return start.datesUntil(LocalDate.now().plusDays(1))
                .map(date -> new DashboardPoint(date.toString().substring(5), counts.getOrDefault(date, 0L)))
                .toList();
    }

    private List<DashboardItem> categoryShare(List<Book> books) {
        return books.stream()
                .collect(Collectors.groupingBy(
                        book -> book.getCategory() == null ? "未分类" : book.getCategory().getName(),
                        Collectors.summingLong(Book::getBorrowCount)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new DashboardItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardItem> readerActivity(List<BorrowRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getReader().getReaderNo() + " " + record.getReader().getName(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new DashboardItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record DashboardPoint(String label, long value) {
    }

    public record DashboardItem(String label, long value) {
    }
}
