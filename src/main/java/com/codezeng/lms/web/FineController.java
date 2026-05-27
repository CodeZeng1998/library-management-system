package com.codezeng.lms.web;

import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.FineService;
import com.codezeng.lms.service.I18nMessageService;
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


@Controller
@RequestMapping("/fines")
public class FineController {

    private final FineRecordRepository fineRecordRepository;
    private final FineService fineService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public FineController(FineRecordRepository fineRecordRepository, FineService fineService, DataScopeService dataScopeService, I18nMessageService i18n) {
        this.fineRecordRepository = fineRecordRepository;
        this.fineService = fineService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINE_VIEW')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) FineStatus status,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Specification<FineRecord> spec = fineSpec(status, keyword);
        Page<FineRecord> fines = fineRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        model.addAttribute("fines", fines);
        model.addAttribute("statuses", FineStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(status, keyword, pageSize));
        model.addAttribute("unpaidCount", fineRecordRepository.count(statusSpec(FineStatus.UNPAID)));
        model.addAttribute("paidCount", fineRecordRepository.count(statusSpec(FineStatus.PAID)));
        model.addAttribute("waivedCount", fineRecordRepository.count(statusSpec(FineStatus.WAIVED)));
        model.addAttribute("totalUnpaidAmount", fineRecordRepository.sumAmountByStatus(FineStatus.UNPAID));
        return "fine/list";
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('FINE_PAY')")
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        fineService.pay(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.fine.paid"));
        return "redirect:/fines";
    }

    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAuthority('FINE_WAIVE')")
    public String waive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        fineService.waive(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.fine.waived"));
        return "redirect:/fines";
    }

    private Specification<FineRecord> fineSpec(FineStatus status, String keyword) {
        Specification<FineRecord> spec = dataScopeService.fineScope();
        if (status != null) {
            spec = spec.and(statusSpec(status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("reason")), like),
                    builder.like(builder.lower(root.join("reader").get("readerNo")), like),
                    builder.like(builder.lower(root.join("reader").get("name")), like),
                    builder.like(builder.lower(root.join("borrowRecord").join("book").get("title")), like)));
        }
        return spec;
    }

    private Specification<FineRecord> statusSpec(FineStatus status) {
        return dataScopeService.fineScope()
                .and((root, query, builder) -> builder.equal(root.get("status"), status));
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
}
