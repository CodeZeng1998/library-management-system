package com.codezeng.lms.web;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ImportResult;
import com.codezeng.lms.service.ReaderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/readers")
public class ReaderController {

    private final ReaderRepository readerRepository;
    private final ReaderService readerService;
    private final I18nMessageService i18n;

    public ReaderController(ReaderRepository readerRepository, ReaderService readerService, I18nMessageService i18n) {
        this.readerRepository = readerRepository;
        this.readerService = readerService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READER_VIEW')")
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "30") int size,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        model.addAttribute("readers", readerService.search(keyword, PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createTime"))));
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageSize", pageSize);
        return "reader/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public String create(Model model) {
        addFormData(model, new Reader());
        return "reader/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public String edit(@PathVariable Long id, Model model) {
        addFormData(model, readerRepository.findById(id).orElseThrow());
        return "reader/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public String save(@ModelAttribute Reader reader, RedirectAttributes redirectAttributes) {
        readerService.save(reader);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.saved"));
        return "redirect:/readers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('READER_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        readerService.softDelete(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.deleted"));
        return "redirect:/readers";
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public String importCsv(@RequestParam MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            ImportResult result = readerService.importCsv(file);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        }
        return "redirect:/readers";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('READER_VIEW')")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=readers.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(readerService.exportCsv().getBytes(StandardCharsets.UTF_8));
    }

    private void addFormData(Model model, Reader reader) {
        model.addAttribute("reader", reader);
        model.addAttribute("readerTypes", ReaderType.values());
        model.addAttribute("memberLevels", MemberLevel.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
    }
}
