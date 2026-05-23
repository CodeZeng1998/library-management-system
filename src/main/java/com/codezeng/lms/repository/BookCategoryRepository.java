package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BookCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookCategoryRepository extends JpaRepository<BookCategory, Long> {

    List<BookCategory> findByDeletedFalseOrderByNameAsc();
}
