package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.security.DataScopeService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookCategoryRepository categoryRepository;
    private final OperationLogService operationLogService;
    private final DataScopeService dataScopeService;
    private final I18nMessageService i18n;

    public BookService(BookRepository bookRepository,
                       BookCategoryRepository categoryRepository,
                       OperationLogService operationLogService,
                       DataScopeService dataScopeService,
                       I18nMessageService i18n) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.operationLogService = operationLogService;
        this.dataScopeService = dataScopeService;
        this.i18n = i18n;
    }

    public Page<Book> search(String keyword, Pageable pageable) {
        BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setKeyword(keyword);
        return search(criteria, pageable);
    }

    public Page<Book> search(BookSearchCriteria criteria, Pageable pageable) {
        return bookRepository.findAll(toSpecification(criteria), pageable);
    }

    public Book getVisible(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
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
                List<Predicate> keywordPredicates = new ArrayList<>();
                for (String variant : variants(keyword)) {
                    String like = contains(variant);
                    keywordPredicates.add(builder.like(builder.lower(root.get("title")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("author")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("isbn")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("publisher")), like));
                    keywordPredicates.add(builder.like(builder.lower(root.get("location")), like));
                    keywordPredicates.add(builder.like(builder.lower(category.get("name")), like));
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
        if (book.getId() != null) {
            Book existing = bookRepository.findById(book.getId()).orElseThrow();
            dataScopeService.requireAccess(existing);
            book.setCreateTime(existing.getCreateTime());
            book.setDeleted(existing.isDeleted());
        }
        dataScopeService.requireAccess(book);
        if (book.getAvailableQuantity() > book.getTotalQuantity()) {
            book.setAvailableQuantity(book.getTotalQuantity());
        }
        Book saved = bookRepository.save(book);
        operationLogService.record("Book management", "Save book", saved.getTitle());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        dataScopeService.requireAccess(book);
        if (book.getAvailableQuantity() < book.getTotalQuantity()) {
            throw new IllegalStateException(i18n.get("error.book.hasActiveBorrows"));
        }
        book.setDeleted(true);
        bookRepository.save(book);
        operationLogService.record("Book management", "Delete book", book.getTitle());
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        return importCsv(file.getBytes());
    }

    @Transactional
    public ImportResult importCsv(byte[] bytes) throws IOException {
        ImportResult result = processCsv(CsvSupport.readRows(bytes), true);
        operationLogService.record("Book management", "Batch import books", result.toMessage());
        return result;
    }

    public ImportResult previewCsv(MultipartFile file) throws IOException {
        return processCsv(CsvSupport.readRows(file), false);
    }

    public String importTemplateCsv() {
        return "\uFEFFISBN,Title,Author,Publisher,Category,TotalQuantity,AvailableQuantity,Location,Price\n"
                + "9787110000001,Sample Book,Sample Author,Sample Publisher,Computer Science,5,5,Main Library A-01,59.00\n";
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
                String isbn = required(row, 0, "ISBN");
                if (!seenIsbns.add(isbn)) {
                    result.addError(rowNumber, "Duplicate ISBN in this file: " + isbn, row);
                    continue;
                }
                if (bookRepository.findByIsbnAndDeletedFalse(isbn).isPresent()) {
                    result.addError(rowNumber, "ISBN already exists: " + isbn, row);
                    continue;
                }
                Book book = new Book();
                book.setIsbn(isbn);
                book.setTitle(required(row, 1, "Title"));
                book.setAuthor(required(row, 2, "Author"));
                book.setPublisher(required(row, 3, "Publisher"));
                if (persist) {
                    book.setCategory(resolveCategory(value(row, 4)));
                }
                int totalQuantity = integer(required(row, 5, "Total quantity"), "Total quantity");
                book.setTotalQuantity(totalQuantity);
                book.setAvailableQuantity(integer(value(row, 6), totalQuantity));
                book.setLocation(value(row, 7));
                book.setPrice(decimal(value(row, 8)));
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
        StringBuilder csv = new StringBuilder("\uFEFFISBN,Title,Author,Publisher,Category,TotalQuantity,AvailableQuantity,Location,Price\n");
        for (Book book : bookRepository.findAll(dataScopeService.bookScope(), Pageable.unpaged()).getContent()) {
            csv.append(CsvSupport.csv(book.getIsbn())).append(',')
                    .append(CsvSupport.csv(book.getTitle())).append(',')
                    .append(CsvSupport.csv(book.getAuthor())).append(',')
                    .append(CsvSupport.csv(book.getPublisher())).append(',')
                    .append(CsvSupport.csv(book.getCategory() == null ? "" : book.getCategory().getName())).append(',')
                    .append(CsvSupport.csv(String.valueOf(book.getTotalQuantity()))).append(',')
                    .append(CsvSupport.csv(String.valueOf(book.getAvailableQuantity()))).append(',')
                    .append(CsvSupport.csv(book.getLocation())).append(',')
                    .append(CsvSupport.csv(book.getPrice() == null ? "" : String.valueOf(book.getPrice()))).append('\n');
        }
        return csv.toString();
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
