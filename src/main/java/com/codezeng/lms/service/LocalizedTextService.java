package com.codezeng.lms.service;

import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.LocalizedText;
import com.codezeng.lms.repository.LocalizedTextRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class LocalizedTextService {

    private final LocalizedTextRepository localizedTextRepository;

    public LocalizedTextService(LocalizedTextRepository localizedTextRepository) {
        this.localizedTextRepository = localizedTextRepository;
    }

    public String resolve(String entityType, Long entityId, String fieldKey, String fallbackText) {
        Locale locale = LocaleContextHolder.getLocale();
        String translated = lookup(entityType, entityId, fieldKey, locale);
        if (StringUtils.hasText(translated)) {
            return translated;
        }
        if (!Locale.SIMPLIFIED_CHINESE.equals(locale)) {
            translated = lookup(entityType, entityId, fieldKey, Locale.SIMPLIFIED_CHINESE);
            if (StringUtils.hasText(translated)) {
                return translated;
            }
        }
        return fallbackText;
    }

    public String categoryName(BookCategory category) {
        if (category == null) {
            return "";
        }
        return resolve("book_category", category.getId(), "name", category.getName());
    }

    private String lookup(String entityType, Long entityId, String fieldKey, Locale locale) {
        if (entityId == null) {
            return null;
        }
        String exact = localizedTextRepository
                .findFirstByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(entityType, entityId, fieldKey, locale.toString())
                .map(LocalizedText::getText)
                .orElse(null);
        if (StringUtils.hasText(exact)) {
            return exact;
        }
        if (StringUtils.hasText(locale.getLanguage()) && StringUtils.hasText(locale.getCountry())) {
            return localizedTextRepository
                    .findFirstByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(entityType, entityId, fieldKey, locale.getLanguage())
                    .map(LocalizedText::getText)
                    .orElse(null);
        }
        return localizedTextRepository
                .findFirstByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(entityType, entityId, fieldKey, locale.getLanguage())
                .map(LocalizedText::getText)
                .orElse(null);
    }
}
