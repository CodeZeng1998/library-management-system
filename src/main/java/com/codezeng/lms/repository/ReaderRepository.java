package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Reader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    Optional<Reader> findByIdAndDeletedFalse(Long id);

    Optional<Reader> findByIdAndDeletedTrue(Long id);

    Optional<Reader> findByReaderNoAndDeletedFalse(String readerNo);

    Optional<Reader> findByEmailAndDeletedFalse(String email);

    Optional<Reader> findByReaderNo(String readerNo);

    Optional<Reader> findByEmail(String email);

    Optional<Reader> findByIdentityNo(String identityNo);

    boolean existsByReaderNoAndDeletedFalse(String readerNo);

    boolean existsByReaderNoAndIdNot(String readerNo, Long id);

    boolean existsByReaderNoAndDeletedFalseAndIdNot(String readerNo, Long id);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmailAndDeletedFalseAndIdNot(String email, Long id);

    boolean existsByIdentityNoAndDeletedFalse(String identityNo);

    boolean existsByIdentityNoAndIdNot(String identityNo, Long id);

    boolean existsByIdentityNoAndDeletedFalseAndIdNot(String identityNo, Long id);

    Page<Reader> findByDeletedFalseAndNameContainingIgnoreCaseOrDeletedFalseAndReaderNoContainingIgnoreCaseOrDeletedFalseAndPhoneContainingIgnoreCase(
            String name, String readerNo, String phone, Pageable pageable);

    Page<Reader> findByDeletedFalse(Pageable pageable);

    Page<Reader> findByDeletedTrue(Pageable pageable);

    long countByDeletedFalse();
}
