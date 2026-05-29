package com.codezeng.lms.web;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.NotificationService;
import org.springframework.data.domain.Page;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final I18nMessageService i18n;

    public NotificationController(NotificationService notificationService, I18nMessageService i18n) {
        this.notificationService = notificationService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) NotificationStatus status,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), 12, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<Notification> notifications = notificationService.search(status, keyword, pageable);
        model.addAttribute("notifications", notifications);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("queryString", queryString(status, keyword));
        model.addAttribute("unreadCount", notificationService.unreadCount());
        return "notification/list";
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    @PreventDuplicateSubmit
    public String markRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        notificationService.markRead(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.notification.read"));
        return "redirect:/notifications";
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    @PreventDuplicateSubmit
    public String batch(@RequestParam(required = false) List<Long> ids,
                        @RequestParam String action,
                        @RequestParam(required = false) NotificationStatus status,
                        @RequestParam(required = false) String keyword,
                        RedirectAttributes redirectAttributes) {
        String redirect = "redirect:/notifications" + redirectQuery(status, keyword);
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", i18n.get("notification.batch.empty"));
            return redirect;
        }
        int count;
        if ("delete".equals(action)) {
            count = notificationService.softDelete(ids);
            redirectAttributes.addFlashAttribute("message", i18n.get("notification.batch.deleted", count));
        } else if ("read".equals(action)) {
            count = notificationService.markRead(ids);
            redirectAttributes.addFlashAttribute("message", i18n.get("notification.batch.read", count));
        } else {
            redirectAttributes.addFlashAttribute("error", i18n.get("notification.batch.invalidAction"));
        }
        return redirect;
    }

    private String queryString(NotificationStatus status, String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "status", status);
        queryParam(builder, "keyword", keyword);
        String query = builder.build().encode().toUriString();
        return query.startsWith("?") ? "&" + query.substring(1) : query;
    }

    private String redirectQuery(NotificationStatus status, String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "status", status);
        queryParam(builder, "keyword", keyword);
        String query = builder.build().encode().toUriString();
        return query.isBlank() ? "" : query;
    }

    private void queryParam(UriComponentsBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            builder.queryParam(name, text);
        }
    }
}
