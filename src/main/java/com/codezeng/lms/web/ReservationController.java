package com.codezeng.lms.web;

import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationRecordRepository reservationRecordRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ReservationService reservationService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public ReservationController(
            ReservationRecordRepository reservationRecordRepository,
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationService reservationService,
            DataScopeService dataScopeService,
            I18nMessageService i18n) {
        this.reservationRecordRepository = reservationRecordRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationService = reservationService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "ACTIVE") String status,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Specification<ReservationRecord> spec = reservationSpec(status, keyword);
        Page<ReservationRecord> reservations = reservationRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "reservedAt")));
        LocalDateTime now = LocalDateTime.now();

        model.addAttribute("reservations", reservations);
        model.addAttribute("books", bookRepository.findAll(dataScopeService.bookScope(), PageRequest.of(0, 200, Sort.by("title"))).getContent());
        model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(status, keyword, pageSize));
        model.addAttribute("queuePositions", queuePositions(reservations.getContent()));
        model.addAttribute("now", now);
        model.addAttribute("waitingCount", reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.WAITING))));
        model.addAttribute("notifiedCount", reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.NOTIFIED))));
        model.addAttribute("expiringSoonCount", reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.NOTIFIED))
                .and((root, query, builder) -> builder.between(root.get("expiresAt"), now, now.plusHours(12)))));
        model.addAttribute("expiredActiveCount", reservationRecordRepository.count(statusSpec(List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED))
                .and((root, query, builder) -> builder.lessThan(root.get("expiresAt"), now))));
        return "reservation/list";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String reserve(@RequestParam Long bookId,
                          @RequestParam Long readerId,
                          RedirectAttributes redirectAttributes) {
        try {
            reservationService.reserve(bookId, readerId);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.created"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reservationService.cancel(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.cancelled"));
        return "redirect:/reservations";
    }

    @PostMapping("/expire")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public String expire(RedirectAttributes redirectAttributes) {
        int releasedCopies = reservationService.expireReservations();
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reservation.expired", releasedCopies));
        return "redirect:/reservations";
    }

    private Specification<ReservationRecord> reservationSpec(String status, String keyword) {
        Specification<ReservationRecord> spec = dataScopeService.reservationScope();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            spec = spec.and(statusSpec(List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED)));
        } else if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            ReservationStatus selected = parseStatus(status);
            if (selected != null) {
                spec = spec.and(statusSpec(List.of(selected)));
            }
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.join("book").get("title")), like),
                    builder.like(builder.lower(root.join("book").get("isbn")), like),
                    builder.like(builder.lower(root.join("reader").get("readerNo")), like),
                    builder.like(builder.lower(root.join("reader").get("name")), like)));
        }
        return spec;
    }

    private Specification<ReservationRecord> statusSpec(List<ReservationStatus> statuses) {
        return dataScopeService.reservationScope()
                .and((root, query, builder) -> root.get("status").in(statuses));
    }

    private ReservationStatus parseStatus(String value) {
        try {
            return ReservationStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<Long, Long> queuePositions(List<ReservationRecord> reservations) {
        Map<Long, Long> positions = new LinkedHashMap<>();
        for (ReservationRecord reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.WAITING) {
                long ahead = reservationRecordRepository.countByBookAndStatusAndReservedAtBefore(
                        reservation.getBook(), ReservationStatus.WAITING, reservation.getReservedAt());
                positions.put(reservation.getId(), ahead + 1);
            }
        }
        return positions;
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
}
