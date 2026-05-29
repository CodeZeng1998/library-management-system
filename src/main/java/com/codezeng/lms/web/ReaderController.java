package com.codezeng.lms.web;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.CsvImportGuard;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.ImportResult;
import com.codezeng.lms.service.ReaderService;
import jakarta.servlet.http.HttpSession;
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

    private static final String READER_IMPORT_BYTES = "READER_IMPORT_BYTES";
    private static final String READER_IMPORT_ERRORS = "READER_IMPORT_ERRORS";

    private final ReaderService readerService;
    private final CsvImportGuard csvImportGuard;
    private final I18nMessageService i18n;

    public ReaderController(ReaderService readerService,
                            CsvImportGuard csvImportGuard,
                            I18nMessageService i18n) {
        this.readerService = readerService;
        this.csvImportGuard = csvImportGuard;
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
        addFormData(model, readerService.getEditable(id));
        return "reader/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('READER_EDIT')")
    @PreventDuplicateSubmit
    public String save(@ModelAttribute Reader reader, Model model, RedirectAttributes redirectAttributes) {
        try {
            readerService.save(reader);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.saved"));
            return "redirect:/readers";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            addFormData(model, reader);
            model.addAttribute("error", ex.getMessage());
            return "reader/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('READER_DELETE')")
    @PreventDuplicateSubmit
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        readerService.softDelete(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.deleted"));
        return "redirect:/readers";
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    @PreventDuplicateSubmit
    public String importCsv(@RequestParam MultipartFile file,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            csvImportGuard.validate(file);
            ImportResult result = readerService.importCsv(file);
            rememberErrorReport(session, READER_IMPORT_ERRORS, result);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/readers";
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    @PreventDuplicateSubmit
    public String previewImport(@RequestParam MultipartFile file,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            csvImportGuard.validate(file);
            session.setAttribute(READER_IMPORT_BYTES, file.getBytes());
            ImportResult result = readerService.previewCsv(file);
            rememberErrorReport(session, READER_IMPORT_ERRORS, result);
            model.addAttribute("result", result);
            model.addAttribute("importTitle", "Reader CSV import preview");
            model.addAttribute("confirmUrl", "/readers/import/confirm");
            model.addAttribute("cancelUrl", "/readers");
            model.addAttribute("templateUrl", "/readers/import/template");
            model.addAttribute("errorReportUrl", "/readers/import/errors");
            return "import/preview";
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
            return "redirect:/readers";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/readers";
        }
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    @PreventDuplicateSubmit
    public String confirmImport(HttpSession session, RedirectAttributes redirectAttributes) {
        byte[] bytes = (byte[]) session.getAttribute(READER_IMPORT_BYTES);
        if (bytes == null) {
            redirectAttributes.addFlashAttribute("error", "No previewed CSV file found. Please upload and preview again.");
            return "redirect:/readers";
        }
        try {
            ImportResult result = readerService.importCsv(bytes);
            rememberErrorReport(session, READER_IMPORT_ERRORS, result);
            session.removeAttribute(READER_IMPORT_BYTES);
            redirectAttributes.addFlashAttribute(result.getFailureCount() == 0 ? "message" : "error", result.toMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", i18n.get("flash.import.failed", ex.getMessage()));
        }
        return "redirect:/readers";
    }

    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public ResponseEntity<byte[]> importTemplate() {
        return csvResponse("reader-import-template.csv", readerService.importTemplateCsv());
    }

    @GetMapping("/import/errors")
    @PreAuthorize("hasAuthority('READER_EDIT')")
    public ResponseEntity<byte[]> importErrors(HttpSession session) {
        String csv = (String) session.getAttribute(READER_IMPORT_ERRORS);
        return csvResponse("reader-import-errors.csv", csv == null ? "\uFEFFrow,status,message,values\n" : csv);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('READER_VIEW')")
    public ResponseEntity<byte[]> exportCsv() {
        return csvResponse("readers.csv", readerService.exportCsv());
    }

    @GetMapping("/trash")
    @PreAuthorize("hasAuthority('READER_DELETE')")
    public String trash(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "30") int size,
                        Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        model.addAttribute("readers", readerService.trash(PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "updateTime"))));
        model.addAttribute("pageSize", pageSize);
        return "reader/trash";
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('READER_DELETE')")
    @PreventDuplicateSubmit
    public String restore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerService.restore(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.restored"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/readers/trash";
    }

    @PostMapping("/{id}/purge")
    @PreAuthorize("hasAuthority('READER_DELETE')")
    @PreventDuplicateSubmit
    public String purge(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            readerService.purge(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.reader.purged"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/readers/trash";
    }

    private void rememberErrorReport(HttpSession session, String key, ImportResult result) {
        if (result.getFailureCount() > 0) {
            session.setAttribute(key, result.errorReportCsv());
        } else {
            session.removeAttribute(key);
        }
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private void addFormData(Model model, Reader reader) {
        model.addAttribute("reader", reader);
        model.addAttribute("readerTypes", ReaderType.values());
        model.addAttribute("memberLevels", MemberLevel.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
    }
}
