package com.codezeng.lms.service;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public void record(String moduleName, String operation, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "system" : authentication.getName();
        operationLogRepository.save(new OperationLog(username, moduleName, operation, detail, clientIp()));
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            return request.getRemoteAddr();
        }
        return "system";
    }
}
