package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.BookTag;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BookTagRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.security.DataScopeService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookCategoryRepository categoryRepository;
    private final BookTagRepository tagRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final OperationLogService operationLogService;
    private final DataScopeService dataScopeService;
    private final CsvImportGuard csvImportGuard;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public BookService(BookRepository bookRepository,
                       BookCategoryRepository categoryRepository,
                       BookTagRepository tagRepository,
                       BorrowRecordRepository borrowRecordRepository,
                       ReservationRecordRepository reservationRecordRepository,
                       OperationLogService operationLogService,
                       DataScopeService dataScopeService,
                       CsvImportGuard csvImportGuard,
                       I18nMessageService i18n,
                       SystemConfigService systemConfigService) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.operationLogService = operationLogService;
        this.dataScopeService = dataScopeService;
        this.csvImportGuard = csvImportGuard;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
    }

    public Page<Book> search(String keyword, Pageable pageable) {
        BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setKeyword(keyword);
        return search(criteria, pageable);
    }

    public Page<Book> search(BookSearchCriteria criteria, Pageable pageable) {
        return bookRepository.findAll(toSpecification(criteria), pageable);
    }

    public Page<Book> trash(Pageable pageable) {
        return bookRepository.findAll(dataScopeService.deletedBookScope(), pageable);
    }

    public Book getVisible(Long id) {
        Book book = bookRepository.findByIdAndDeletedFalse(id).orElseThrow();
        dataScopeService.requireAccess(book);
        return book;
    }

    private Specification<Book> toSpecification(BookSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(dataScopeService.bookScope().toPredicate(root, query, builder));

            if (StringUtils.hasText(criteria.getKeyword())) {
                String keyword = clean(criteria.getKeyword());
                Join<Book, BookCategory> category = root.join("category", JoinType.LEFT);
                Join<Book, BookTag> tag = root.joinSet("tags", JoinType.LEFT);
                query.distinct(true);
                List<Predicate> keywordPredicates = new ArrayList<>();
                for (String variant : variants(keyword)) {
                    String like = contains(variant);
                    keywordPredicates.add(builder.like(builder.lower(root.get("title")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("author")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("isbn")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("publisher")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("location")), like));
                    keywordPredicates.add(builder.like(builder.lower(category.get("name")), like));
                    keywordPredicates.add(builder.like(builder.lower(tag.get("name")), like));
                }
                predicates.add(builder.or(keywordPredicates.toArray(Predicate[]::new)));
            }
            if (StringUtils.hasText(criteria.getTitle())) {
                predicates.add(textPredicate(builder, builder.lower(root.get("title")), criteria.getTitle(), criteria.getTitleMatch()));
            }
            if (StringUtils.hasText(criteria.getAuthor())) {
                predicates.add(textPredicate(builder, builder.lower(root.get("author")), criteria.getAuthor(), criteria.getAuthorMatch()));
            }
            if (criteria.getCategoryIds() != null && !criteria.getCategoryIds().isEmpty()) {
                Join<Book, BookCategory> category = root.join("category", JoinType.LEFT);
                predicates.add(category.get("id").in(criteria.getCategoryIds()));
            }
            if (criteria.getTagIds() != null && !criteria.getTagIds().isEmpty()) {
                Join<Book, BookTag> tag = root.joinSet("tags", JoinType.LEFT);
                query.distinct(true);
                predicates.add(tag.get("id").in(criteria.getTagIds()));
            }
            if (criteria.getPublishYearFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("publishDate"), LocalDate.of(criteria.getPublishYearFrom(), 1, 1)));
            }
            if (criteria.getPublishYearTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("publishDate"), LocalDate.of(criteria.getPublishYearTo(), 12, 31)));
            }
            if (criteria.isAvailableOnly()) {
                predicates.add(builder.greaterThan(root.get("availableQuantity"), 0));
            }
            if (StringUtils.hasText(criteria.getLocation())) {
                predicates.add(builder.equal(root.get("location"), criteria.getLocation().trim()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate textPredicate(jakarta.persistence.criteria.CriteriaBuilder builder,
                                    jakarta.persistence.criteria.Expression<String> field,
                                    String value,
                                    String matchMode) {
        String cleaned = clean(value);
        if ("exact".equals(matchMode)) {
            return builder.equal(field, cleaned);
        }
        return builder.like(field, contains(cleaned));
    }

    private String clean(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String contains(String value) {
        return "%" + value + "%";
    }

    private Set<String> variants(String keyword) {
        Set<String> values = new LinkedHashSet<>();
        values.add(keyword);
        return values;
    }

    @Transactional
    public Book save(Book book) {
        validateForSave(book);
        if (book.getId() != null) {
            Book existing = bookRepository.findByIdAndDeletedFalse(book.getId()).orElseThrow();
            dataScopeService.requireAccess(existing);
            int activeCopies = Math.max(existing.getTotalQuantity() - existing.getAvailableQuantity(), 0);
            if (book.getTotalQuantity() < activeCopies) {
                throw new IllegalArgumentException(i18n.get("error.book.totalBelowBorrowed", activeCopies));
            }
            if (book.getAvailableQuantity() > book.getTotalQuantity() - activeCopies) {
                throw new IllegalArgumentException(i18n.get("error.book.availableExceedsFree", activeCopies));
            }
            book.setCreateTime(existing.getCreateTime());
            book.setDeleted(existing.isDeleted());
            book.setBorrowCount(existing.getBorrowCount());
        }
        dataScopeService.requireAccess(book);
        Book saved = bookRepository.save(book);
        operationLogService.record("Book management", "Save book", saved.getTitle());
        return saved;
    }

    private void validateForSave(Book book) {
        book.setIsbn(requiredText(book.getIsbn(), "error.book.isbnRequired"));
        book.setTitle(requiredText(book.getTitle(), "error.book.titleRequired"));
        book.setAuthor(requiredText(book.getAuthor(), "error.book.authorRequired"));
        book.setPublisher(requiredText(book.getPublisher(), "error.book.publisherRequired"));
        book.setLocation(trimToNull(book.getLocation()));
        book.setSubtitle(trimToNull(book.getSubtitle()));
        book.setTranslator(trimToNull(book.getTranslator()));
        book.setBinding(trimToNull(book.getBinding()));
        book.setSummary(trimToNull(book.getSummary()));
        book.setCoverUrl(trimToNull(book.getCoverUrl()));

        assertUniqueIsbnForSave(book);
        if (book.getTotalQuantity() < 0 || book.getAvailableQuantity() < 0) {
            throw new IllegalArgumentException(i18n.get("error.book.negativeQuantity"));
        }
        if (book.getAvailableQuantity() > book.getTotalQuantity()) {
            throw new IllegalArgumentException(i18n.get("error.book.availableExceedsTotal"));
        }
        if (book.getPrice() != null && book.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(i18n.get("error.book.negativePrice"));
        }
        if (book.getPages() != null && book.getPages() < 0) {
            throw new IllegalArgumentException(i18n.get("error.book.negativePages"));
        }
    }

    private String requiredText(String value, String messageKey) {
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

    public Set<BookTag> resolveTags(List<Long> tagIds, String tagNames) {
        Set<BookTag> tags = new LinkedHashSet<>();
        if (tagIds != null && !tagIds.isEmpty()) {
            tagRepository.findByIdInAndDeletedFalse(tagIds).stream()
                    .sorted(Comparator.comparing(BookTag::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(tags::add);
        }
        parseTagNames(tagNames).forEach(name -> tags.add(resolveTag(name)));
        return tags;
    }

    private void assertUniqueIsbnForSave(Book book) {
        boolean duplicateIsbn = book.getId() == null
                ? bookRepository.findByIsbn(book.getIsbn()).isPresent()
                : bookRepository.existsByIsbnAndIdNot(book.getIsbn(), book.getId());
        if (!duplicateIsbn) {
            return;
        }
        boolean deletedConflict = bookRepository.findByIsbn(book.getIsbn())
                .filter(Book::isDeleted)
                .isPresent();
        throw new IllegalArgumentException(i18n.get(deletedConflict
                ? "error.book.duplicateIsbnInTrash"
                : "error.book.duplicateIsbn"));
    }

    @Transactional
    public void softDelete(Long id) {
        Book book = bookRepository.findByIdAndDeletedFalse(id).orElseThrow();
        dataScopeService.requireAccess(book);
        long activeReservations = reservationRecordRepository.countByBookAndStatusInAndDeletedFalse(
                book, List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED));
        if (activeReservations > 0) {
            throw new IllegalStateException(i18n.get("error.book.hasActiveReservations", activeReservations));
        }
        long activeBorrows = borrowRecordRepository.countByBookAndStatusInAndDeletedFalse(
                book, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE));
        if (activeBorrows > 0) {
            throw new IllegalStateException(i18n.get("error.book.hasActiveBorrows"));
        }
        book.setDeleted(true);
        bookRepository.save(book);
        operationLogService.record("Book management", "Delete book", book.getTitle());
    }

    @Transactional
    public void purge(Long id) {
        Book book = bookRepository.findByIdAndDeletedTrue(id).orElseThrow();
        dataScopeService.requireAccess(book);
        ensureBookCanBePurged(book);
        bookRepository.delete(book);
        operationLogService.record("Book management", "Permanently delete book", book.getTitle());
    }

    @Transactional
    public Book restore(Long id) {
        Book book = bookRepository.findByIdAndDeletedTrue(id).orElseThrow();
        dataScopeService.requireAccess(book);
        if (bookRepository.findByIsbnAndDeletedFalse(book.getIsbn()).isPresent()) {
            throw new IllegalStateException(i18n.get("error.book.restoreDuplicateIsbn"));
        }
        book.setDeleted(false);
        Book restored = bookRepository.save(book);
        operationLogService.record("Book management", "Restore book", restored.getTitle());
        return restored;
    }

    private void ensureBookCanBePurged(Book book) {
        long borrowRecords = borrowRecordRepository.countByBook(book);
        long reservationRecords = reservationRecordRepository.countByBook(book);
        long references = borrowRecords + reservationRecords;
        if (references > 0) {
            throw new IllegalStateException(i18n.get("error.book.purgeHasHistory", references));
        }
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        return importCsv(file.getBytes());
    }

    @Transactional
    public ImportResult importCsv(byte[] bytes) throws IOException {
        List<String[]> rows = CsvSupport.readRows(bytes);
        csvImportGuard.validateRows(rows.size());
        ImportResult result = processCsv(rows, true);
        operationLogService.record("Book management", "Batch import books", result.toMessage());
        return result;
    }

    public ImportResult previewCsv(MultipartFile file) throws IOException {
        List<String[]> rows = CsvSupport.readRows(file);
        csvImportGuard.validateRows(rows.size());
        return processCsv(rows, false);
    }

    public String importTemplateCsv() {
        return "\uFEFFISBN,Title,Author,Publisher,Category,Tags,TotalQuantity,AvailableQuantity,Location,Price\n"
                + "9787110000001,Sample Book,Sample Author,Sample Publisher,Computer Science,\"AI;Reference\",5,5,Main Library A-01,59.00\n";
    }

    private ImportResult processCsv(List<String[]> rows, boolean persist) {
        ImportResult result = new ImportResult();
        Set<String> seenIsbns = new LinkedHashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rows.get(i);
            try {
                if (row.length < 6) {
                    result.addError(rowNumber, "At least ISBN, title, author, publisher, category and total quantity are required.", row);
                    continue;
                }
                boolean hasTagsColumn = row.length >= 10;
                String isbn = required(row, 0, "ISBN");
                if (!seenIsbns.add(isbn)) {
                    result.addError(rowNumber, "Duplicate ISBN in this file: " + isbn, row);
                    continue;
                }
                Optional<Book> isbnConflict = bookRepository.findByIsbn(isbn);
                if (isbnConflict.isPresent()) {
                    result.addError(rowNumber, i18n.get(isbnConflict.get().isDeleted()
                            ? "error.book.duplicateIsbnInTrash"
                            : "error.book.duplicateIsbn"), row);
                    continue;
                }
                Book book = new Book();
                book.setIsbn(isbn);
                book.setTitle(required(row, 1, "Title"));
                book.setAuthor(required(row, 2, "Author"));
                book.setPublisher(required(row, 3, "Publisher"));
                if (persist) {
                    book.setCategory(resolveCategory(value(row, 4)));
                    book.setTags(resolveTags(null, hasTagsColumn ? value(row, 5) : ""));
                }
                int totalQuantity = integer(required(row, hasTagsColumn ? 6 : 5, "Total quantity"), "Total quantity");
                book.setTotalQuantity(totalQuantity);
                book.setAvailableQuantity(integer(value(row, hasTagsColumn ? 7 : 6), totalQuantity));
                book.setLocation(value(row, hasTagsColumn ? 8 : 7));
                book.setPrice(decimal(value(row, hasTagsColumn ? 9 : 8)));
                dataScopeService.requireAccess(book);
                if (persist) {
                    save(book);
                }
                result.addSuccess(rowNumber, row, persist ? "Imported" : "Ready to import");
            } catch (RuntimeException ex) {
                result.addError(rowNumber, ex.getMessage(), row);
            }
        }
        return result;
    }

    public String exportCsv() {
        return exportCsv(new BookSearchCriteria());
    }

    public String exportCsv(BookSearchCriteria criteria) {
        StringBuilder csv = new StringBuilder("\uFEFFISBN,Title,Author,Publisher,Category,Tags,TotalQuantity,AvailableQuantity,Location,Price\n");
        PageRequest exportPage = PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "createTime"));
        BookSearchCriteria safeCriteria = criteria == null ? new BookSearchCriteria() : criteria;
        for (Book book : bookRepository.findAll(toSpecification(safeCriteria), exportPage).getContent()) {
            csv.append(CsvSupport.csv(book.getIsbn())).append(',')
                    .append(CsvSupport.csv(book.getTitle())).append(',')
                    .append(CsvSupport.csv(book.getAuthor())).append(',')
                    .append(CsvSupport.csv(book.getPublisher())).append(',')
                    .append(CsvSupport.csv(book.getCategory() == null ? "" : book.getCategory().getName())).append(',')
                    .append(CsvSupport.csv(tagText(book))).append(',')
                    .append(CsvSupport.csv(String.valueOf(book.getTotalQuantity()))).append(',')
                    .append(CsvSupport.csv(String.valueOf(book.getAvailableQuantity()))).append(',')
                    .append(CsvSupport.csv(book.getLocation())).append(',')
                    .append(CsvSupport.csv(book.getPrice() == null ? "" : String.valueOf(book.getPrice()))).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public BatchOperationResult batchSoftDelete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new BatchOperationResult(0, List.of());
        }
        int successCount = 0;
        List<String> failures = new ArrayList<>();
        for (Long id : ids.stream().filter(item -> item != null).distinct().toList()) {
            try {
                softDelete(id);
                successCount++;
            } catch (RuntimeException ex) {
                failures.add("#" + id + ": " + ex.getMessage());
            }
        }
        operationLogService.record("Book management", "Batch delete books",
                "success=" + successCount + ", failed=" + failures.size());
        return new BatchOperationResult(successCount, failures);
    }

    public record BatchOperationResult(int successCount, List<String> failures) {
        public int failureCount() {
            return failures == null ? 0 : failures.size();
        }

        public boolean hasFailures() {
            return failureCount() > 0;
        }
    }

    private BookCategory resolveCategory(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return categoryRepository.findByNameAndDeletedFalse(name)
                .orElseGet(() -> {
                    BookCategory category = new BookCategory();
                    category.setName(name);
                    category.setDescription("Created by CSV import");
                    return categoryRepository.save(category);
                });
    }

    private BookTag resolveTag(String name) {
        String normalized = name.trim();
        return tagRepository.findByNameIgnoreCaseAndDeletedFalse(normalized)
                .orElseGet(() -> {
                    BookTag tag = new BookTag();
                    tag.setName(normalized);
                    tag.setDescription("Created by book metadata");
                    return tagRepository.save(tag);
                });
    }

    private List<String> parseTagNames(String tagNames) {
        if (!StringUtils.hasText(tagNames)) {
            return List.of();
        }
        return Arrays.stream(tagNames.split("[,;，；]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .toList();
    }

    private String tagText(Book book) {
        if (book.getTags() == null || book.getTags().isEmpty()) {
            return "";
        }
        return book.getTags().stream()
                .filter(tag -> !tag.isDeleted())
                .map(BookTag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(";"));
    }

    private String value(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }

    private String required(String[] row, int index, String fieldName) {
        String value = value(row, index);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private int integer(String value, int defaultValue) {
        return StringUtils.hasText(value) ? integer(value, "Numeric field") : defaultValue;
    }

    private int integer(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
    }

    private BigDecimal decimal(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value) : null;
    }
}
