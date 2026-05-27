package com.codezeng.lms;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.repository.OperationLogRepository;
import com.codezeng.lms.repository.SystemConfigRepository;
import com.codezeng.lms.service.OperationLogQueryService;
import com.codezeng.lms.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SystemInfrastructureTests {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private OperationLogQueryService operationLogQueryService;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Test
    void rejectsInvalidBorrowLimitConfig() {
        SystemConfig config = systemConfigRepository.findByConfigKey("borrow.normal.max_books").orElseThrow();

        assertThatThrownBy(() -> systemConfigService.update(config.getId(), "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Borrow limit");
    }

    @Test
    void normalizesValidMoneyConfig() {
        SystemConfig config = systemConfigRepository.findByConfigKey("fine.overdue.per_day").orElseThrow();

        systemConfigService.update(config.getId(), "0.50");

        assertThat(systemConfigRepository.findById(config.getId()).orElseThrow().getConfigValue()).isEqualTo("0.5");
    }

    @Test
    void exportsFilteredOperationLogs() {
        operationLogRepository.save(new OperationLog("auditor", "Risk", "Review", "Export target", "127.0.0.1"));
        operationLogRepository.save(new OperationLog("auditor", "Other", "Review", "Should be filtered", "127.0.0.1"));

        String csv = operationLogQueryService.exportCsv("target", "Risk", "127.0.0.1");

        assertThat(csv).contains("Time,User,Module,Operation,IP,Detail");
        assertThat(csv).contains("Export target");
        assertThat(csv).doesNotContain("Should be filtered");
    }
}
