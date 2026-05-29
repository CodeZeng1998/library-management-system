package com.codezeng.lms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流时间窗口（秒）
     */
    int timeWindow() default 60;

    /**
     * 时间窗口内最大请求次数
     */
    int maxRequests() default 100;

    /**
     * 限流类型：IP、USER、GLOBAL
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 自定义限流key前缀
     */
    String keyPrefix() default "";

    enum LimitType {
        IP,      // 按IP限流
        USER,    // 按用户限流
        GLOBAL   // 全局限流
    }
}
