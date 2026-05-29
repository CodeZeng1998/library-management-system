package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"reader"})
    Page<Notification> findByDeletedFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"reader"})
    Page<Notification> findByStatusAndDeletedFalse(NotificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reader"})
    Page<Notification> findByReaderAndDeletedFalse(Reader reader, Pageable pageable);

    @EntityGraph(attributePaths = {"reader"})
    List<Notification> findByIdInAndDeletedFalse(Collection<Long> ids);

    @Override
    @EntityGraph(attributePaths = {"reader"})
    Page<Notification> findAll(Specification<Notification> spec, Pageable pageable);

    long countByReaderAndStatusAndDeletedFalse(Reader reader, NotificationStatus status);

    long countByReader(Reader reader);

    long countByStatusAndDeletedFalse(NotificationStatus status);

    boolean existsByReaderAndTitleAndContentAndDeletedFalse(Reader reader, String title, String content);
}
