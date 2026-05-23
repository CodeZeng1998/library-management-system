package com.codezeng.lms.service;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class I18nMessageService {

    private final MessageSource messageSource;

    public I18nMessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    public String enumLabel(String prefix, Enum<?> value) {
        return get(prefix + "." + value.name());
    }
}
