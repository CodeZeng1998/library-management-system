package com.codezeng.lms.service;

import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.repository.SystemConfigRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SystemConfigService {

    private static final int MAX_VALUE_LENGTH = 500;

    private final SystemConfigRepository systemConfigRepository;
    private final OperationLogService operationLogService;

    public SystemConfigService(SystemConfigRepository systemConfigRepository,
                               OperationLogService operationLogService) {
        this.systemConfigRepository = systemConfigRepository;
        this.operationLogService = operationLogService;
    }

    public List<SystemConfig> search(String keyword) {
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            return systemConfigRepository
                    .findByConfigKeyContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByConfigKey(
                            value, value, value);
        }
        return systemConfigRepository.findAll(Sort.by("configKey"));
    }

    public Map<String, String> validationHints() {
        return Map.of(
                "borrow.normal.max_books", "Integer, 1-100",
                "fine.overdue.per_day", "Amount, 0-9999.99",
                "reservation.max_queue", "Integer, 1-1000"
        );
    }

    @Transactional
    public SystemConfig update(Long id, String rawValue) {
        SystemConfig config = systemConfigRepository.findById(id).orElseThrow();
        String newValue = validate(config.getConfigKey(), rawValue);
        String oldValue = config.getConfigValue();
        if (oldValue.equals(newValue)) {
            return config;
        }
        config.setConfigValue(newValue);
        SystemConfig saved = systemConfigRepository.save(config);
        operationLogService.record("System config", "Update config", config.getConfigKey() + ": " + oldValue + " -> " + newValue);
        return saved;
    }

    private String validate(String key, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new IllegalArgumentException("Configuration value cannot be empty.");
        }
        String value = rawValue.trim();
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Configuration value is too long.");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Configuration value contains invalid control characters.");
        }
        return switch (key) {
            case "borrow.normal.max_books" -> integerRange(value, 1, 100, "Borrow limit");
            case "reservation.max_queue" -> integerRange(value, 1, 1000, "Reservation queue limit");
            case "fine.overdue.per_day" -> moneyRange(value, new BigDecimal("0"), new BigDecimal("9999.99"), "Daily overdue fine");
            default -> value;
        };
    }

    private String integerRange(String value, int min, int max, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return String.valueOf(parsed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
    }

    private String moneyRange(String value, BigDecimal min, BigDecimal max, String label) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(min) < 0 || parsed.compareTo(max) > 0) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            if (parsed.scale() > 2) {
                throw new IllegalArgumentException(label + " supports up to 2 decimal places.");
            }
            return parsed.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a valid amount.");
        }
    }
}
