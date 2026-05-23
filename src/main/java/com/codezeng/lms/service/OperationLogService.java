package com.codezeng.lms.service;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.repository.OperationLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public void record(String moduleName, String operation, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "system" : authentication.getName();
        operationLogRepository.save(new OperationLog(username, moduleName, operation, detail));
    }
}
