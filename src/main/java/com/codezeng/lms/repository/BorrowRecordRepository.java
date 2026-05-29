package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.BorrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long>, JpaSpecificationExecutor<BorrowRecord> {

    @EntityGraph(attributePaths = {"book", "reader"})
    Optional<BorrowRecord> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"book", "reader"})
    @Query("select br from BorrowRecord br where br.id = :id and br.deleted = false")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);

    long countByReaderAndStatusInAndDeletedFalse(Reader reader, Collection<BorrowStatus> statuses);

    long countByReader(Reader reader);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<BorrowRecord> findByReaderAndStatusInAndDeletedFalseOrderByDueDateAsc(Reader reader, Collection<BorrowStatus> statuses);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<BorrowRecord> findByReaderAndDeletedFalseOrderByBorrowDateDesc(Reader reader);

    boolean existsByReaderAndStatusAndDueDateBeforeAndDeletedFalse(Reader reader, BorrowStatus status, LocalDate date);

    boolean existsByBookAndReaderAndStatusInAndDeletedFalse(Book book, Reader reader, Collection<BorrowStatus> statuses);

    long countByBookAndStatusInAndDeletedFalse(Book book, Collection<BorrowStatus> statuses);

    long countByBook(Book book);

    long countByStatusIn(Collection<BorrowStatus> statuses);

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<BorrowRecord> findByDeletedFalse(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"book", "reader"})
    Page<BorrowRecord> findAll(Specification<BorrowRecord> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"book", "reader", "book.category", "book.tags"})
    List<BorrowRecord> findAll(Specification<BorrowRecord> spec);

    @Override
    @EntityGraph(attributePaths = {"book", "reader"})
    List<BorrowRecord> findAll(Specification<BorrowRecord> spec, Sort sort);

    @EntityGraph(attributePaths = {"book", "reader"})
    @Query("SELECT br FROM BorrowRecord br WHERE br.status = :status AND br.dueDate < :date AND br.deleted = false")
    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowStatus status, LocalDate date);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<BorrowRecord> findByStatusAndDueDate(BorrowStatus status, LocalDate date);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<BorrowRecord> findByStatus(BorrowStatus status);

    @EntityGraph(attributePaths = {"book", "reader"})
    Optional<BorrowRecord> findFirstByBook_IsbnAndStatusInAndDeletedFalseOrderByDueDateAsc(
            String isbn, Collection<BorrowStatus> statuses);
}
