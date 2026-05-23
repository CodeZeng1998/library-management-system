package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BookCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookCategoryRepository extends JpaRepository<BookCategory, Long> {

    List<BookCategory> findByDeletedFalseOrderByNameAsc();

    Optional<BookCategory> findByNameAndDeletedFalse(String name);
}
