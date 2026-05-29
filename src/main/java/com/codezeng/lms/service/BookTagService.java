package com.codezeng.lms.service;

import com.codezeng.lms.domain.BookTag;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BookTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookTagService {

    private final BookTagRepository tagRepository;
    private final BookRepository bookRepository;
    private final OperationLogService operationLogService;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public BookTagService(BookTagRepository tagRepository,
                          BookRepository bookRepository,
                          OperationLogService operationLogService,
                          I18nMessageService i18n,
                          SystemConfigService systemConfigService) {
        this.tagRepository = tagRepository;
        this.bookRepository = bookRepository;
        this.operationLogService = operationLogService;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<BookTag> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return tagRepository.findByDeletedFalse(pageable);
        }
        return tagRepository.findByDeletedFalseAndNameContainingIgnoreCase(keyword.trim(), pageable);
    }

    public BookTag getEditable(Long id) {
        return tagRepository.findById(id)
                .filter(tag -> !tag.isDeleted())
                .orElseThrow();
    }

    public String exportCsv(String keyword) {
        Page<BookTag> tags = search(keyword,
                PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.ASC, "name")));
        Map<Long, Long> usageCounts = usageCounts(tags.getContent());
        StringBuilder csv = new StringBuilder("\uFEFFName,Description,Book Count,Updated At\n");
        for (BookTag tag : tags.getContent()) {
            csv.append(CsvSupport.csv(tag.getName())).append(',')
                    .append(CsvSupport.csv(tag.getDescription())).append(',')
                    .append(CsvSupport.csv(String.valueOf(usageCounts.getOrDefault(tag.getId(), 0L)))).append(',')
                    .append(CsvSupport.csv(tag.getUpdateTime() == null ? "" : tag.getUpdateTime().toString())).append('\n');
        }
        operationLogService.record(i18n.get("log.module.tag"), i18n.get("log.tag.export"), "Rows: " + tags.getNumberOfElements());
        return csv.toString();
    }

    @Transactional
    public BookTag save(BookTag form) {
        BookTag tag = form.getId() == null ? new BookTag() : getEditable(form.getId());
        tag.setName(required(form.getName()));
        tag.setDescription(trimToNull(form.getDescription()));
        validate(tag);
        BookTag saved = tagRepository.save(tag);
        operationLogService.record(i18n.get("log.module.tag"), i18n.get("log.tag.save"), saved.getName());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        BookTag tag = getEditable(id);
        long books = bookRepository.countByTagsContainingAndDeletedFalse(tag);
        if (books > 0) {
            throw new IllegalStateException(i18n.get("error.tag.hasBooks", books));
        }
        tag.setDeleted(true);
        tagRepository.save(tag);
        operationLogService.record(i18n.get("log.module.tag"), i18n.get("log.tag.delete"), tag.getName());
    }

    public long usageCount(BookTag tag) {
        return bookRepository.countByTagsContainingAndDeletedFalse(tag);
    }

    public Map<Long, Long> usageCounts(Collection<BookTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        return bookRepository.countActiveBooksByTagIds(tags.stream()
                        .map(BookTag::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private void validate(BookTag tag) {
        boolean duplicate = tag.getId() == null
                ? tagRepository.existsByNameIgnoreCaseAndDeletedFalse(tag.getName())
                : tagRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(tag.getName(), tag.getId());
        if (duplicate) {
            throw new IllegalArgumentException(i18n.get("error.tag.duplicateName"));
        }
    }

    private String required(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(i18n.get("error.tag.nameRequired"));
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
