package com.codezeng.lms.service;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.repository.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OperationLogQueryService {

    private static final int CLEANUP_BATCH_SIZE = 1000;
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogRepository operationLogRepository;
    private final OperationLogService operationLogService;
    private final SystemConfigService systemConfigService;

    public OperationLogQueryService(OperationLogRepository operationLogRepository,
                                    OperationLogService operationLogService,
                                    SystemConfigService systemConfigService) {
        this.operationLogRepository = operationLogRepository;
        this.operationLogService = operationLogService;
        this.systemConfigService = systemConfigService;
    }

    public Page<OperationLog> search(String keyword, String module, String ip, Pageable pageable) {
        return operationLogRepository.findAll(spec(keyword, module, ip), pageable);
    }

    public Page<OperationLog> search(String keyword, String module, String ip, int page, int size) {
        return search(keyword, module, ip,
                PageRequest.of(Math.max(page, 0), normalizePageSize(size), Sort.by(Sort.Direction.DESC, "createTime")));
    }

    public String exportCsv(String keyword, String module, String ip) {
        PageRequest page = PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "createTime"));
        StringBuilder csv = new StringBuilder("\uFEFFTime,User,Module,Operation,IP,Detail\n");
        for (OperationLog log : search(keyword, module, ip, page).getContent()) {
            csv.append(CsvSupport.csv(log.getCreateTime() == null ? "" : EXPORT_TIME_FORMAT.format(log.getCreateTime()))).append(',')
                    .append(CsvSupport.csv(log.getUsername())).append(',')
                    .append(CsvSupport.csv(log.getModuleName())).append(',')
                    .append(CsvSupport.csv(log.getOperation())).append(',')
                    .append(CsvSupport.csv(log.getIpAddress())).append(',')
                    .append(CsvSupport.csv(log.getDetail())).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public int cleanupOlderThan(int retentionDays) {
        if (retentionDays < 30 || retentionDays > 3650) {
            throw new IllegalArgumentException("Log retention days must be between 30 and 3650.");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        List<OperationLog> batch;
        do {
            batch = operationLogRepository.findTop1000ByDeletedFalseAndCreateTimeBeforeOrderByCreateTimeAsc(cutoff);
            for (OperationLog log : batch) {
                log.setDeleted(true);
            }
            operationLogRepository.saveAll(batch);
            total += batch.size();
        } while (batch.size() == CLEANUP_BATCH_SIZE);
        operationLogService.record("Operation logs", "Cleanup logs", "Retention days: " + retentionDays + ", removed: " + total);
        return total;
    }

    public int normalizePageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private Specification<OperationLog> spec(String keyword, String module, String ip) {
        Specification<OperationLog> spec = (root, query, builder) -> builder.isFalse(root.get("deleted"));
        if (StringUtils.hasText(module)) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("moduleName"), module.trim()));
        }
        if (StringUtils.hasText(ip)) {
            String like = "%" + ip.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.like(builder.lower(root.get("ipAddress")), like));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("username")), like),
                    builder.like(builder.lower(root.get("moduleName")), like),
                    builder.like(builder.lower(root.get("operation")), like),
                    builder.like(builder.lower(root.get("detail")), like)));
        }
        return spec;
    }
}
