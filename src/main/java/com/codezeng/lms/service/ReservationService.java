package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservationService {

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final OperationLogService operationLogService;
    private final NotificationService notificationService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public ReservationService(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationRecordRepository reservationRecordRepository,
            BorrowRecordRepository borrowRecordRepository,
            OperationLogService operationLogService,
            NotificationService notificationService,
            DataScopeService dataScopeService,
            I18nMessageService i18n,
            SystemConfigService systemConfigService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.operationLogService = operationLogService;
        this.notificationService = notificationService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<ReservationRecord> search(String status, String keyword, int page, int size) {
        int pageSize = normalizePageSize(size);
        return reservationRecordRepository.findAll(
                reservationSpec(status, keyword),
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "reservedAt")));
    }

    public List<Book> selectableBooks() {
        return bookRepository.findAll(dataScopeService.bookScope(), PageRequest.of(0, 200, Sort.by("title"))).getContent();
    }

    public List<Reader> selectableReaders() {
        return readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent();
    }

    public long countByStatuses(List<ReservationStatus> statuses) {
        return reservationRecordRepository.count(statusSpec(statuses));
    }

    public long countExpiringSoon(LocalDateTime now) {
        return reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.NOTIFIED))
                .and((root, query, builder) -> builder.between(root.get("expiresAt"), now, now.plusHours(12))));
    }

    public long countExpiredActive(LocalDateTime now) {
        return reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED))
                .and((root, query, builder) -> builder.lessThan(root.get("expiresAt"), now)));
    }

    public Map<Long, Long> queuePositions(List<ReservationRecord> reservations) {
        Map<Long, Long> positions = new LinkedHashMap<>();
        for (ReservationRecord reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.WAITING) {
                long ahead = reservationRecordRepository.countByBookAndStatusAndReservedAtBeforeAndDeletedFalse(
                        reservation.getBook(), ReservationStatus.WAITING, reservation.getReservedAt());
                positions.put(reservation.getId(), ahead + 1);
            }
        }
        return positions;
    }

    public String exportCsv(String status, String keyword) {
        List<ReservationRecord> reservations = reservationRecordRepository.findAll(
                reservationSpec(status, keyword),
                PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "reservedAt"))).getContent();
        Map<Long, Long> positions = queuePositions(reservations);
        StringBuilder csv = new StringBuilder("\uFEFFBook Title,ISBN,Reader No,Reader Name,Status,Queue Position,Reserved At,Expires At\n");
        for (ReservationRecord reservation : reservations) {
            csv.append(CsvSupport.csv(reservation.getBook().getTitle())).append(',')
                    .append(CsvSupport.csv(reservation.getBook().getIsbn())).append(',')
                    .append(CsvSupport.csv(reservation.getReader().getReaderNo())).append(',')
                    .append(CsvSupport.csv(reservation.getReader().getName())).append(',')
                    .append(CsvSupport.csv(reservation.getStatus().name())).append(',')
                    .append(CsvSupport.csv(String.valueOf(positions.getOrDefault(reservation.getId(), 0L)))).append(',')
                    .append(CsvSupport.csv(formatTime(reservation.getReservedAt()))).append(',')
                    .append(CsvSupport.csv(formatTime(reservation.getExpiresAt()))).append('\n');
        }
        operationLogService.record(i18n.get("log.module.reservation"), "Export reservations", "Rows: " + reservations.size());
        return csv.toString();
    }

    public int normalizePageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    @Transactional
    public ReservationRecord reserve(Long bookId, Long readerId) {
        Book book = bookRepository.findByIdForUpdate(bookId).orElseThrow();
        dataScopeService.requireAccess(book);
        Reader reader = readerRepository.findByIdAndDeletedFalse(readerId).orElseThrow();
        if (book.isDeleted() || reader.isDeleted()) {
            throw new IllegalStateException(i18n.get("error.borrow.bookOrReaderNotFound"));
        }
        if (reader.getStatus() != AccountStatus.NORMAL) {
            throw new IllegalStateException(i18n.get("error.borrow.accountStatus"));
        }
        if (book.getAvailableQuantity() > 0) {
            throw new IllegalStateException(i18n.get("error.reservation.inventoryAvailable"));
        }
        if (book.isReferenceOnly()) {
            throw new IllegalStateException(i18n.get("error.borrow.referenceOnly"));
        }
        if (borrowRecordRepository.existsByBookAndReaderAndStatusInAndDeletedFalse(
                book, reader, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE))) {
            throw new IllegalStateException(i18n.get("error.reservation.alreadyBorrowed"));
        }
        if (reservationRecordRepository.existsByBookAndReaderAndStatusInAndDeletedFalse(
                book, reader, List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED))) {
            throw new IllegalStateException(i18n.get("error.reservation.duplicate"));
        }
        long queueSize = reservationRecordRepository.countByBookAndStatusAndDeletedFalse(book, ReservationStatus.WAITING);
        if (queueSize >= systemConfigService.reservationMaxQueue()) {
            throw new IllegalStateException(i18n.get("error.reservation.queueFull"));
        }
        LocalDateTime now = LocalDateTime.now();
        ReservationRecord record = new ReservationRecord();
        record.setBook(book);
        record.setReader(reader);
        record.setReservedAt(now);
        record.setExpiresAt(now.plusDays(systemConfigService.reservationWaitingHoldDays()));
        record.setStatus(ReservationStatus.WAITING);
        ReservationRecord saved = reservationRecordRepository.save(record);
        operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.reservation.create"),
                book.getTitle() + " -> " + reader.getReaderNo());
        return saved;
    }

    @Transactional
    public Optional<ReservationRecord> lockNextReservation(Book book) {
        List<ReservationRecord> queue = reservationRecordRepository
                .findByBookAndStatusAndDeletedFalseOrderByReservedAtAsc(book, ReservationStatus.WAITING);
        if (queue.isEmpty() || book.getAvailableQuantity() <= 0) {
            return Optional.empty();
        }

        ReservationRecord next = queue.get(0);
        next.setStatus(ReservationStatus.NOTIFIED);
        next.setExpiresAt(LocalDateTime.now().plusHours(systemConfigService.reservationPickupWindowHours()));
        book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        bookRepository.save(book);
        ReservationRecord saved = reservationRecordRepository.save(next);

        notificationService.send(
                next.getReader(),
                i18n.get("notification.reservation.available.title"),
                i18n.get("notification.reservation.available.content", book.getTitle(), displayLocation(book)));
        operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.reservation.notify"),
                book.getTitle() + " -> " + next.getReader().getReaderNo());
        return Optional.of(saved);
    }

    @Transactional
    public Optional<ReservationRecord> completeIfNotified(Book book, Reader reader) {
        Optional<ReservationRecord> reservation = reservationRecordRepository
                .findFirstByBookAndReaderAndStatusAndExpiresAtAfterAndDeletedFalse(book, reader, ReservationStatus.NOTIFIED, LocalDateTime.now());
        reservation.ifPresent(record -> {
            record.setStatus(ReservationStatus.COMPLETED);
            reservationRecordRepository.save(record);
            operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.reservation.complete"),
                    book.getTitle() + " -> " + reader.getReaderNo());
        });
        return reservation;
    }

    public boolean hasActiveNotifiedReservation(Book book, Reader reader) {
        return reservationRecordRepository
                .findFirstByBookAndReaderAndStatusAndExpiresAtAfterAndDeletedFalse(book, reader, ReservationStatus.NOTIFIED, LocalDateTime.now())
                .isPresent();
    }

    @Transactional
    public int expireReservations() {
        List<ReservationRecord> expired = reservationRecordRepository.findByStatusInAndExpiresAtBeforeAndDeletedFalse(
                List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED), LocalDateTime.now());
        int releasedCopies = 0;
        for (ReservationRecord record : expired) {
            boolean releaseLockedCopy = record.getStatus() == ReservationStatus.NOTIFIED;
            record.setStatus(ReservationStatus.EXPIRED);
            reservationRecordRepository.save(record);
            notificationService.send(
                    record.getReader(),
                    i18n.get("notification.reservation.expired.title"),
                    i18n.get("notification.reservation.expired.content", record.getBook().getTitle()));
            if (releaseLockedCopy) {
                Book book = record.getBook();
                book.setAvailableQuantity(Math.min(book.getAvailableQuantity() + 1, book.getTotalQuantity()));
                bookRepository.save(book);
                releasedCopies++;
                lockNextReservation(book);
            }
        }
        if (!expired.isEmpty()) {
            operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.reservation.expire"),
                    String.valueOf(expired.size()));
        }
        return releasedCopies;
    }

    @Transactional
    public void cancel(Long id) {
        ReservationRecord record = reservationRecordRepository.findByIdForUpdate(id).orElseThrow();
        dataScopeService.requireAccess(record);
        if (record.getStatus() != ReservationStatus.WAITING && record.getStatus() != ReservationStatus.NOTIFIED) {
            return;
        }
        boolean releaseLockedCopy = record.getStatus() == ReservationStatus.NOTIFIED;
        record.setStatus(ReservationStatus.CANCELLED);
        reservationRecordRepository.save(record);
        if (releaseLockedCopy) {
            Book book = record.getBook();
            book.setAvailableQuantity(Math.min(book.getAvailableQuantity() + 1, book.getTotalQuantity()));
            bookRepository.save(book);
            lockNextReservation(book);
        }
        operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.reservation.cancel"),
                record.getBook().getTitle());
    }

    private String displayLocation(Book book) {
        return book.getLocation() == null || book.getLocation().isBlank()
                ? i18n.get("reservation.location.serviceDesk")
                : book.getLocation();
    }

    private Specification<ReservationRecord> reservationSpec(String status, String keyword) {
        Specification<ReservationRecord> spec = dataScopeService.reservationScope();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            spec = spec.and(statusSpec(List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED)));
        } else if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            ReservationStatus selected = parseStatus(status);
            if (selected != null) {
                spec = spec.and(statusSpec(List.of(selected)));
            }
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

    private Specification<ReservationRecord> statusSpec(List<ReservationStatus> statuses) {
        return dataScopeService.reservationScope()
                .and((root, query, builder) -> root.get("status").in(statuses));
    }

    private ReservationStatus parseStatus(String value) {
        try {
            return ReservationStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : EXPORT_TIME_FORMAT.format(time);
    }
}
