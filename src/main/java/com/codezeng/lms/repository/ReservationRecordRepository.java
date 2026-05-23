package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, Long> {

    long countByBookAndStatus(Book book, ReservationStatus status);

    Page<ReservationRecord> findByDeletedFalse(Pageable pageable);
}
