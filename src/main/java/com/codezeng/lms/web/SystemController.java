package com.codezeng.lms.web;

import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.OperationLogQueryService;
import com.codezeng.lms.service.SystemConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/system")
public class SystemController {

    private final SystemConfigService systemConfigService;
    private final OperationLogQueryService operationLogQueryService;
    private final I18nMessageService i18n;

    public SystemController(
            SystemConfigService systemConfigService,
            OperationLogQueryService operationLogQueryService,
            I18nMessageService i18n) {
        this.systemConfigService = systemConfigService;
        this.operationLogQueryService = operationLogQueryService;
        this.i18n = i18n;
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('CONFIG_MANAGE')")
    public String configs(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("configs", systemConfigService.search(keyword));
        model.addAttribute("validationHints", systemConfigService.validationHints());
        model.addAttribute("keyword", keyword);
        return "system/configs";
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('CONFIG_MANAGE')")
    public String updateConfig(@RequestParam Long id,
                               @RequestParam String configValue,
                               @RequestParam(required = false) String keyword,
                               RedirectAttributes redirectAttributes) {
        try {
            systemConfigService.update(id, configValue);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.system.configUpdated"));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToConfigs(keyword);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('LOG_VIEW')")
    public String logs(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String module,
                       @RequestParam(required = false) String ip,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<OperationLog> logs = operationLogQueryService.search(
                keyword,
                module,
                ip,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        model.addAttribute("logs", logs);
        model.addAttribute("keyword", keyword);
        model.addAttribute("module", module);
        model.addAttribute("ip", ip);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, module, ip, pageSize));
        return "system/logs";
    }

    @GetMapping("/logs/export")
    @PreAuthorize("hasAuthority('LOG_VIEW')")
    public ResponseEntity<byte[]> exportLogs(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String module,
                                             @RequestParam(required = false) String ip) {
        return csvResponse("operation-logs.csv", operationLogQueryService.exportCsv(keyword, module, ip));
    }

    private String redirectToConfigs(String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/system/configs");
        queryParam(builder, "keyword", keyword);
        return "redirect:" + builder.build().encode().toUriString();
    }

    private String queryString(String keyword, String module, String ip, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "keyword", keyword);
        queryParam(builder, "module", module);
        queryParam(builder, "ip", ip);
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
