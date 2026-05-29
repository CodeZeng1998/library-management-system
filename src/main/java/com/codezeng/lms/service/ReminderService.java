package com.codezeng.lms.service;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReminderService {

    private final BorrowService borrowService;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final ReaderRepository readerRepository;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;
    private final ReservationService reservationService;
    private final SystemConfigService systemConfigService;
    private final I18nMessageService i18n;

    public ReminderService(
            BorrowService borrowService,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            ReaderRepository readerRepository,
            NotificationService notificationService,
            OperationLogService operationLogService,
            ReservationService reservationService,
            SystemConfigService systemConfigService,
            I18nMessageService i18n) {
        this.borrowService = borrowService;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.readerRepository = readerRepository;
        this.notificationService = notificationService;
        this.operationLogService = operationLogService;
        this.reservationService = reservationService;
        this.systemConfigService = systemConfigService;
        this.i18n = i18n;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void sendDueReminders() {
        runDueReminders();
    }

    public int runDueReminders() {
        if (!systemConfigService.maintenanceEnabled()) {
            return 0;
        }
        int reminderDays = systemConfigService.dueReminderDays();
        LocalDate targetDate = LocalDate.now().plusDays(reminderDays);
        List<BorrowRecord> records = borrowRecordRepository.findByStatusAndDueDate(BorrowStatus.BORROWED, targetDate);
        int sent = 0;
        for (BorrowRecord record : records) {
            if (notificationService.sendOnce(
                    record.getReader(),
                    i18n.get("notification.borrow.due.title"),
                    i18n.get("notification.borrow.due.content", record.getBook().getTitle(), reminderDays, record.getDueDate()))) {
                sent++;
            }
        }
        if (sent > 0) {
            operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.maintenance.dueReminders"),
                    i18n.get("log.maintenance.sentCount", sent));
        }
        return sent;
    }

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendOverdueNotices() {
        runOverdueMaintenance();
    }

    public OverdueMaintenanceResult runOverdueMaintenance() {
        if (!systemConfigService.maintenanceEnabled()) {
            return new OverdueMaintenanceResult(0, 0, 0);
        }
        borrowService.markOverdueRecords();
        List<BorrowRecord> records = borrowRecordRepository.findByStatus(BorrowStatus.OVERDUE);
        int sent = 0;
        int finesCreated = 0;
        int readersFrozen = 0;
        int freezeDays = systemConfigService.overdueFreezeDays();
        for (BorrowRecord record : records) {
            long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
            if (notificationService.sendOnce(
                    record.getReader(),
                    i18n.get("notification.borrow.overdue.title"),
                    i18n.get("notification.borrow.overdue.content", record.getBook().getTitle(), overdueDays, record.getFineAmount()))) {
                sent++;
            }
            if (!fineRecordRepository.existsByBorrowRecordAndStatus(record, FineStatus.UNPAID)
                    && record.getFineAmount().signum() > 0) {
                FineRecord fine = new FineRecord();
                fine.setReader(record.getReader());
                fine.setBorrowRecord(record);
                fine.setReason(i18n.get("fine.reason.overdue"));
                fine.setAmount(record.getFineAmount());
                fine.setStatus(FineStatus.UNPAID);
                fineRecordRepository.save(fine);
                finesCreated++;
            }
            if (overdueDays > freezeDays && record.getReader().getStatus() == AccountStatus.NORMAL) {
                record.getReader().setStatus(AccountStatus.FROZEN);
                readerRepository.save(record.getReader());
                readersFrozen++;
            }
        }
        if (sent > 0 || finesCreated > 0 || readersFrozen > 0) {
            operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.maintenance.overdueMaintenance"),
                    i18n.get("log.maintenance.overdueDetail", sent, finesCreated, readersFrozen));
        }
        return new OverdueMaintenanceResult(sent, finesCreated, readersFrozen);
    }

    @Scheduled(cron = "0 30 9 * * ?")
    @Transactional
    public void releaseExpiredReservations() {
        runReservationMaintenance();
    }

    public int runReservationMaintenance() {
        if (!systemConfigService.maintenanceEnabled()) {
            return 0;
        }
        int releasedCopies = reservationService.expireReservations();
        if (releasedCopies > 0) {
            operationLogService.record(i18n.get("log.module.reservation"), i18n.get("log.maintenance.releaseReservations"),
                    i18n.get("log.maintenance.releasedCopies", releasedCopies));
        }
        return releasedCopies;
    }

    public record OverdueMaintenanceResult(int notificationsSent, int finesCreated, int readersFrozen) {
    }
}
