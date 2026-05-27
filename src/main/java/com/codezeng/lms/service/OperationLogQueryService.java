package com.codezeng.lms.service;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.repository.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
public class OperationLogQueryService {

    private static final int EXPORT_LIMIT = 5000;
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogRepository operationLogRepository;

    public OperationLogQueryService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public Page<OperationLog> search(String keyword, String module, String ip, Pageable pageable) {
        return operationLogRepository.findAll(spec(keyword, module, ip), pageable);
    }

    public String exportCsv(String keyword, String module, String ip) {
        PageRequest page = PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createTime"));
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
