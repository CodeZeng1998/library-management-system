package com.codezeng.lms.web;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.service.BorrowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/borrow")
public class BorrowController {

    private static final BigDecimal OVERDUE_FINE_PER_DAY = new BigDecimal("0.10");
    private static final List<BorrowStatus> ACTIVE_STATUSES = List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE);

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final BorrowService borrowService;

    public BorrowController(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            BorrowService borrowService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.borrowService = borrowService;
    }

    @GetMapping
    public String records(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("records", borrowRecordRepository.findByDeletedFalse(PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        return "borrow/list";
    }

    @GetMapping("/new")
    public String form() {
        return "redirect:/borrow";
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

    @GetMapping("/api/readers/{readerNo}")
    @ResponseBody
    public ResponseEntity<ReaderLookupResponse> lookupReader(@PathVariable String readerNo) {
        return readerRepository.findByReaderNoAndDeletedFalse(readerNo.trim())
                .map(reader -> ResponseEntity.ok(new ReaderLookupResponse(true, "读者已识别", toReaderCard(reader))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ReaderLookupResponse(false, "读者证号不存在", null)));
    }

    @PostMapping("/api/checkout")
    @ResponseBody
    public ResponseEntity<ActionResponse> checkout(@RequestParam String readerNo,
                                                   @RequestParam String isbn) {
        try {
            Reader reader = readerRepository.findByReaderNoAndDeletedFalse(readerNo.trim())
                    .orElseThrow(() -> new IllegalStateException("读者证号不存在"));
            Long bookId = bookRepository.findByIsbnAndDeletedFalse(isbn.trim())
                    .orElseThrow(() -> new IllegalStateException("ISBN 不存在"))
                    .getId();
            BorrowRecord record = borrowService.borrowBook(bookId, reader.getId());
            return ResponseEntity.ok(new ActionResponse(true, "借出成功", toRecordCard(record), toReaderCard(record.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    @GetMapping("/api/returns/resolve")
    @ResponseBody
    public ResponseEntity<ReturnResolveResponse> resolveReturn(@RequestParam String isbn) {
        return borrowRecordRepository.findFirstByBook_IsbnAndStatusInAndDeletedFalseOrderByDueDateAsc(isbn.trim(), ACTIVE_STATUSES)
                .map(record -> ResponseEntity.ok(new ReturnResolveResponse(true, "已加入待还队列", toRecordCard(record), estimateFine(record, false, false))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ReturnResolveResponse(false, "没有找到可归还的在借记录", null, BigDecimal.ZERO)));
    }

    @PostMapping("/api/returns/batch")
    @ResponseBody
    public ResponseEntity<ReturnBatchResponse> batchReturn(@RequestBody ReturnBatchRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return ResponseEntity.badRequest().body(new ReturnBatchResponse(false, "待还队列为空", List.of(), List.of(), BigDecimal.ZERO));
        }

        List<RecordCard> processed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BigDecimal totalFine = BigDecimal.ZERO;
        for (ReturnBatchItem item : request.items()) {
            try {
                BorrowRecord returned = borrowService.returnBook(item.recordId(), item.damaged(), item.lost());
                processed.add(toRecordCard(returned));
                totalFine = totalFine.add(returned.getFineAmount());
            } catch (RuntimeException ex) {
                errors.add("记录 " + item.recordId() + "：" + ex.getMessage());
            }
        }

        String message = errors.isEmpty()
                ? "批量归还完成，共处理 " + processed.size() + " 本"
                : "已处理 " + processed.size() + " 本，失败 " + errors.size() + " 本";
        return ResponseEntity.ok(new ReturnBatchResponse(errors.isEmpty(), message, processed, errors, totalFine));
    }

    @PostMapping("/api/renew")
    @ResponseBody
    public ResponseEntity<ActionResponse> renewByScan(@RequestParam String isbn) {
        try {
            BorrowRecord record = borrowRecordRepository
                    .findFirstByBook_IsbnAndStatusInAndDeletedFalseOrderByDueDateAsc(isbn.trim(), List.of(BorrowStatus.BORROWED))
                    .orElseThrow(() -> new IllegalStateException("没有找到可续借的在借记录"));
            BorrowRecord renewed = borrowService.renew(record.getId());
            return ResponseEntity.ok(new ActionResponse(true, "续借成功", toRecordCard(renewed), toReaderCard(renewed.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    @PostMapping("/api/renew/{id}")
    @ResponseBody
    public ResponseEntity<ActionResponse> renewById(@PathVariable Long id) {
        try {
            BorrowRecord renewed = borrowService.renew(id);
            return ResponseEntity.ok(new ActionResponse(true, "续借成功", toRecordCard(renewed), toReaderCard(renewed.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    private ReaderCard toReaderCard(Reader reader) {
        long activeBorrowCount = borrowRecordRepository.countByReaderAndStatusIn(reader, ACTIVE_STATUSES);
        boolean hasOverdue = borrowRecordRepository.existsByReaderAndStatusAndDueDateBefore(reader, BorrowStatus.BORROWED, LocalDate.now());
        boolean hasUnpaidFine = fineRecordRepository.existsByReaderAndStatus(reader, FineStatus.UNPAID);
        List<String> blockers = new ArrayList<>();
        if (reader.getStatus() != AccountStatus.NORMAL) {
            blockers.add("账号状态不可借阅");
        }
        if (activeBorrowCount >= reader.getMemberLevel().getMaxBorrowBooks()) {
            blockers.add("已达借阅上限");
        }
        if (hasOverdue) {
            blockers.add("存在逾期未还图书");
        }
        if (hasUnpaidFine) {
            blockers.add("存在未缴纳罚款");
        }
        return new ReaderCard(
                reader.getId(),
                reader.getReaderNo(),
                reader.getName(),
                reader.getMemberLevel().getLabel(),
                reader.getStatus().getLabel(),
                reader.getDepositAmount(),
                activeBorrowCount,
                reader.getMemberLevel().getMaxBorrowBooks(),
                hasOverdue,
                hasUnpaidFine,
                blockers.isEmpty(),
                blockers);
    }

    private RecordCard toRecordCard(BorrowRecord record) {
        return new RecordCard(
                record.getId(),
                record.getBook().getTitle(),
                record.getBook().getIsbn(),
                record.getReader().getReaderNo(),
                record.getReader().getName(),
                record.getBorrowDate(),
                record.getDueDate(),
                record.getReturnDate(),
                record.getStatus().name(),
                record.getStatus().getLabel(),
                record.getFineAmount(),
                record.getRenewCount());
    }

    private BigDecimal estimateFine(BorrowRecord record, boolean damaged, boolean lost) {
        long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
        BigDecimal fine = overdueDays <= 0 ? BigDecimal.ZERO : OVERDUE_FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays));
        if (lost && record.getBook().getPrice() != null) {
            return fine.add(record.getBook().getPrice().multiply(new BigDecimal("2.00")));
        }
        if (damaged && record.getBook().getPrice() != null) {
            return fine.add(record.getBook().getPrice().multiply(new BigDecimal("0.50")));
        }
        return fine;
    }

    public record ReaderLookupResponse(boolean success, String message, ReaderCard reader) {
    }

    public record ActionResponse(boolean success, String message, RecordCard record, ReaderCard reader) {
    }

    public record ReturnResolveResponse(boolean success, String message, RecordCard record, BigDecimal estimatedFine) {
    }

    public record ReturnBatchRequest(List<ReturnBatchItem> items) {
    }

    public record ReturnBatchItem(Long recordId, boolean damaged, boolean lost) {
    }

    public record ReturnBatchResponse(boolean success, String message, List<RecordCard> processed, List<String> errors, BigDecimal totalFine) {
    }

    public record ReaderCard(Long id,
                             String readerNo,
                             String name,
                             String memberLevel,
                             String status,
                             BigDecimal depositAmount,
                             long activeBorrowCount,
                             int maxBorrowBooks,
                             boolean hasOverdue,
                             boolean hasUnpaidFine,
                             boolean borrowAllowed,
                             List<String> blockers) {
    }

    public record RecordCard(Long id,
                             String bookTitle,
                             String bookIsbn,
                             String readerNo,
                             String readerName,
                             LocalDate borrowDate,
                             LocalDate dueDate,
                             LocalDate returnDate,
                             String status,
                             String statusLabel,
                             BigDecimal fineAmount,
                             int renewCount) {
    }
}
