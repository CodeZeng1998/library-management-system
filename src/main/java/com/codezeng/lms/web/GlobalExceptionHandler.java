package com.codezeng.lms.web;

import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.web.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final I18nMessageService i18n;

    public GlobalExceptionHandler(I18nMessageService i18n) {
        this.i18n = i18n;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Object handleBusinessException(RuntimeException ex, Model model, HttpServletRequest request) {
        if (isAjaxRequest(request)) {
            return handleAjaxBusinessException(ex);
        }
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("supportCode", null);
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, Model model, HttpServletRequest request) {
        if (isAjaxRequest(request)) {
            return handleAjaxAccessDenied(ex);
        }
        model.addAttribute("errorMessage", i18n.get("error.accessDenied"));
        model.addAttribute("supportCode", null);
        return "error";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoHandlerFoundException ex, Model model) {
        model.addAttribute("errorMessage", i18n.get("error.notFound"));
        model.addAttribute("supportCode", null);
        return "error";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        ApiResponse<Map<String, String>> response = ApiResponse.error(i18n.get("error.validation"), "VALIDATION_ERROR");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public Object handle(Exception ex, Model model, HttpServletRequest request) {
        String supportCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Unhandled request failure, supportCode={}, path={}", supportCode, request.getRequestURI(), ex);

        if (isAjaxRequest(request)) {
            return handleAjaxException(ex, supportCode);
        }

        model.addAttribute("errorMessage", i18n.get("error.system"));
        model.addAttribute("supportCode", supportCode);
        return "error";
    }

    private ResponseEntity<ApiResponse<Void>> handleAjaxBusinessException(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "BUSINESS_ERROR"));
    }

    private ResponseEntity<ApiResponse<Void>> handleAjaxAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(i18n.get("error.accessDenied"), "ACCESS_DENIED"));
    }

    private ResponseEntity<ApiResponse<Void>> handleAjaxException(Exception ex, String supportCode) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(i18n.get("error.system") + " (Code: " + supportCode + ")", "SYSTEM_ERROR"));
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equals(requestedWith)
                || (accept != null && accept.contains("application/json"))
                || (contentType != null && contentType.contains("application/json"))
                || request.getRequestURI().startsWith("/api/");
    }
}
