package com.codezeng.lms.security;

import com.codezeng.lms.service.I18nMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);
    private final ConcurrentHashMap<String, RateLimitCounter> limitCache = new ConcurrentHashMap<>();
    private final I18nMessageService i18n;

    public RateLimitAspect(I18nMessageService i18n) {
        this.i18n = i18n;
    }

    @Around("@annotation(com.codezeng.lms.security.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = buildKey(rateLimit);
        RateLimitCounter counter = limitCache.computeIfAbsent(key, k -> new RateLimitCounter(rateLimit.timeWindow()));

        if (!counter.tryAcquire(rateLimit.maxRequests())) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new IllegalStateException(i18n.get("error.rateLimit"));
        }

        return joinPoint.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        String prefix = rateLimit.keyPrefix().isEmpty() ? "rate_limit" : rateLimit.keyPrefix();
        String suffix;

        switch (rateLimit.limitType()) {
            case IP:
                suffix = getClientIp();
                break;
            case USER:
                suffix = getCurrentUsername();
                break;
            case GLOBAL:
                suffix = "global";
                break;
            default:
                suffix = "unknown";
        }

        return prefix + ":" + suffix;
    }

    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private static class RateLimitCounter {
        private final long timeWindowMillis;
        private volatile long windowStart;
        private final AtomicInteger count;

        public RateLimitCounter(int timeWindowSeconds) {
            this.timeWindowMillis = timeWindowSeconds * 1000L;
            this.windowStart = System.currentTimeMillis();
            this.count = new AtomicInteger(0);
        }

        public synchronized boolean tryAcquire(int maxRequests) {
            long now = System.currentTimeMillis();

            if (now - windowStart >= timeWindowMillis) {
                windowStart = now;
                count.set(0);
            }

            int current = count.incrementAndGet();
            return current <= maxRequests;
        }
    }
}
