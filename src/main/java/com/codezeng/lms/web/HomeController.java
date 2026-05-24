package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
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
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class HomeController {

    private static final Pattern GRADE_PATTERN = Pattern.compile("(20\\d{2})");

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
        model.addAttribute("borrowTrend", borrowTrend(visibleRecords));
        model.addAttribute("borrowTrendDaily", borrowTrendByDay(visibleRecords));
        model.addAttribute("borrowTrendWeekly", borrowTrendByWeek(visibleRecords));
        model.addAttribute("borrowTrendMonthly", borrowTrendByMonth(visibleRecords));
        model.addAttribute("categoryShare", categoryShare(visibleBooks, visibleRecords));
        model.addAttribute("shelfHeatmap", shelfHeatmap(visibleBooks, visibleRecords));
        model.addAttribute("readerActivity", readerActivityDistribution(visibleRecords));
        model.addAttribute("overdueRateTrend", overdueRateTrend(visibleRecords));
        model.addAttribute("newBookImpact", newBookImpact(visibleBooks, visibleRecords));
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

    private List<DashboardPoint> borrowTrendByDay(List<BorrowRecord> records) {
        LocalDate start = LocalDate.now().minusDays(29);
        Map<LocalDate, Long> counts = records.stream()
                .filter(record -> !record.getBorrowDate().isBefore(start))
                .collect(Collectors.groupingBy(BorrowRecord::getBorrowDate, Collectors.counting()));
        return start.datesUntil(LocalDate.now().plusDays(1))
                .map(date -> new DashboardPoint(date.toString().substring(5), counts.getOrDefault(date, 0L)))
                .toList();
    }

    private List<DashboardPoint> borrowTrendByWeek(List<BorrowRecord> records) {
        WeekFields weekFields = WeekFields.ISO;
        LocalDate start = LocalDate.now().minusWeeks(7);
        Map<String, Long> counts = records.stream()
                .filter(record -> !record.getBorrowDate().isBefore(start))
                .collect(Collectors.groupingBy(record -> weekLabel(record.getBorrowDate(), weekFields), Collectors.counting()));
        return IntStream.rangeClosed(0, 7)
                .mapToObj(start::plusWeeks)
                .map(date -> {
                    String label = weekLabel(date, weekFields);
                    return new DashboardPoint(label, counts.getOrDefault(label, 0L));
                })
                .toList();
    }

    private List<DashboardPoint> borrowTrendByMonth(List<BorrowRecord> records) {
        YearMonth start = YearMonth.now().minusMonths(5);
        Map<YearMonth, Long> counts = records.stream()
                .collect(Collectors.groupingBy(record -> YearMonth.from(record.getBorrowDate()), Collectors.counting()));
        return IntStream.rangeClosed(0, 5)
                .mapToObj(start::plusMonths)
                .map(month -> new DashboardPoint(month.toString(), counts.getOrDefault(month, 0L)))
                .toList();
    }

    private List<DashboardItem> categoryShare(List<Book> books, List<BorrowRecord> records) {
        Map<String, Long> borrowCounts = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getBook().getCategory() == null ? "Unclassified" : record.getBook().getCategory().getName(),
                        Collectors.counting()));
        if (borrowCounts.isEmpty()) {
            borrowCounts = books.stream()
                    .collect(Collectors.groupingBy(
                            book -> book.getCategory() == null ? "Unclassified" : book.getCategory().getName(),
                            Collectors.summingLong(Book::getBorrowCount)));
        }
        return borrowCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new DashboardItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ShelfHeatPoint> shelfHeatmap(List<Book> books, List<BorrowRecord> records) {
        Map<Long, Long> borrowCounts = records.stream()
                .collect(Collectors.groupingBy(record -> record.getBook().getId(), Collectors.counting()));
        return books.stream()
                .filter(book -> book.getLocation() != null && !book.getLocation().isBlank())
                .map(book -> {
                    String[] parts = locationParts(book.getLocation());
                    long borrowCount = borrowCounts.getOrDefault(book.getId(), book.getBorrowCount());
                    int totalQuantity = Math.max(book.getTotalQuantity(), 1);
                    double turnoverRate = borrowCount * 1.0 / totalQuantity;
                    return new ShelfHeatPoint(parts[0], parts[1], borrowCount, book.getTotalQuantity(),
                            Math.round(turnoverRate * 10.0) / 10.0, intensity(turnoverRate));
                })
                .sorted(Comparator
                        .comparing(ShelfHeatPoint::area)
                        .thenComparing(ShelfHeatPoint::shelf))
                .limit(24)
                .toList();
    }

    private List<DashboardItem> readerActivityDistribution(List<BorrowRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(record -> readerSegment(record.getReader()), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new DashboardItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardRatePoint> overdueRateTrend(List<BorrowRecord> records) {
        LocalDate start = LocalDate.now().minusDays(29);
        Map<LocalDate, List<BorrowRecord>> dueByDate = records.stream()
                .filter(record -> !record.getDueDate().isBefore(start))
                .collect(Collectors.groupingBy(BorrowRecord::getDueDate));
        return start.datesUntil(LocalDate.now().plusDays(1))
                .map(date -> {
                    List<BorrowRecord> dueRecords = dueByDate.getOrDefault(date, List.of());
                    long overdue = dueRecords.stream().filter(this::isOverdueRecord).count();
                    long total = dueRecords.size();
                    double rate = total == 0 ? 0 : overdue * 100.0 / total;
                    return new DashboardRatePoint(date.toString().substring(5), overdue, total, Math.round(rate * 10.0) / 10.0);
                })
                .toList();
    }

    private List<NewBookImpactPoint> newBookImpact(List<Book> books, List<BorrowRecord> records) {
        YearMonth start = YearMonth.now().minusMonths(5);
        Map<YearMonth, Long> newBookCounts = books.stream()
                .filter(book -> book.getCreateTime() != null)
                .collect(Collectors.groupingBy(book -> YearMonth.from(book.getCreateTime()), Collectors.counting()));
        Map<YearMonth, Long> newBookBorrows = records.stream()
                .filter(record -> record.getBook().getCreateTime() != null)
                .collect(Collectors.groupingBy(record -> YearMonth.from(record.getBook().getCreateTime()), Collectors.counting()));
        return IntStream.rangeClosed(0, 5)
                .mapToObj(start::plusMonths)
                .map(month -> new NewBookImpactPoint(month.toString(),
                        newBookCounts.getOrDefault(month, 0L),
                        newBookBorrows.getOrDefault(month, 0L)))
                .toList();
    }

    private boolean isOverdueRecord(BorrowRecord record) {
        if (record.getStatus() == BorrowStatus.OVERDUE) {
            return true;
        }
        if (record.getReturnDate() != null) {
            return record.getReturnDate().isAfter(record.getDueDate());
        }
        return record.getDueDate().isBefore(LocalDate.now())
                && (record.getStatus() == BorrowStatus.BORROWED || record.getStatus() == BorrowStatus.OVERDUE);
    }

    private String weekLabel(LocalDate date, WeekFields weekFields) {
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    private String[] locationParts(String location) {
        String trimmed = location.trim();
        String[] tokens = trimmed.split("\\s+", 2);
        if (tokens.length == 2) {
            return new String[]{tokens[0], tokens[1]};
        }
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            return new String[]{trimmed.substring(0, dash), trimmed.substring(dash + 1)};
        }
        return new String[]{trimmed, "Default shelf"};
    }

    private String intensity(double turnoverRate) {
        if (turnoverRate >= 30) {
            return "level-5";
        }
        if (turnoverRate >= 15) {
            return "level-4";
        }
        if (turnoverRate >= 8) {
            return "level-3";
        }
        if (turnoverRate >= 3) {
            return "level-2";
        }
        return "level-1";
    }

    private String readerSegment(Reader reader) {
        String type = reader.getReaderType() == null ? "OTHER" : reader.getReaderType().name();
        return type + " / " + grade(reader.getIdentityNo());
    }

    private String grade(String identityNo) {
        if (identityNo == null) {
            return "Unknown grade";
        }
        Matcher matcher = GRADE_PATTERN.matcher(identityNo);
        return matcher.find() ? matcher.group(1) : "Unknown grade";
    }

    public record DashboardPoint(String label, long value) {
    }

    public record DashboardItem(String label, long value) {
    }

    public record DashboardRatePoint(String label, long overdue, long total, double rate) {
    }

    public record ShelfHeatPoint(String area, String shelf, long borrowCount, int totalQuantity, double turnoverRate, String intensity) {
    }

    public record NewBookImpactPoint(String label, long newBooks, long borrows) {
    }
}
