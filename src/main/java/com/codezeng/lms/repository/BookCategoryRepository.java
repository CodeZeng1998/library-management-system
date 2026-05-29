package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BookCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookCategoryRepository extends JpaRepository<BookCategory, Long> {

    List<BookCategory> findByDeletedFalseOrderByNameAsc();

    Optional<BookCategory> findByNameAndDeletedFalse(String name);

    Optional<BookCategory> findByIdAndDeletedFalse(Long id);

    Page<BookCategory> findByDeletedFalse(Pageable pageable);

    Page<BookCategory> findByDeletedFalseAndNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalseAndIdNot(String name, Long id);

    boolean existsByParentAndDeletedFalse(BookCategory parent);
}
