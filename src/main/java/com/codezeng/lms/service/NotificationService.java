package com.codezeng.lms.service;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.NotificationChannel;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OperationLogService operationLogService;

    public NotificationService(NotificationRepository notificationRepository, OperationLogService operationLogService) {
        this.notificationRepository = notificationRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public Notification send(Reader reader, String title, String content) {
        Notification notification = new Notification();
        notification.setReader(reader);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.UNREAD);
        notification.setSentAt(LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        operationLogService.record("消息中心", "发送站内信", reader.getReaderNo() + " - " + title);
        return saved;
    }

    @Transactional
    public void markRead(Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow();
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public int markRead(Collection<Long> ids) {
        List<Notification> notifications = notificationRepository.findByIdInAndDeletedFalse(ids);
        LocalDateTime now = LocalDateTime.now();
        notifications.stream()
                .filter(notification -> notification.getStatus() == NotificationStatus.UNREAD)
                .forEach(notification -> {
                    notification.setStatus(NotificationStatus.READ);
                    notification.setReadAt(now);
                });
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    @Transactional
    public int softDelete(Collection<Long> ids) {
        List<Notification> notifications = notificationRepository.findByIdInAndDeletedFalse(ids);
        notifications.forEach(notification -> notification.setDeleted(true));
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }
}
