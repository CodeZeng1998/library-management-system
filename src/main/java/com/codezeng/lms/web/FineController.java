package com.codezeng.lms.web;

import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.service.FineService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    public FineController(FineRecordRepository fineRecordRepository, FineService fineService) {
        this.fineRecordRepository = fineRecordRepository;
        this.fineService = fineService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("fines", fineRecordRepository.findByDeletedFalse(PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        return "fine/list";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        fineService.pay(id);
        redirectAttributes.addFlashAttribute("message", "罚款已缴纳");
        return "redirect:/fines";
    }

    @PostMapping("/{id}/waive")
    public String waive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        fineService.waive(id);
        redirectAttributes.addFlashAttribute("message", "罚款已减免");
        return "redirect:/fines";
    }
}
