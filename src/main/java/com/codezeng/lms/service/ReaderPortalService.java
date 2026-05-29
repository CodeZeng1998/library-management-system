package com.codezeng.lms.service;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ReaderPortalService {

    private static final List<BorrowStatus> ACTIVE_STATUSES = List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE);
    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final NotificationRepository notificationRepository;
    private final RecommendationService recommendationService;
    private final OperationLogService operationLogService;

    public ReaderPortalService(UserRepository userRepository,
                               ReaderRepository readerRepository,
                               BorrowRecordRepository borrowRecordRepository,
                               ReservationRecordRepository reservationRecordRepository,
                               FineRecordRepository fineRecordRepository,
                               NotificationRepository notificationRepository,
                               RecommendationService recommendationService,
                               OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.notificationRepository = notificationRepository;
        this.recommendationService = recommendationService;
        this.operationLogService = operationLogService;
    }

    public Reader requireCurrentReader() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Reader portal requires login.");
        }
        String username = authentication.getName();
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new AccessDeniedException("Current user was not found."));

        if (StringUtils.hasText(user.getReaderNo())) {
            return readerRepository.findByReaderNoAndDeletedFalse(user.getReaderNo())
                    .orElseThrow(() -> new AccessDeniedException("No reader profile is bound to this account."));
        }
        return readerRepository.findByEmailAndDeletedFalse(user.getEmail())
                .or(() -> readerRepository.findByReaderNoAndDeletedFalse(user.getUsername()))
                .orElseThrow(() -> new AccessDeniedException("No reader profile is bound to this account."));
    }

    public ReaderPortalDashboard dashboard() {
        Reader reader = requireCurrentReader();
        List<BorrowRecord> activeBorrows = borrowRecordRepository
                .findByReaderAndStatusInAndDeletedFalseOrderByDueDateAsc(reader, ACTIVE_STATUSES);
        List<BorrowRecord> history = borrowRecordRepository.findByReaderAndDeletedFalseOrderByBorrowDateDesc(reader)
                .stream()
                .filter(record -> !ACTIVE_STATUSES.contains(record.getStatus()))
                .limit(8)
                .toList();
        List<ReservationView> reservations = reservationRecordRepository
                .findByReaderAndDeletedFalseOrderByReservedAtDesc(reader)
                .stream()
                .filter(record -> ACTIVE_RESERVATION_STATUSES.contains(record.getStatus()))
                .map(this::toReservationView)
                .toList();
        List<FineRecord> fines = fineRecordRepository.findByReaderAndDeletedFalseOrderByCreateTimeDesc(reader);
        List<Notification> notifications = notificationRepository
                .findByReaderAndDeletedFalse(reader, PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "sentAt")))
                .getContent();
        RecommendationService.RecommendationDashboard recommendations = recommendationService.dashboard(reader.getId(), null);

        BigDecimal unpaidFineAmount = fines.stream()
                .filter(fine -> fine.getStatus() == FineStatus.UNPAID)
                .map(FineRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReaderPortalDashboard(
                reader,
                activeBorrows,
                history,
                reservations,
                fines,
                notifications,
                recommendations.collaborative().stream().limit(4).toList(),
                activeBorrows.stream().filter(this::isOverdue).count(),
                unpaidFineAmount,
                notificationRepository.countByReaderAndStatusAndDeletedFalse(reader, NotificationStatus.UNREAD));
    }

    public String exportActivityCsv() {
        Reader reader = requireCurrentReader();
        List<ActivityRow> rows = new ArrayList<>();

        for (BorrowRecord record : borrowRecordRepository.findByReaderAndDeletedFalseOrderByBorrowDateDesc(reader)) {
            rows.add(new ActivityRow(
                    record.getBorrowDate().atStartOfDay(),
                    "Borrow",
                    record.getStatus().name(),
                    record.getBook().getTitle(),
                    record.getBook().getIsbn(),
                    "Borrowed " + formatDate(record.getBorrowDate())
                            + ", due " + formatDate(record.getDueDate())
                            + ", returned " + formatDate(record.getReturnDate())
                            + ", fine " + record.getFineAmount()));
        }
        for (ReservationRecord record : reservationRecordRepository.findByReaderAndDeletedFalseOrderByReservedAtDesc(reader)) {
            rows.add(new ActivityRow(
                    record.getReservedAt(),
                    "Reservation",
                    record.getStatus().name(),
                    record.getBook().getTitle(),
                    record.getBook().getIsbn(),
                    "Reserved " + formatTime(record.getReservedAt())
                            + ", expires " + formatTime(record.getExpiresAt())));
        }
        for (FineRecord fine : fineRecordRepository.findByReaderAndDeletedFalseOrderByCreateTimeDesc(reader)) {
            BorrowRecord borrowRecord = fine.getBorrowRecord();
            rows.add(new ActivityRow(
                    fine.getCreateTime(),
                    "Fine",
                    fine.getStatus().name(),
                    borrowRecord == null ? "" : borrowRecord.getBook().getTitle(),
                    borrowRecord == null ? "" : borrowRecord.getBook().getIsbn(),
                    fine.getReason() + ", amount " + fine.getAmount() + ", paid " + formatTime(fine.getPaidAt())));
        }
        for (Notification notification : notificationRepository
                .findByReaderAndDeletedFalse(reader, PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "sentAt")))
                .getContent()) {
            rows.add(new ActivityRow(
                    notification.getSentAt(),
                    "Notification",
                    notification.getStatus().name(),
                    notification.getTitle(),
                    "",
                    notification.getContent()));
        }

        rows.sort(Comparator.comparing(ActivityRow::time, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        StringBuilder csv = new StringBuilder("\uFEFFTime,Type,Status,Title,ISBN,Detail\n");
        for (ActivityRow row : rows) {
            csv.append(CsvSupport.csv(formatTime(row.time()))).append(',')
                    .append(CsvSupport.csv(row.type())).append(',')
                    .append(CsvSupport.csv(row.status())).append(',')
                    .append(CsvSupport.csv(row.title())).append(',')
                    .append(CsvSupport.csv(row.isbn())).append(',')
                    .append(CsvSupport.csv(row.detail())).append('\n');
        }
        operationLogService.record("Reader portal", "Export personal activity", reader.getReaderNo() + " rows: " + rows.size());
        return csv.toString();
    }

    public void requireOwnedBorrow(Long recordId) {
        BorrowRecord record = borrowRecordRepository.findByIdAndDeletedFalse(recordId).orElseThrow();
        requireOwner(record.getReader());
    }

    public void requireOwnedReservation(Long reservationId) {
        ReservationRecord record = reservationRecordRepository.findByIdAndDeletedFalse(reservationId).orElseThrow();
        requireOwner(record.getReader());
    }

    public void requireOwnedFine(Long fineId) {
        FineRecord fine = fineRecordRepository.findByIdForUpdate(fineId).orElseThrow();
        requireOwner(fine.getReader());
    }

    public void requireOwnedNotification(Long notificationId) {
        Notification notification = notificationRepository.findByIdAndDeletedFalse(notificationId).orElseThrow();
        requireOwner(notification.getReader());
    }

    private void requireOwner(Reader owner) {
        Reader currentReader = requireCurrentReader();
        if (owner == null || !Objects.equals(owner.getId(), currentReader.getId())) {
            throw new AccessDeniedException("This record does not belong to the current reader.");
        }
    }

    private ReservationView toReservationView(ReservationRecord record) {
        int position = 0;
        if (record.getStatus() == ReservationStatus.WAITING) {
            position = Math.toIntExact(reservationRecordRepository.countByBookAndStatusAndReservedAtBeforeAndDeletedFalse(
                    record.getBook(), ReservationStatus.WAITING, record.getReservedAt()) + 1);
        }
        return new ReservationView(record, position);
    }

    private boolean isOverdue(BorrowRecord record) {
        return record.getStatus() == BorrowStatus.OVERDUE
                || (record.getStatus() == BorrowStatus.BORROWED && record.getDueDate().isBefore(LocalDate.now()));
    }

    public long daysUntilDue(BorrowRecord record) {
        return ChronoUnit.DAYS.between(LocalDate.now(), record.getDueDate());
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }

    public record ReaderPortalDashboard(Reader reader,
                                        List<BorrowRecord> activeBorrows,
                                        List<BorrowRecord> history,
                                        List<ReservationView> reservations,
                                        List<FineRecord> fines,
                                        List<Notification> notifications,
                                        List<RecommendationService.BookRecommendation> recommendations,
                                        long overdueCount,
                                        BigDecimal unpaidFineAmount,
                                        long unreadCount) {
    }

    public record ReservationView(ReservationRecord record, int queuePosition) {
    }

    private record ActivityRow(LocalDateTime time, String type, String status, String title, String isbn, String detail) {
    }
}
