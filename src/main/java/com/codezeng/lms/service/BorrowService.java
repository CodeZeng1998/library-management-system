package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BorrowService {

    private static final DateTimeFormatter EXPORT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final OperationLogService operationLogService;
    private final ReservationService reservationService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public BorrowService(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            OperationLogService operationLogService,
            ReservationService reservationService,
            DataScopeService dataScopeService,
            I18nMessageService i18n,
            SystemConfigService systemConfigService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.operationLogService = operationLogService;
        this.reservationService = reservationService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<BorrowRecord> search(BorrowStatus status, String keyword, int page, int size) {
        int pageSize = normalizePageSize(size);
        return borrowRecordRepository.findAll(
                borrowSpec(status, keyword),
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    public String exportCsv(BorrowStatus status, String keyword) {
        List<BorrowRecord> records = borrowRecordRepository.findAll(
                borrowSpec(status, keyword),
                PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "createTime"))).getContent();
        StringBuilder csv = new StringBuilder("\uFEFFBook Title,ISBN,Reader No,Reader Name,Borrow Date,Due Date,Return Date,Status,Fine,Renew Count\n");
        for (BorrowRecord record : records) {
            csv.append(CsvSupport.csv(record.getBook().getTitle())).append(',')
                    .append(CsvSupport.csv(record.getBook().getIsbn())).append(',')
                    .append(CsvSupport.csv(record.getReader().getReaderNo())).append(',')
                    .append(CsvSupport.csv(record.getReader().getName())).append(',')
                    .append(CsvSupport.csv(formatDate(record.getBorrowDate()))).append(',')
                    .append(CsvSupport.csv(formatDate(record.getDueDate()))).append(',')
                    .append(CsvSupport.csv(formatDate(record.getReturnDate()))).append(',')
                    .append(CsvSupport.csv(record.getStatus().name())).append(',')
                    .append(CsvSupport.csv(String.valueOf(record.getFineAmount()))).append(',')
                    .append(CsvSupport.csv(String.valueOf(record.getRenewCount()))).append('\n');
        }
        operationLogService.record("Borrow management", "Export borrow records", "Rows: " + records.size());
        return csv.toString();
    }

    public int normalizePageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    @Transactional
    public BorrowRecord borrowBook(Long bookId, Long readerId) {
        Book book = bookRepository.findByIdForUpdate(bookId).orElseThrow();
        dataScopeService.requireAccess(book);
        Reader reader = readerRepository.findByIdAndDeletedFalse(readerId).orElseThrow();
        boolean useLockedReservation = validateBorrow(book, reader);

        BorrowRecord record = new BorrowRecord();
        record.setBook(book);
        record.setReader(reader);
        record.setBorrowDate(LocalDate.now());
        record.setDueDate(LocalDate.now().plusDays(reader.getMemberLevel().getBorrowDays()));
        record.setStatus(BorrowStatus.BORROWED);

        if (useLockedReservation) {
            reservationService.completeIfNotified(book, reader);
        } else {
            book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        }
        book.setBorrowCount(book.getBorrowCount() + 1);
        bookRepository.save(book);

        BorrowRecord saved = borrowRecordRepository.save(record);
        operationLogService.record("Borrow management", "Borrow book", book.getTitle() + " -> " + reader.getReaderNo());
        return saved;
    }

    @Transactional
    public BorrowRecord returnBook(Long recordId, boolean damaged, boolean lost) {
        BorrowRecord record = borrowRecordRepository.findByIdForUpdate(recordId).orElseThrow();
        dataScopeService.requireAccess(record);
        if (isTerminalReturnStatus(record.getStatus())) {
            return record;
        }

        BigDecimal fineAmount = calculateOverdueFine(record);
        record.setFineAmount(fineAmount);
        record.setReturnDate(LocalDate.now());
        if (lost) {
            record.setStatus(BorrowStatus.LOST);
            fineAmount = fineAmount.add(compensation(record.getBook(), new BigDecimal("2.00")));
        } else if (damaged) {
            record.setStatus(BorrowStatus.DAMAGED);
            fineAmount = fineAmount.add(compensation(record.getBook(), new BigDecimal("0.50")));
        } else {
            record.setStatus(BorrowStatus.RETURNED);
            record.getBook().setAvailableQuantity(record.getBook().getAvailableQuantity() + 1);
            reservationService.lockNextReservation(record.getBook());
        }
        record.setFineAmount(fineAmount);

        if (fineAmount.compareTo(BigDecimal.ZERO) > 0) {
            FineRecord fine = new FineRecord();
            fine.setReader(record.getReader());
            fine.setBorrowRecord(record);
            fine.setReason(lost ? i18n.get("fine.reason.lost") : damaged ? i18n.get("fine.reason.damaged") : i18n.get("fine.reason.overdue"));
            fine.setAmount(fineAmount);
            fine.setStatus(FineStatus.UNPAID);
            fineRecordRepository.save(fine);
        }

        BorrowRecord saved = borrowRecordRepository.save(record);
        operationLogService.record("Borrow management", "Return book", record.getBook().getTitle());
        return saved;
    }

    private boolean isTerminalReturnStatus(BorrowStatus status) {
        return status == BorrowStatus.RETURNED
                || status == BorrowStatus.LOST
                || status == BorrowStatus.DAMAGED;
    }

    @Transactional
    public BorrowRecord renew(Long recordId) {
        BorrowRecord record = borrowRecordRepository.findByIdForUpdate(recordId).orElseThrow();
        dataScopeService.requireAccess(record);
        if (record.getStatus() != BorrowStatus.BORROWED) {
            throw new IllegalStateException(i18n.get("error.borrow.onlyBorrowedRenew"));
        }
        int maxRenewCount = record.getReader().getMemberLevel().getMaxRenewCount();
        if (record.getRenewCount() >= maxRenewCount) {
            throw new IllegalStateException(i18n.get("error.borrow.maxRenew"));
        }
        if (record.getDueDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException(i18n.get("error.borrow.overdueRenew"));
        }
        record.setRenewCount(record.getRenewCount() + 1);
        record.setDueDate(record.getDueDate().plusDays(record.getReader().getMemberLevel().getBorrowDays()));
        BorrowRecord saved = borrowRecordRepository.save(record);
        operationLogService.record("Borrow management", "Renew book", record.getBook().getTitle());
        return saved;
    }

    @Transactional
    public void markOverdueRecords() {
        List<BorrowRecord> records = borrowRecordRepository.findByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now());
        records = records.stream().filter(dataScopeService::canAccess).toList();
        for (BorrowRecord record : records) {
            record.setStatus(BorrowStatus.OVERDUE);
            record.setFineAmount(calculateOverdueFine(record));
        }
        borrowRecordRepository.saveAll(records);
    }

    private boolean validateBorrow(Book book, Reader reader) {
        if (book.isDeleted() || reader.isDeleted()) {
            throw new IllegalStateException(i18n.get("error.borrow.bookOrReaderNotFound"));
        }
        if (book.isReferenceOnly()) {
            throw new IllegalStateException(i18n.get("error.borrow.referenceOnly"));
        }
        if (reader.getStatus() != AccountStatus.NORMAL) {
            throw new IllegalStateException(i18n.get("error.borrow.accountStatus"));
        }
        long activeBorrows = borrowRecordRepository.countByReaderAndStatusInAndDeletedFalse(
                reader, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE));
        if (activeBorrows >= maxBorrowBooks(reader)) {
            throw new IllegalStateException(i18n.get("error.borrow.maxBorrow"));
        }
        if (borrowRecordRepository.existsByReaderAndStatusAndDueDateBeforeAndDeletedFalse(reader, BorrowStatus.BORROWED, LocalDate.now())) {
            throw new IllegalStateException(i18n.get("error.borrow.overdueBook"));
        }
        if (fineRecordRepository.existsByReaderAndStatusAndDeletedFalse(reader, FineStatus.UNPAID)) {
            throw new IllegalStateException(i18n.get("error.borrow.unpaidFine"));
        }
        if (borrowRecordRepository.existsByBookAndReaderAndStatusInAndDeletedFalse(book, reader, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE))) {
            throw new IllegalStateException(i18n.get("error.borrow.duplicateActiveBook"));
        }
        boolean hasLockedReservation = reservationService.hasActiveNotifiedReservation(book, reader);
        if (book.getAvailableQuantity() <= 0 && !hasLockedReservation) {
            throw new IllegalStateException(i18n.get("error.borrow.noInventory"));
        }
        return hasLockedReservation;
    }

    public int maxBorrowBooks(Reader reader) {
        if (reader.getMemberLevel() == MemberLevel.NORMAL) {
            return systemConfigService.normalBorrowLimit();
        }
        return reader.getMemberLevel().getMaxBorrowBooks();
    }

    public BigDecimal estimateFine(BorrowRecord record, boolean damaged, boolean lost) {
        BigDecimal fine = calculateOverdueFine(record);
        if (lost) {
            return fine.add(compensation(record.getBook(), new BigDecimal("2.00")));
        }
        if (damaged) {
            return fine.add(compensation(record.getBook(), new BigDecimal("0.50")));
        }
        return fine;
    }

    private BigDecimal calculateOverdueFine(BorrowRecord record) {
        long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
        if (overdueDays <= 0) {
            return BigDecimal.ZERO;
        }
        return systemConfigService.dailyOverdueFine().multiply(BigDecimal.valueOf(overdueDays));
    }

    private BigDecimal compensation(Book book, BigDecimal multiplier) {
        if (book.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return book.getPrice().multiply(multiplier);
    }

    private Specification<BorrowRecord> borrowSpec(BorrowStatus status, String keyword) {
        Specification<BorrowRecord> spec = dataScopeService.borrowRecordScope();
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.join("book").get("title")), like),
                    builder.like(builder.lower(root.join("book").get("isbn")), like),
                    builder.like(builder.lower(root.join("reader").get("readerNo")), like),
                    builder.like(builder.lower(root.join("reader").get("name")), like)));
        }
        return spec;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : EXPORT_DATE_FORMAT.format(date);
    }
}
