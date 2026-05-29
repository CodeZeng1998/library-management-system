package com.codezeng.lms.web;

import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.FineService;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.NotificationService;
import com.codezeng.lms.service.ReaderPortalService;
import com.codezeng.lms.service.ReservationService;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/portal")
@PreAuthorize("hasAuthority('ROLE_READER')")
public class ReaderPortalController {

    private final ReaderPortalService readerPortalService;
    private final BorrowService borrowService;
    private final ReservationService reservationService;
    private final FineService fineService;
    private final NotificationService notificationService;
    private final I18nMessageService i18n;

    public ReaderPortalController(ReaderPortalService readerPortalService,
                                  BorrowService borrowService,
                                  ReservationService reservationService,
                                  FineService fineService,
                                  NotificationService notificationService,
                                  I18nMessageService i18n) {
        this.readerPortalService = readerPortalService;
        this.borrowService = borrowService;
        this.reservationService = reservationService;
        this.fineService = fineService;
        this.notificationService = notificationService;
        this.i18n = i18n;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("dashboard", readerPortalService.dashboard());
        model.addAttribute("portalService", readerPortalService);
        return "portal/index";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportActivity() {
        String csv = readerPortalService.exportActivityCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reader-activity.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/borrows/{id}/renew")
    @PreventDuplicateSubmit
    public String renew(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerPortalService.requireOwnedBorrow(id);
            borrowService.renew(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.borrow.renewed"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/portal";
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreventDuplicateSubmit
    public String cancelReservation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerPortalService.requireOwnedReservation(id);
            reservationService.cancel(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.cancelled"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/portal";
    }

    @PostMapping("/books/{id}/reserve")
    @PreventDuplicateSubmit
    public String reserveBook(@PathVariable Long id,
                              @RequestParam(defaultValue = "/books") String redirect,
                              RedirectAttributes redirectAttributes) {
        try {
            reservationService.reserve(id, readerPortalService.requireCurrentReader().getId());
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.created"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:" + safeRedirect(redirect);
    }

    @PostMapping("/fines/{id}/pay")
    @PreventDuplicateSubmit
    public String payFine(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerPortalService.requireOwnedFine(id);
            fineService.pay(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("portal.payment.success"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/portal";
    }

    @PostMapping("/notifications/{id}/read")
    @PreventDuplicateSubmit
    public String markNotificationRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerPortalService.requireOwnedNotification(id);
            notificationService.markRead(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.notification.read"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/portal";
    }

    private String userFacingError(RuntimeException ex) {
        if (ex instanceof NoSuchElementException || ex instanceof AccessDeniedException) {
            return i18n.get("error.recordUnavailable");
        }
        return ex.getMessage();
    }

    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/books";
        }
        return redirect;
    }
}
