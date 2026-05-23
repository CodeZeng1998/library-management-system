package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.BorrowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    long countByReaderAndStatusIn(Reader reader, Collection<BorrowStatus> statuses);

    boolean existsByReaderAndStatusAndDueDateBefore(Reader reader, BorrowStatus status, LocalDate date);

    long countByStatusIn(Collection<BorrowStatus> statuses);

    Page<BorrowRecord> findByDeletedFalse(Pageable pageable);

    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowStatus status, LocalDate date);
}
