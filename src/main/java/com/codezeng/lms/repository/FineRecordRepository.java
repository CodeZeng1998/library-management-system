package com.codezeng.lms.repository;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.FineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface FineRecordRepository extends JpaRepository<FineRecord, Long>, JpaSpecificationExecutor<FineRecord> {

    boolean existsByReaderAndStatus(Reader reader, FineStatus status);

    List<FineRecord> findByReaderAndDeletedFalseOrderByCreateTimeDesc(Reader reader);

    boolean existsByBorrowRecordAndStatus(BorrowRecord borrowRecord, FineStatus status);

    Page<FineRecord> findByDeletedFalse(Pageable pageable);
}
