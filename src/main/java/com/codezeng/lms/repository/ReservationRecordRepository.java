package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, Long> {

    long countByBookAndStatus(Book book, ReservationStatus status);

    List<ReservationRecord> findByBookAndStatusOrderByReservedAtAsc(Book book, ReservationStatus status);

    Optional<ReservationRecord> findFirstByBookAndReaderAndStatusAndExpiresAtAfter(
            Book book, com.codezeng.lms.domain.Reader reader, ReservationStatus status, LocalDateTime dateTime);

    List<ReservationRecord> findByStatusInAndExpiresAtBefore(Collection<ReservationStatus> statuses, LocalDateTime dateTime);

    Page<ReservationRecord> findByDeletedFalse(Pageable pageable);
}
