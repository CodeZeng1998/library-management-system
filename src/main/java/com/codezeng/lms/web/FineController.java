package com.codezeng.lms.web;

import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.FineService;
import com.codezeng.lms.service.I18nMessageService;
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
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("fines", fineRecordRepository.findAll(dataScopeService.fineScope(), PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
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
}
