package com.codezeng.lms.service;

import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookCategoryService {

    private final BookCategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final OperationLogService operationLogService;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public BookCategoryService(BookCategoryRepository categoryRepository,
                               BookRepository bookRepository,
                               OperationLogService operationLogService,
                               I18nMessageService i18n,
                               SystemConfigService systemConfigService) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.operationLogService = operationLogService;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<BookCategory> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return categoryRepository.findByDeletedFalse(pageable);
        }
        return categoryRepository.findByDeletedFalseAndNameContainingIgnoreCase(keyword.trim(), pageable);
    }

    public List<BookCategory> parents(Long currentId) {
        return categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(category -> currentId == null || !category.getId().equals(currentId))
                .toList();
    }

    public BookCategory getEditable(Long id) {
        return categoryRepository.findByIdAndDeletedFalse(id).orElseThrow();
    }

    public String exportCsv(String keyword) {
        Page<BookCategory> categories = search(keyword,
                PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.ASC, "name")));
        Map<Long, Long> usageCounts = usageCounts(categories.getContent());
        StringBuilder csv = new StringBuilder("\uFEFFName,Parent,Description,Book Count,Updated At\n");
        for (BookCategory category : categories.getContent()) {
            csv.append(CsvSupport.csv(category.getName())).append(',')
                    .append(CsvSupport.csv(category.getParent() == null ? "" : category.getParent().getName())).append(',')
                    .append(CsvSupport.csv(category.getDescription())).append(',')
                    .append(CsvSupport.csv(String.valueOf(usageCounts.getOrDefault(category.getId(), 0L)))).append(',')
                    .append(CsvSupport.csv(category.getUpdateTime() == null ? "" : category.getUpdateTime().toString())).append('\n');
        }
        operationLogService.record(i18n.get("log.module.category"), i18n.get("log.category.export"), "Rows: " + categories.getNumberOfElements());
        return csv.toString();
    }

    public Map<Long, Long> usageCounts(Collection<BookCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyMap();
        }
        return bookRepository.countActiveBooksByCategoryIds(categories.stream()
                        .map(BookCategory::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Transactional
    public BookCategory save(BookCategory form, Long parentId) {
        BookCategory category = form.getId() == null ? new BookCategory() : getEditable(form.getId());
        category.setName(required(form.getName(), "error.category.nameRequired"));
        category.setDescription(trimToNull(form.getDescription()));
        category.setParent(resolveParent(parentId, category.getId()));
        validate(category);
        BookCategory saved = categoryRepository.save(category);
        operationLogService.record(i18n.get("log.module.category"), i18n.get("log.category.save"), saved.getName());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        BookCategory category = getEditable(id);
        ensureCanDelete(category);
        category.setDeleted(true);
        categoryRepository.save(category);
        operationLogService.record(i18n.get("log.module.category"), i18n.get("log.category.delete"), category.getName());
    }

    private BookCategory resolveParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return null;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new IllegalArgumentException(i18n.get("error.category.parentSelf"));
        }
        return categoryRepository.findByIdAndDeletedFalse(parentId).orElseThrow();
    }

    private void validate(BookCategory category) {
        boolean duplicate = category.getId() == null
                ? categoryRepository.existsByNameAndDeletedFalse(category.getName())
                : categoryRepository.existsByNameAndDeletedFalseAndIdNot(category.getName(), category.getId());
        if (duplicate) {
            throw new IllegalArgumentException(i18n.get("error.category.duplicateName"));
        }
    }

    private void ensureCanDelete(BookCategory category) {
        long books = bookRepository.countByCategoryAndDeletedFalse(category);
        if (books > 0) {
            throw new IllegalStateException(i18n.get("error.category.hasBooks", books));
        }
        if (categoryRepository.existsByParentAndDeletedFalse(category)) {
            throw new IllegalStateException(i18n.get("error.category.hasChildren"));
        }
    }

    private String required(String value, String messageKey) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(i18n.get(messageKey));
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
