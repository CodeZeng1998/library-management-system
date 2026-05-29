package com.codezeng.lms.web;

import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final I18nMessageService i18n;

    public ReservationController(
            ReservationService reservationService,
            I18nMessageService i18n) {
        this.reservationService = reservationService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "ACTIVE") String status,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = reservationService.normalizePageSize(size);
        Page<ReservationRecord> reservations = reservationService.search(status, keyword, page, pageSize);
        LocalDateTime now = LocalDateTime.now();

        model.addAttribute("reservations", reservations);
        model.addAttribute("books", reservationService.selectableBooks());
        model.addAttribute("readers", reservationService.selectableReaders());
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(status, keyword, pageSize));
        model.addAttribute("queuePositions", reservationService.queuePositions(reservations.getContent()));
        model.addAttribute("now", now);
        model.addAttribute("waitingCount", reservationService.countByStatuses(List.of(ReservationStatus.WAITING)));
        model.addAttribute("notifiedCount", reservationService.countByStatuses(List.of(ReservationStatus.NOTIFIED)));
        model.addAttribute("expiringSoonCount", reservationService.countExpiringSoon(now));
        model.addAttribute("expiredActiveCount", reservationService.countExpiredActive(now));
        return "reservation/list";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "ACTIVE") String status,
                                            @RequestParam(required = false) String keyword) {
        return csvResponse("reservations.csv", reservationService.exportCsv(status, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @PreventDuplicateSubmit
    public String reserve(@RequestParam Long bookId,
                          @RequestParam Long readerId,
                          RedirectAttributes redirectAttributes) {
        try {
            reservationService.reserve(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.created"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @PreventDuplicateSubmit
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.cancel(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.cancelled"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/reservations";
    }

    @PostMapping("/expire")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @PreventDuplicateSubmit
    public String expire(RedirectAttributes redirectAttributes) {
        try {
            int releasedCopies = reservationService.expireReservations();
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.expired", releasedCopies));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/reservations";
    }

    private String userFacingError(RuntimeException ex) {
        if (ex instanceof NoSuchElementException || ex instanceof AccessDeniedException) {
            return i18n.get("error.recordUnavailable");
        }
        return ex.getMessage();
    }

    private String queryString(String status, String keyword, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "status", status);
        queryParam(builder, "keyword", keyword);
        builder.queryParam("size", size);
        String query = builder.build().encode().toUriString();
        return query.startsWith("?") ? "&" + query.substring(1) : query;
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

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
