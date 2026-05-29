package com.codezeng.lms.repository;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.FineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FineRecordRepository extends JpaRepository<FineRecord, Long>, JpaSpecificationExecutor<FineRecord> {

    @Override
    @EntityGraph(attributePaths = {"reader", "borrowRecord", "borrowRecord.book"})
    Page<FineRecord> findAll(Specification<FineRecord> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"reader", "borrowRecord", "borrowRecord.book"})
    List<FineRecord> findAll(Specification<FineRecord> spec, Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FineRecord f where f.id = :id and f.deleted = false")
    Optional<FineRecord> findByIdForUpdate(@Param("id") Long id);

    boolean existsByReaderAndStatusAndDeletedFalse(Reader reader, FineStatus status);

    long countByReader(Reader reader);

    List<FineRecord> findByReaderAndDeletedFalseOrderByCreateTimeDesc(Reader reader);

    boolean existsByBorrowRecordAndStatus(BorrowRecord borrowRecord, FineStatus status);

    @Query("select coalesce(sum(f.amount), 0) from FineRecord f where f.deleted = false and f.status = :status")
    BigDecimal sumAmountByStatus(FineStatus status);

    Page<FineRecord> findByDeletedFalse(Pageable pageable);
}
