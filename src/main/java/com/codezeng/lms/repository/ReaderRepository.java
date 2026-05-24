package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Reader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    Optional<Reader> findByReaderNoAndDeletedFalse(String readerNo);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByIdentityNoAndDeletedFalse(String identityNo);

    Page<Reader> findByDeletedFalseAndNameContainingIgnoreCaseOrDeletedFalseAndReaderNoContainingIgnoreCaseOrDeletedFalseAndPhoneContainingIgnoreCase(
            String name, String readerNo, String phone, Pageable pageable);

    Page<Reader> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();
}
