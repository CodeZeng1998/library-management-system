package com.codezeng.lms.web;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.service.ReaderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/readers")
public class ReaderController {

    private final ReaderRepository readerRepository;
    private final ReaderService readerService;

    public ReaderController(ReaderRepository readerRepository, ReaderService readerService) {
        this.readerRepository = readerRepository;
        this.readerService = readerService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("readers", readerService.search(keyword, PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        model.addAttribute("keyword", keyword);
        return "reader/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        addFormData(model, new Reader());
        return "reader/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        addFormData(model, readerRepository.findById(id).orElseThrow());
        return "reader/form";
    }

    @PostMapping
    public String save(@ModelAttribute Reader reader, RedirectAttributes redirectAttributes) {
        readerService.save(reader);
        redirectAttributes.addFlashAttribute("message", "读者信息已保存");
        return "redirect:/readers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        readerService.softDelete(id);
        redirectAttributes.addFlashAttribute("message", "读者已删除");
        return "redirect:/readers";
    }

    private void addFormData(Model model, Reader reader) {
        model.addAttribute("reader", reader);
        model.addAttribute("readerTypes", ReaderType.values());
        model.addAttribute("memberLevels", MemberLevel.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
    }
}
