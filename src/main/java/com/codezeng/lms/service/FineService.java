package com.codezeng.lms.service;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FineService {

    private final FineRecordRepository fineRecordRepository;
    private final OperationLogService operationLogService;
    private final DataScopeService dataScopeService;

    public FineService(FineRecordRepository fineRecordRepository,
                       OperationLogService operationLogService,
                       DataScopeService dataScopeService) {
        this.fineRecordRepository = fineRecordRepository;
        this.operationLogService = operationLogService;
        this.dataScopeService = dataScopeService;
    }

    @Transactional
    public void pay(Long id) {
        FineRecord fine = fineRecordRepository.findById(id).orElseThrow();
        dataScopeService.requireAccess(fine);
        if (fine.getStatus() != FineStatus.UNPAID) {
            return;
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());
        fineRecordRepository.save(fine);
        operationLogService.record("罚款管理", "缴纳罚款", fine.getReader().getReaderNo() + " " + fine.getAmount());
    }

    @Transactional
    public void waive(Long id) {
        FineRecord fine = fineRecordRepository.findById(id).orElseThrow();
        dataScopeService.requireAccess(fine);
        if (fine.getStatus() != FineStatus.UNPAID) {
            return;
        }
        fine.setStatus(FineStatus.WAIVED);
        fineRecordRepository.save(fine);
        operationLogService.record("罚款管理", "减免罚款", fine.getReader().getReaderNo() + " " + fine.getAmount());
    }
}
