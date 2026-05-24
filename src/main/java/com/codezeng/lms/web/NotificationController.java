package com.codezeng.lms.web;

import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final I18nMessageService i18n;

    public NotificationController(NotificationRepository notificationRepository, NotificationService notificationService, I18nMessageService i18n) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) NotificationStatus status,
                       Model model) {
        PageRequest pageable = PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "sentAt"));
        model.addAttribute("notifications", status == null
                ? notificationRepository.findByDeletedFalse(pageable)
                : notificationRepository.findByStatusAndDeletedFalse(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("unreadCount", notificationRepository.countByStatusAndDeletedFalse(NotificationStatus.UNREAD));
        return "notification/list";
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public String markRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        notificationService.markRead(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.notification.read"));
        return "redirect:/notifications";
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public String batch(@RequestParam(required = false) List<Long> ids,
                        @RequestParam String action,
                        RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", i18n.get("notification.batch.empty"));
            return "redirect:/notifications";
        }
        int count;
        if ("delete".equals(action)) {
            count = notificationService.softDelete(ids);
            redirectAttributes.addFlashAttribute("message", i18n.get("notification.batch.deleted", count));
        } else {
            count = notificationService.markRead(ids);
            redirectAttributes.addFlashAttribute("message", i18n.get("notification.batch.read", count));
        }
        return "redirect:/notifications";
    }
}
