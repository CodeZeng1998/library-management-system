package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BorrowService {

    private static final BigDecimal OVERDUE_FINE_PER_DAY = new BigDecimal("0.10");

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final OperationLogService operationLogService;
    private final ReservationService reservationService;

    public BorrowService(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            OperationLogService operationLogService,
            ReservationService reservationService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.operationLogService = operationLogService;
        this.reservationService = reservationService;
    }

    @Transactional
    public BorrowRecord borrowBook(Long bookId, Long readerId) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        Reader reader = readerRepository.findById(readerId).orElseThrow();
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
        operationLogService.record("借阅管理", "借出图书", book.getTitle() + " -> " + reader.getReaderNo());
        return saved;
    }

    @Transactional
    public BorrowRecord returnBook(Long recordId, boolean damaged, boolean lost) {
        BorrowRecord record = borrowRecordRepository.findById(recordId).orElseThrow();
        if (record.getStatus() == BorrowStatus.RETURNED) {
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
            fine.setReason(lost ? "图书丢失" : damaged ? "图书损坏" : "逾期归还");
            fine.setAmount(fineAmount);
            fine.setStatus(FineStatus.UNPAID);
            fineRecordRepository.save(fine);
        }

        BorrowRecord saved = borrowRecordRepository.save(record);
        operationLogService.record("借阅管理", "归还图书", record.getBook().getTitle());
        return saved;
    }

    @Transactional
    public BorrowRecord renew(Long recordId) {
        BorrowRecord record = borrowRecordRepository.findById(recordId).orElseThrow();
        if (record.getStatus() != BorrowStatus.BORROWED) {
            throw new IllegalStateException("只有借阅中的记录可以续借");
        }
        int maxRenewCount = record.getReader().getMemberLevel().getMaxRenewCount();
        if (record.getRenewCount() >= maxRenewCount) {
            throw new IllegalStateException("已达到最大续借次数");
        }
        if (record.getDueDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("逾期记录不能续借");
        }
        record.setRenewCount(record.getRenewCount() + 1);
        record.setDueDate(record.getDueDate().plusDays(record.getReader().getMemberLevel().getBorrowDays()));
        BorrowRecord saved = borrowRecordRepository.save(record);
        operationLogService.record("借阅管理", "续借图书", record.getBook().getTitle());
        return saved;
    }

    @Transactional
    public void markOverdueRecords() {
        List<BorrowRecord> records = borrowRecordRepository.findByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now());
        for (BorrowRecord record : records) {
            record.setStatus(BorrowStatus.OVERDUE);
            record.setFineAmount(calculateOverdueFine(record));
        }
        borrowRecordRepository.saveAll(records);
    }

    private boolean validateBorrow(Book book, Reader reader) {
        if (book.isDeleted() || reader.isDeleted()) {
            throw new IllegalStateException("图书或读者不存在");
        }
        if (book.isReferenceOnly()) {
            throw new IllegalStateException("该图书仅限馆内阅读");
        }
        if (reader.getStatus() != AccountStatus.NORMAL) {
            throw new IllegalStateException("读者账号状态不允许借阅");
        }
        long activeBorrows = borrowRecordRepository.countByReaderAndStatusIn(
                reader, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE));
        if (activeBorrows >= reader.getMemberLevel().getMaxBorrowBooks()) {
            throw new IllegalStateException("已达到借阅上限");
        }
        if (borrowRecordRepository.existsByReaderAndStatusAndDueDateBefore(reader, BorrowStatus.BORROWED, LocalDate.now())) {
            throw new IllegalStateException("存在逾期未还图书");
        }
        if (fineRecordRepository.existsByReaderAndStatus(reader, FineStatus.UNPAID)) {
            throw new IllegalStateException("存在未缴纳罚款");
        }
        boolean hasLockedReservation = reservationService.hasActiveNotifiedReservation(book, reader);
        if (book.getAvailableQuantity() <= 0 && !hasLockedReservation) {
            throw new IllegalStateException("库存不足，请先预约");
        }
        return hasLockedReservation;
    }

    private BigDecimal calculateOverdueFine(BorrowRecord record) {
        long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
        if (overdueDays <= 0) {
            return BigDecimal.ZERO;
        }
        return OVERDUE_FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays));
    }

    private BigDecimal compensation(Book book, BigDecimal multiplier) {
        if (book.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return book.getPrice().multiply(multiplier);
    }
}
