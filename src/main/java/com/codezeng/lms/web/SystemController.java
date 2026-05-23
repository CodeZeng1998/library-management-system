package com.codezeng.lms.web;

import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.repository.OperationLogRepository;
import com.codezeng.lms.repository.SystemConfigRepository;
import com.codezeng.lms.service.OperationLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/system")
public class SystemController {

    private final SystemConfigRepository systemConfigRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperationLogService operationLogService;

    public SystemController(
            SystemConfigRepository systemConfigRepository,
            OperationLogRepository operationLogRepository,
            OperationLogService operationLogService) {
        this.systemConfigRepository = systemConfigRepository;
        this.operationLogRepository = operationLogRepository;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/configs")
    public String configs(Model model) {
        model.addAttribute("configs", systemConfigRepository.findAll(Sort.by("configKey")));
        return "system/configs";
    }

    @PostMapping("/configs")
    public String updateConfig(@RequestParam Long id,
                               @RequestParam String configValue,
                               RedirectAttributes redirectAttributes) {
        SystemConfig config = systemConfigRepository.findById(id).orElseThrow();
        config.setConfigValue(configValue);
        systemConfigRepository.save(config);
        operationLogService.record("系统管理", "更新配置", config.getConfigKey());
        redirectAttributes.addFlashAttribute("message", "系统配置已更新");
        return "redirect:/system/configs";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", operationLogRepository.findByDeletedFalse(PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createTime"))));
        return "system/logs";
    }
}
