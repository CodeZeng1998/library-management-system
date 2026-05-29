package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, Long>, JpaSpecificationExecutor<ReservationRecord> {

    @EntityGraph(attributePaths = {"book", "reader"})
    Optional<ReservationRecord> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"book", "reader"})
    @Query("select r from ReservationRecord r where r.id = :id and r.deleted = false")
    Optional<ReservationRecord> findByIdForUpdate(@Param("id") Long id);

    long countByBookAndStatusAndDeletedFalse(Book book, ReservationStatus status);

    long countByBookAndStatusInAndDeletedFalse(Book book, Collection<ReservationStatus> statuses);

    long countByBook(Book book);

    long countByBookAndStatusAndReservedAtBeforeAndDeletedFalse(Book book, ReservationStatus status, LocalDateTime reservedAt);

    long countByStatusIn(Collection<ReservationStatus> statuses);

    long countByReaderAndStatusInAndDeletedFalse(
            com.codezeng.lms.domain.Reader reader, Collection<ReservationStatus> statuses);

    long countByReader(com.codezeng.lms.domain.Reader reader);

    boolean existsByBookAndReaderAndStatusInAndDeletedFalse(
            Book book, com.codezeng.lms.domain.Reader reader, Collection<ReservationStatus> statuses);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<ReservationRecord> findByBookAndStatusAndDeletedFalseOrderByReservedAtAsc(Book book, ReservationStatus status);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<ReservationRecord> findByReaderAndDeletedFalseOrderByReservedAtDesc(com.codezeng.lms.domain.Reader reader);

    @EntityGraph(attributePaths = {"book", "reader"})
    Optional<ReservationRecord> findFirstByBookAndReaderAndStatusAndExpiresAtAfterAndDeletedFalse(
            Book book, com.codezeng.lms.domain.Reader reader, ReservationStatus status, LocalDateTime dateTime);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<ReservationRecord> findByStatusInAndExpiresAtBeforeAndDeletedFalse(Collection<ReservationStatus> statuses, LocalDateTime dateTime);

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<ReservationRecord> findByDeletedFalse(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"book", "reader"})
    Page<ReservationRecord> findAll(Specification<ReservationRecord> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"book", "reader"})
    List<ReservationRecord> findAll(Specification<ReservationRecord> spec, Sort sort);
}
