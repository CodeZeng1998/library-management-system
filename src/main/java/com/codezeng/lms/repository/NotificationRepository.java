package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    Page<Notification> findByDeletedFalse(Pageable pageable);

    Page<Notification> findByStatusAndDeletedFalse(NotificationStatus status, Pageable pageable);

    Page<Notification> findByReaderAndDeletedFalse(Reader reader, Pageable pageable);

    List<Notification> findByIdInAndDeletedFalse(Collection<Long> ids);

    long countByReaderAndStatusAndDeletedFalse(Reader reader, NotificationStatus status);

    long countByStatusAndDeletedFalse(NotificationStatus status);
}
