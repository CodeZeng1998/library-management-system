package com.codezeng.lms.aspect;

import com.codezeng.lms.security.PreventDuplicateSubmit;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class DuplicateSubmitAspect {

    private final MessageSource messageSource;
    private final Map<String, Long> submitCache = new ConcurrentHashMap<>();

    public DuplicateSubmitAspect(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Around("@annotation(com.codezeng.lms.security.PreventDuplicateSubmit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        PreventDuplicateSubmit annotation = method.getAnnotation(PreventDuplicateSubmit.class);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (annotation.requireLogin() && !isAuthenticatedUser(authentication)) {
            return joinPoint.proceed();
        }

        String key = generateKey(request, method, authentication, joinPoint);
        long now = System.currentTimeMillis();
        Long lastSubmitTime = submitCache.get(key);

        if (lastSubmitTime != null && (now - lastSubmitTime) < annotation.timeout() * 1000L) {
            String message = messageSource.getMessage(
                annotation.messageKey(),
                null,
                "Please do not submit repeatedly",
                LocaleContextHolder.getLocale()
            );
            throw new IllegalStateException(message);
        }

        submitCache.put(key, now);
        cleanExpiredKeys(annotation.timeout());
        return joinPoint.proceed();
    }

    private String generateKey(HttpServletRequest request,
                               Method method,
                               Authentication authentication,
                               ProceedingJoinPoint joinPoint) {
        String actor = !isAuthenticatedUser(authentication)
                ? request.getSession().getId()
                : authentication.getName();
        String uri = request.getRequestURI();
        String methodName = method.getName();
        return actor + ":" + request.getMethod() + ":" + uri + ":" + methodName + ":" + requestFingerprint(request, joinPoint);
    }

    private String requestFingerprint(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        String canonical = request.getParameterMap().entrySet().stream()
                .filter(entry -> !isVolatileParameter(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + sortedValues(entry.getValue()))
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "&" + right);
        if (canonical.isBlank()) {
            canonical = methodArguments(joinPoint);
        }
        return sha256(canonical);
    }

    private String sortedValues(String[] values) {
        return Arrays.stream(values == null ? new String[0] : values)
                .sorted(Comparator.nullsFirst(String::compareTo))
                .reduce("", (left, right) -> left.isEmpty() ? String.valueOf(right) : left + "," + right);
    }

    private boolean isVolatileParameter(String name) {
        return "_csrf".equals(name) || "csrf".equalsIgnoreCase(name);
    }

    private String methodArguments(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof CodeSignature signature)) {
            return Arrays.toString(joinPoint.getArgs());
        }
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (isRequestContextArgument(value)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(index < names.length ? names[index] : index)
                    .append('=')
                    .append(String.valueOf(value));
        }
        return builder.toString();
    }

    private boolean isRequestContextArgument(Object value) {
        if (value == null) {
            return false;
        }
        String type = value.getClass().getName();
        return type.startsWith("org.springframework.ui.")
                || type.startsWith("org.springframework.web.servlet.mvc.support.")
                || type.startsWith("jakarta.servlet.")
                || type.startsWith("org.springframework.validation.");
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }

    private void cleanExpiredKeys(int timeout) {
        long now = System.currentTimeMillis();
        submitCache.entrySet().removeIf(entry ->
            (now - entry.getValue()) > timeout * 1000L * 2
        );
    }
}
