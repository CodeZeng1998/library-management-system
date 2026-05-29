package com.codezeng.lms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreventDuplicateSubmit {

    /**
     * Duplicate submission time window in seconds.
     */
    int timeout() default 5;

    /**
     * Whether an authenticated principal is required for duplicate detection.
     */
    boolean requireLogin() default true;

    /**
     * Message key used when a duplicate submission is blocked.
     */
    String messageKey() default "error.duplicateSubmit";
}
