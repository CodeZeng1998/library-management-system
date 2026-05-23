package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbnAndDeletedFalse(String isbn);

    Page<Book> findByDeletedFalseAndTitleContainingIgnoreCaseOrDeletedFalseAndAuthorContainingIgnoreCaseOrDeletedFalseAndIsbnContainingIgnoreCase(
            String title, String author, String isbn, Pageable pageable);

    Page<Book> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();

    List<Book> findTop10ByDeletedFalseOrderByBorrowCountDesc();

    @Query("select distinct b.location from Book b where b.deleted = false and b.location is not null and b.location <> '' order by b.location")
    List<String> findDistinctLocations();
}
