package com.codezeng.lms.web;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.FineService;
import com.codezeng.lms.service.I18nMessageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
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
import java.util.NoSuchElementException;


@Controller
@RequestMapping("/fines")
public class FineController {

    private final FineService fineService;
    private final I18nMessageService i18n;

    public FineController(FineService fineService, I18nMessageService i18n) {
        this.fineService = fineService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINE_VIEW')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) FineStatus status,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = fineService.normalizePageSize(size);
        Page<FineRecord> fines = fineService.search(status, keyword, page, pageSize);
        model.addAttribute("fines", fines);
        model.addAttribute("statuses", FineStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(status, keyword, pageSize));
        model.addAttribute("unpaidCount", fineService.countByStatus(FineStatus.UNPAID));
        model.addAttribute("paidCount", fineService.countByStatus(FineStatus.PAID));
        model.addAttribute("waivedCount", fineService.countByStatus(FineStatus.WAIVED));
        model.addAttribute("totalUnpaidAmount", fineService.totalUnpaidAmount());
        return "fine/list";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('FINE_VIEW')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) FineStatus status,
                                            @RequestParam(required = false) String keyword) {
        return csvResponse("fines.csv", fineService.exportCsv(status, keyword));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('FINE_PAY')")
    @PreventDuplicateSubmit
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            fineService.pay(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.fine.paid"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/fines";
    }

    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAuthority('FINE_WAIVE')")
    @PreventDuplicateSubmit
    public String waive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            fineService.waive(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.fine.waived"));
        } catch (IllegalStateException | NoSuchElementException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/fines";
    }

    private String userFacingError(RuntimeException ex) {
        if (ex instanceof NoSuchElementException || ex instanceof AccessDeniedException) {
            return i18n.get("error.recordUnavailable");
        }
        return ex.getMessage();
    }

    private String queryString(FineStatus status, String keyword, int size) {
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
