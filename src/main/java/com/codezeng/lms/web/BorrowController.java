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
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.I18nMessageService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/borrow")
public class BorrowController {

    private static final List<BorrowStatus> ACTIVE_STATUSES = List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE);

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final BorrowService borrowService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public BorrowController(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            BorrowService borrowService,
            DataScopeService dataScopeService,
            I18nMessageService i18n) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.borrowService = borrowService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    public String records(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "30") int size,
                          @RequestParam(required = false) BorrowStatus status,
                          @RequestParam(required = false) String keyword,
                          Model model) {
        int pageSize = borrowService.normalizePageSize(size);
        Page<BorrowRecord> records = borrowService.search(status, keyword, page, pageSize);
        model.addAttribute("records", records);
        model.addAttribute("statuses", BorrowStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(status, keyword, pageSize));
        return "borrow/list";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) BorrowStatus status,
                                            @RequestParam(required = false) String keyword) {
        return csvResponse("borrow-records.csv", borrowService.exportCsv(status, keyword));
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    public String form() {
        return "redirect:/borrow";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public String borrow(@RequestParam Long bookId,
                         @RequestParam Long readerId,
                         RedirectAttributes redirectAttributes) {
        try {
            borrowService.borrowBook(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.borrow.success"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/borrow";
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public String returnBook(@PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean damaged,
                             @RequestParam(defaultValue = "false") boolean lost,
                             RedirectAttributes redirectAttributes) {
        borrowService.returnBook(id, damaged, lost);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.borrow.returned"));
        return "redirect:/borrow";
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public String renew(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            borrowService.renew(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.borrow.renewed"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/borrow";
    }

    @PostMapping("/overdue-scan")
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public String overdueScan(RedirectAttributes redirectAttributes) {
        borrowService.markOverdueRecords();
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.borrow.overdueScan"));
        return "redirect:/borrow";
    }

    @GetMapping("/api/readers/{readerNo}")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    public ResponseEntity<ReaderLookupResponse> lookupReader(@PathVariable String readerNo) {
        return readerRepository.findByReaderNoAndDeletedFalse(readerNo.trim())
                .map(reader -> ResponseEntity.ok(new ReaderLookupResponse(true, i18n.get("api.borrow.readerFound"), toReaderCard(reader))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ReaderLookupResponse(false, i18n.get("api.borrow.readerNotFound"), null)));
    }

    @PostMapping("/api/checkout")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public ResponseEntity<ActionResponse> checkout(@RequestParam String readerNo,
                                                   @RequestParam String isbn) {
        try {
            Reader reader = readerRepository.findByReaderNoAndDeletedFalse(readerNo.trim())
                    .orElseThrow(() -> new IllegalStateException(i18n.get("api.borrow.readerNotFound")));
            Long bookId = bookRepository.findByIsbnAndDeletedFalse(isbn.trim())
                    .orElseThrow(() -> new IllegalStateException(i18n.get("api.borrow.isbnNotFound")))
                    .getId();
            BorrowRecord record = borrowService.borrowBook(bookId, reader.getId());
            return ResponseEntity.ok(new ActionResponse(true, i18n.get("api.borrow.checkoutSuccess"), toRecordCard(record), toReaderCard(record.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    @GetMapping("/api/returns/resolve")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    public ResponseEntity<ReturnResolveResponse> resolveReturn(@RequestParam String isbn) {
        return borrowRecordRepository.findFirstByBook_IsbnAndStatusInAndDeletedFalseOrderByDueDateAsc(isbn.trim(), ACTIVE_STATUSES)
                .filter(dataScopeService::canAccess)
                .map(record -> ResponseEntity.ok(new ReturnResolveResponse(true, i18n.get("api.borrow.returnQueued"), toRecordCard(record), estimateFine(record, false, false))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ReturnResolveResponse(false, i18n.get("api.borrow.returnRecordNotFound"), null, BigDecimal.ZERO)));
    }

    @PostMapping("/api/returns/batch")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public ResponseEntity<ReturnBatchResponse> batchReturn(@RequestBody ReturnBatchRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return ResponseEntity.badRequest().body(new ReturnBatchResponse(false, i18n.get("borrow.returnQueue.empty"), List.of(), List.of(), BigDecimal.ZERO));
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
                errors.add(i18n.get("api.borrow.recordError", item.recordId(), ex.getMessage()));
            }
        }

        String message = errors.isEmpty()
                ? i18n.get("api.borrow.batchReturnSuccess", processed.size())
                : i18n.get("api.borrow.batchReturnPartial", processed.size(), errors.size());
        return ResponseEntity.ok(new ReturnBatchResponse(errors.isEmpty(), message, processed, errors, totalFine));
    }

    @PostMapping("/api/renew")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public ResponseEntity<ActionResponse> renewByScan(@RequestParam String isbn) {
        try {
            BorrowRecord record = borrowRecordRepository
                    .findFirstByBook_IsbnAndStatusInAndDeletedFalseOrderByDueDateAsc(isbn.trim(), List.of(BorrowStatus.BORROWED))
                    .filter(dataScopeService::canAccess)
                    .orElseThrow(() -> new IllegalStateException(i18n.get("api.borrow.renewRecordNotFound")));
            BorrowRecord renewed = borrowService.renew(record.getId());
            return ResponseEntity.ok(new ActionResponse(true, i18n.get("flash.borrow.renewed"), toRecordCard(renewed), toReaderCard(renewed.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    @PostMapping("/api/renew/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('BORROW_MANAGE')")
    @PreventDuplicateSubmit
    public ResponseEntity<ActionResponse> renewById(@PathVariable Long id) {
        try {
            BorrowRecord renewed = borrowService.renew(id);
            return ResponseEntity.ok(new ActionResponse(true, i18n.get("flash.borrow.renewed"), toRecordCard(renewed), toReaderCard(renewed.getReader())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ActionResponse(false, ex.getMessage(), null, null));
        }
    }

    private ReaderCard toReaderCard(Reader reader) {
        long activeBorrowCount = borrowRecordRepository.countByReaderAndStatusInAndDeletedFalse(reader, ACTIVE_STATUSES);
        boolean hasOverdue = borrowRecordRepository.existsByReaderAndStatusAndDueDateBeforeAndDeletedFalse(reader, BorrowStatus.BORROWED, LocalDate.now());
        boolean hasUnpaidFine = fineRecordRepository.existsByReaderAndStatusAndDeletedFalse(reader, FineStatus.UNPAID);
        List<String> blockers = new ArrayList<>();
        if (reader.getStatus() != AccountStatus.NORMAL) {
            blockers.add(i18n.get("api.borrow.blocker.accountStatus"));
        }
        int maxBorrowBooks = borrowService.maxBorrowBooks(reader);
        if (activeBorrowCount >= maxBorrowBooks) {
            blockers.add(i18n.get("api.borrow.blocker.maxBorrow"));
        }
        if (hasOverdue) {
            blockers.add(i18n.get("api.borrow.blocker.overdue"));
        }
        if (hasUnpaidFine) {
            blockers.add(i18n.get("api.borrow.blocker.unpaidFine"));
        }
        return new ReaderCard(
                reader.getId(),
                reader.getReaderNo(),
                reader.getName(),
                i18n.enumLabel("memberLevel", reader.getMemberLevel()),
                i18n.enumLabel("status", reader.getStatus()),
                reader.getDepositAmount(),
                activeBorrowCount,
                maxBorrowBooks,
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
                i18n.enumLabel("borrowStatus", record.getStatus()),
                record.getFineAmount(),
                record.getRenewCount());
    }

    private BigDecimal estimateFine(BorrowRecord record, boolean damaged, boolean lost) {
        return borrowService.estimateFine(record, damaged, lost);
    }

    private String queryString(BorrowStatus status, String keyword, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "status", status);
        queryParam(builder, "keyword", keyword);
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

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
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
