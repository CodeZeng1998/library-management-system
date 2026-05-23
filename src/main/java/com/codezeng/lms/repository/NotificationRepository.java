package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDeletedFalse(Pageable pageable);

    Page<Notification> findByReaderAndDeletedFalse(Reader reader, Pageable pageable);

    long countByStatusAndDeletedFalse(NotificationStatus status);
}
