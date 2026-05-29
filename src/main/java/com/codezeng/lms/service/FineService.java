package com.codezeng.lms.service;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FineService {

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FineRecordRepository fineRecordRepository;
    private final OperationLogService operationLogService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public FineService(FineRecordRepository fineRecordRepository,
                       OperationLogService operationLogService,
                       DataScopeService dataScopeService,
                       I18nMessageService i18n,
                       SystemConfigService systemConfigService) {
        this.fineRecordRepository = fineRecordRepository;
        this.operationLogService = operationLogService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<FineRecord> search(FineStatus status, String keyword, int page, int size) {
        int pageSize = normalizePageSize(size);
        return fineRecordRepository.findAll(
                fineSpec(status, keyword),
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    public long countByStatus(FineStatus status) {
        return fineRecordRepository.count(statusSpec(status));
    }

    public BigDecimal totalUnpaidAmount() {
        return fineRecordRepository.sumAmountByStatus(FineStatus.UNPAID);
    }

    @Transactional
    public void pay(Long id) {
        FineRecord fine = fineRecordRepository.findByIdForUpdate(id).orElseThrow();
        dataScopeService.requireAccess(fine);
        if (fine.getStatus() != FineStatus.UNPAID) {
            return;
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());
        fineRecordRepository.save(fine);
        operationLogService.record(i18n.get("log.module.fine"), i18n.get("log.fine.pay"),
                fine.getReader().getReaderNo() + " " + fine.getAmount());
    }

    @Transactional
    public void waive(Long id) {
        FineRecord fine = fineRecordRepository.findByIdForUpdate(id).orElseThrow();
        dataScopeService.requireAccess(fine);
        if (fine.getStatus() != FineStatus.UNPAID) {
            return;
        }
        fine.setStatus(FineStatus.WAIVED);
        fineRecordRepository.save(fine);
        operationLogService.record(i18n.get("log.module.fine"), i18n.get("log.fine.waive"),
                fine.getReader().getReaderNo() + " " + fine.getAmount());
    }

    public String exportCsv(FineStatus status, String keyword) {
        StringBuilder csv = new StringBuilder("\uFEFFReader No,Reader Name,Book Title,ISBN,Reason,Amount,Status,Paid At,Created At\n");
        List<FineRecord> fines = fineRecordRepository.findAll(
                fineSpec(status, keyword),
                PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "createTime"))).getContent();
        for (FineRecord fine : fines) {
            String bookTitle = fine.getBorrowRecord() == null || fine.getBorrowRecord().getBook() == null
                    ? "" : fine.getBorrowRecord().getBook().getTitle();
            String isbn = fine.getBorrowRecord() == null || fine.getBorrowRecord().getBook() == null
                    ? "" : fine.getBorrowRecord().getBook().getIsbn();
            csv.append(CsvSupport.csv(fine.getReader().getReaderNo())).append(',')
                    .append(CsvSupport.csv(fine.getReader().getName())).append(',')
                    .append(CsvSupport.csv(bookTitle)).append(',')
                    .append(CsvSupport.csv(isbn)).append(',')
                    .append(CsvSupport.csv(fine.getReason())).append(',')
                    .append(CsvSupport.csv(String.valueOf(fine.getAmount()))).append(',')
                    .append(CsvSupport.csv(fine.getStatus().name())).append(',')
                    .append(CsvSupport.csv(formatTime(fine.getPaidAt()))).append(',')
                    .append(CsvSupport.csv(formatTime(fine.getCreateTime()))).append('\n');
        }
        operationLogService.record(i18n.get("log.module.fine"), "Export fines", "Rows: " + fines.size());
        return csv.toString();
    }

    public int normalizePageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private Specification<FineRecord> fineSpec(FineStatus status, String keyword) {
        Specification<FineRecord> spec = dataScopeService.fineScope();
        if (status != null) {
            spec = spec.and(statusSpec(status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("reason")), like),
                    builder.like(builder.lower(root.join("reader").get("readerNo")), like),
                    builder.like(builder.lower(root.join("reader").get("name")), like),
                    builder.like(builder.lower(root.join("borrowRecord").join("book").get("title")), like)));
        }
        return spec;
    }

    private Specification<FineRecord> statusSpec(FineStatus status) {
        return dataScopeService.fineScope()
                .and((root, query, builder) -> builder.equal(root.get("status"), status));
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : EXPORT_TIME_FORMAT.format(time);
    }
}
