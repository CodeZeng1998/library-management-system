package com.codezeng.lms.repository;

import com.codezeng.lms.domain.BookTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookTagRepository extends JpaRepository<BookTag, Long> {

    List<BookTag> findByDeletedFalseOrderByNameAsc();

    Page<BookTag> findByDeletedFalse(Pageable pageable);

    Page<BookTag> findByDeletedFalseAndNameContainingIgnoreCase(String name, Pageable pageable);

    List<BookTag> findByIdInAndDeletedFalse(Collection<Long> ids);

    Optional<BookTag> findByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);
}
