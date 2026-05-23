package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationService {

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final OperationLogService operationLogService;
    private final NotificationService notificationService;
    private final I18nMessageService i18n;

    public ReservationService(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationRecordRepository reservationRecordRepository,
            OperationLogService operationLogService,
            NotificationService notificationService,
            I18nMessageService i18n) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.operationLogService = operationLogService;
        this.notificationService = notificationService;
        this.i18n = i18n;
    }

    @Transactional
    public ReservationRecord reserve(Long bookId, Long readerId) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        Reader reader = readerRepository.findById(readerId).orElseThrow();
        if (book.getAvailableQuantity() > 0) {
            throw new IllegalStateException(i18n.get("error.reservation.inventoryAvailable"));
        }
        long queueSize = reservationRecordRepository.countByBookAndStatus(book, ReservationStatus.WAITING);
        if (queueSize >= 5) {
            throw new IllegalStateException(i18n.get("error.reservation.queueFull"));
        }
        ReservationRecord record = new ReservationRecord();
        record.setBook(book);
        record.setReader(reader);
        record.setReservedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusDays(3));
        record.setStatus(ReservationStatus.WAITING);
        ReservationRecord saved = reservationRecordRepository.save(record);
        operationLogService.record("预约管理", "创建预约", book.getTitle() + " -> " + reader.getReaderNo());
        return saved;
    }

    @Transactional
    public Optional<ReservationRecord> lockNextReservation(Book book) {
        List<ReservationRecord> queue = reservationRecordRepository
                .findByBookAndStatusOrderByReservedAtAsc(book, ReservationStatus.WAITING);
        if (queue.isEmpty() || book.getAvailableQuantity() <= 0) {
            return Optional.empty();
        }

        ReservationRecord next = queue.get(0);
        next.setStatus(ReservationStatus.NOTIFIED);
        next.setExpiresAt(LocalDateTime.now().plusHours(48));
        book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        bookRepository.save(book);
        ReservationRecord saved = reservationRecordRepository.save(next);

        notificationService.send(
                next.getReader(),
                "预约图书已到馆",
                "您预约的《" + book.getTitle() + "》已到馆，请在48小时内到馆取书。馆藏位置：" + displayLocation(book));
        operationLogService.record("预约管理", "通知到书", book.getTitle() + " -> " + next.getReader().getReaderNo());
        return Optional.of(saved);
    }

    @Transactional
    public Optional<ReservationRecord> completeIfNotified(Book book, Reader reader) {
        Optional<ReservationRecord> reservation = reservationRecordRepository
                .findFirstByBookAndReaderAndStatusAndExpiresAtAfter(book, reader, ReservationStatus.NOTIFIED, LocalDateTime.now());
        reservation.ifPresent(record -> {
            record.setStatus(ReservationStatus.COMPLETED);
            reservationRecordRepository.save(record);
            operationLogService.record("预约管理", "预约转借阅", book.getTitle() + " -> " + reader.getReaderNo());
        });
        return reservation;
    }

    public boolean hasActiveNotifiedReservation(Book book, Reader reader) {
        return reservationRecordRepository
                .findFirstByBookAndReaderAndStatusAndExpiresAtAfter(book, reader, ReservationStatus.NOTIFIED, LocalDateTime.now())
                .isPresent();
    }

    @Transactional
    public int expireReservations() {
        List<ReservationRecord> expired = reservationRecordRepository.findByStatusInAndExpiresAtBefore(
                List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED), LocalDateTime.now());
        int releasedCopies = 0;
        for (ReservationRecord record : expired) {
            boolean releaseLockedCopy = record.getStatus() == ReservationStatus.NOTIFIED;
            record.setStatus(ReservationStatus.EXPIRED);
            reservationRecordRepository.save(record);
            notificationService.send(
                    record.getReader(),
                    "预约已过期",
                    "您预约的《" + record.getBook().getTitle() + "》已过期，如仍需借阅请重新预约。");
            if (releaseLockedCopy) {
                Book book = record.getBook();
                book.setAvailableQuantity(Math.min(book.getAvailableQuantity() + 1, book.getTotalQuantity()));
                bookRepository.save(book);
                releasedCopies++;
                lockNextReservation(book);
            }
        }
        if (!expired.isEmpty()) {
            operationLogService.record("预约管理", "清理过期预约", expired.size() + " 条");
        }
        return releasedCopies;
    }

    @Transactional
    public void cancel(Long id) {
        ReservationRecord record = reservationRecordRepository.findById(id).orElseThrow();
        record.setStatus(ReservationStatus.CANCELLED);
        reservationRecordRepository.save(record);
        operationLogService.record("预约管理", "取消预约", record.getBook().getTitle());
    }

    private String displayLocation(Book book) {
        return book.getLocation() == null || book.getLocation().isBlank() ? "请咨询服务台" : book.getLocation();
    }
}
