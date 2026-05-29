package com.codezeng.lms.service;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.NotificationChannel;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OperationLogService operationLogService;
    private final I18nMessageService i18n;

    public NotificationService(NotificationRepository notificationRepository,
                               OperationLogService operationLogService,
                               I18nMessageService i18n) {
        this.notificationRepository = notificationRepository;
        this.operationLogService = operationLogService;
        this.i18n = i18n;
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
        operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.notification.send"),
                reader.getReaderNo() + " - " + title);
        return saved;
    }

    @Transactional
    public boolean sendOnce(Reader reader, String title, String content) {
        if (notificationRepository.existsByReaderAndTitleAndContentAndDeletedFalse(reader, title, content)) {
            return false;
        }
        send(reader, title, content);
        return true;
    }

    @Transactional(readOnly = true)
    public Page<Notification> search(NotificationStatus status, String keyword, Pageable pageable) {
        return notificationRepository.findAll(toSpecification(status, keyword), pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByStatusAndDeletedFalse(NotificationStatus.UNREAD);
    }

    @Transactional
    public boolean markRead(Long id) {
        Notification notification = notificationRepository.findByIdAndDeletedFalse(id).orElseThrow();
        if (notification.getStatus() == NotificationStatus.READ) {
            return false;
        }
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
        operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.notification.read"),
                notification.getTitle());
        return true;
    }

    @Transactional
    public int markRead(Collection<Long> ids) {
        List<Notification> notifications = notificationRepository.findByIdInAndDeletedFalse(ids);
        LocalDateTime now = LocalDateTime.now();
        List<Notification> changed = notifications.stream()
                .filter(notification -> notification.getStatus() == NotificationStatus.UNREAD)
                .peek(notification -> {
                    notification.setStatus(NotificationStatus.READ);
                    notification.setReadAt(now);
                })
                .toList();
        notificationRepository.saveAll(changed);
        if (!changed.isEmpty()) {
            operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.notification.batchRead"),
                    i18n.get("log.notification.batchDetail", changed.size()));
        }
        return changed.size();
    }

    @Transactional
    public int softDelete(Collection<Long> ids) {
        List<Notification> notifications = notificationRepository.findByIdInAndDeletedFalse(ids);
        notifications.forEach(notification -> notification.setDeleted(true));
        notificationRepository.saveAll(notifications);
        if (!notifications.isEmpty()) {
            operationLogService.record(i18n.get("log.module.notification"), i18n.get("log.notification.batchDelete"),
                    i18n.get("log.notification.batchDetail", notifications.size()));
        }
        return notifications.size();
    }

    private Specification<Notification> toSpecification(NotificationStatus status, String keyword) {
        Specification<Notification> spec = (root, query, builder) -> builder.isFalse(root.get("deleted"));
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), like),
                    builder.like(builder.lower(root.get("content")), like),
                    builder.like(builder.lower(root.join("reader").get("readerNo")), like),
                    builder.like(builder.lower(root.join("reader").get("name")), like)));
        }
        return spec;
    }
}
