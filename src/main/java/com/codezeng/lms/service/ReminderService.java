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

    public ReminderService(
            BorrowService borrowService,
            BorrowRecordRepository borrowRecordRepository,
            FineRecordRepository fineRecordRepository,
            ReaderRepository readerRepository,
            NotificationService notificationService,
            OperationLogService operationLogService,
            ReservationService reservationService) {
        this.borrowService = borrowService;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.readerRepository = readerRepository;
        this.notificationService = notificationService;
        this.operationLogService = operationLogService;
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void sendDueReminders() {
        LocalDate targetDate = LocalDate.now().plusDays(3);
        List<BorrowRecord> records = borrowRecordRepository.findByStatusAndDueDate(BorrowStatus.BORROWED, targetDate);
        for (BorrowRecord record : records) {
            notificationService.send(
                    record.getReader(),
                    "图书即将到期",
                    "您借阅的《" + record.getBook().getTitle() + "》将在3天后到期，请及时归还或办理续借。");
        }
        if (!records.isEmpty()) {
            operationLogService.record("消息中心", "发送到期提醒", records.size() + " 条");
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendOverdueNotices() {
        borrowService.markOverdueRecords();
        List<BorrowRecord> records = borrowRecordRepository.findByStatus(BorrowStatus.OVERDUE);
        for (BorrowRecord record : records) {
            long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now());
            notificationService.send(
                    record.getReader(),
                    "图书逾期提醒",
                    "您借阅的《" + record.getBook().getTitle() + "》已逾期" + overdueDays
                            + "天，当前罚款：" + record.getFineAmount() + "元，请尽快归还。");
            if (!fineRecordRepository.existsByBorrowRecordAndStatus(record, FineStatus.UNPAID)
                    && record.getFineAmount().signum() > 0) {
                FineRecord fine = new FineRecord();
                fine.setReader(record.getReader());
                fine.setBorrowRecord(record);
                fine.setReason("逾期未还");
                fine.setAmount(record.getFineAmount());
                fine.setStatus(FineStatus.UNPAID);
                fineRecordRepository.save(fine);
            }
            if (overdueDays > 7 && record.getReader().getStatus() == AccountStatus.NORMAL) {
                record.getReader().setStatus(AccountStatus.FROZEN);
                readerRepository.save(record.getReader());
            }
        }
        if (!records.isEmpty()) {
            operationLogService.record("消息中心", "发送逾期提醒", records.size() + " 条");
        }
    }

    @Scheduled(cron = "0 30 9 * * ?")
    @Transactional
    public void releaseExpiredReservations() {
        int releasedCopies = reservationService.expireReservations();
        if (releasedCopies > 0) {
            operationLogService.record("预约管理", "释放过期到书保留", releasedCopies + " 本");
        }
    }
}
