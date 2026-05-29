package com.codezeng.lms.repository;

import com.codezeng.lms.domain.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    Page<OperationLog> findByDeletedFalse(Pageable pageable);

    List<OperationLog> findTop1000ByDeletedFalseAndCreateTimeBeforeOrderByCreateTimeAsc(LocalDateTime cutoff);
}
