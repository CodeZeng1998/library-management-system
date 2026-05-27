package com.codezeng.lms.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleBusinessException(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("supportCode", null);
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("errorMessage", "当前账号无权访问该资源或执行该操作。");
        model.addAttribute("supportCode", null);
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handle(Exception ex, Model model) {
        String supportCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Unhandled request failure, supportCode={}", supportCode, ex);
        model.addAttribute("errorMessage", "系统暂时无法完成请求，请稍后重试或联系管理员。");
        model.addAttribute("supportCode", supportCode);
        return "error";
    }
}
