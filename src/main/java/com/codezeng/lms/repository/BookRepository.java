package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbnAndDeletedFalse(String isbn);

    Page<Book> findByDeletedFalseAndTitleContainingIgnoreCaseOrDeletedFalseAndAuthorContainingIgnoreCaseOrDeletedFalseAndIsbnContainingIgnoreCase(
            String title, String author, String isbn, Pageable pageable);

    Page<Book> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();

    List<Book> findTop10ByDeletedFalseOrderByBorrowCountDesc();
}
