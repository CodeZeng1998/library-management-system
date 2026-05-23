package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
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

    public BookService(BookRepository bookRepository, BookCategoryRepository categoryRepository, OperationLogService operationLogService) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.operationLogService = operationLogService;
    }

    public Page<Book> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return bookRepository.findByDeletedFalse(pageable);
        }
        String value = keyword.trim();
        return bookRepository
                .findByDeletedFalseAndTitleContainingIgnoreCaseOrDeletedFalseAndAuthorContainingIgnoreCaseOrDeletedFalseAndIsbnContainingIgnoreCase(
                        value, value, value, pageable);
    }

    public Page<Book> search(BookSearchCriteria criteria, Pageable pageable) {
        return bookRepository.findAll(toSpecification(criteria), pageable);
    }

    private Specification<Book> toSpecification(BookSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));

            if (StringUtils.hasText(criteria.getKeyword())) {
                String keyword = clean(criteria.getKeyword());
                String like = contains(keyword);
                Join<Book, BookCategory> category = root.join("category", JoinType.LEFT);
                List<Predicate> keywordPredicates = new ArrayList<>();
                for (String variant : variants(keyword)) {
                    String variantLike = contains(variant);
                    keywordPredicates.add(builder.like(builder.lower(root.get("title")), variantLike));
                    keywordPredicates.add(builder.like(builder.lower(root.get("author")), variantLike));
                    keywordPredicates.add(builder.like(builder.lower(root.get("isbn")), variantLike));
                    keywordPredicates.add(builder.like(builder.lower(root.get("publisher")), variantLike));
                    keywordPredicates.add(builder.like(builder.lower(root.get("location")), variantLike));
                    keywordPredicates.add(builder.like(builder.lower(category.get("name")), variantLike));
                }
                categoryIdsByInitials(keyword).forEach(id -> keywordPredicates.add(builder.equal(category.get("id"), id)));
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
        values.add(keyword.replace('亞', '亚').replace('臺', '台').replace('圖', '图').replace('書', '书'));
        values.add(keyword.replace('亚', '亞').replace('台', '臺').replace('图', '圖').replace('书', '書'));
        return values;
    }

    private List<Long> categoryIdsByInitials(String keyword) {
        if (!keyword.matches("[a-z0-9]+")) {
            return List.of();
        }
        return categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(category -> initials(category.getName()).contains(keyword))
                .map(BookCategory::getId)
                .toList();
    }

    private String initials(String value) {
        StringBuilder result = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (ch == '计') {
                result.append('j');
            } else if (ch == '算') {
                result.append('s');
            } else if (ch == '机') {
                result.append('j');
            } else if (ch == '文') {
                result.append('w');
            } else if (ch == '学') {
                result.append('x');
            } else if (ch == '历') {
                result.append('l');
            } else if (ch == '史') {
                result.append('s');
            } else if (Character.isLetterOrDigit(ch)) {
                result.append(Character.toLowerCase(ch));
            }
        }
        return result.toString();
    }

    @Transactional
    public Book save(Book book) {
        if (book.getId() != null) {
            Book existing = bookRepository.findById(book.getId()).orElseThrow();
            book.setCreateTime(existing.getCreateTime());
            book.setDeleted(existing.isDeleted());
        }
        if (book.getAvailableQuantity() > book.getTotalQuantity()) {
            book.setAvailableQuantity(book.getTotalQuantity());
        }
        Book saved = bookRepository.save(book);
        operationLogService.record("图书管理", "保存图书", saved.getTitle());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        if (book.getAvailableQuantity() < book.getTotalQuantity()) {
            throw new IllegalStateException("该图书存在未归还记录，禁止删除");
        }
        book.setDeleted(true);
        bookRepository.save(book);
        operationLogService.record("图书管理", "删除图书", book.getTitle());
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        List<String[]> rows = CsvSupport.readRows(file);
        ImportResult result = new ImportResult();
        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rows.get(i);
            try {
                if (row.length < 6) {
                    result.addError(rowNumber, "至少需要ISBN、书名、作者、出版社、分类、总库存");
                    continue;
                }
                String isbn = required(row, 0, "ISBN");
                if (bookRepository.findByIsbnAndDeletedFalse(isbn).isPresent()) {
                    result.addError(rowNumber, "ISBN已存在：" + isbn);
                    continue;
                }
                Book book = new Book();
                book.setIsbn(isbn);
                book.setTitle(required(row, 1, "书名"));
                book.setAuthor(required(row, 2, "作者"));
                book.setPublisher(required(row, 3, "出版社"));
                book.setCategory(resolveCategory(value(row, 4)));
                int totalQuantity = integer(required(row, 5, "总库存"), "总库存");
                book.setTotalQuantity(totalQuantity);
                book.setAvailableQuantity(integer(value(row, 6), totalQuantity));
                book.setLocation(value(row, 7));
                book.setPrice(decimal(value(row, 8)));
                save(book);
                result.incrementSuccessCount();
            } catch (RuntimeException ex) {
                result.addError(rowNumber, ex.getMessage());
            }
        }
        operationLogService.record("图书管理", "批量导入图书", result.toMessage());
        return result;
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFFISBN,书名,作者,出版社,分类,总库存,可借库存,位置,价格\n");
        for (Book book : bookRepository.findByDeletedFalse(Pageable.unpaged()).getContent()) {
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
                    category.setDescription("批量导入自动创建");
                    return categoryRepository.save(category);
                });
    }

    private String value(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }

    private String required(String[] row, int index, String fieldName) {
        String value = value(row, index);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private int integer(String value, int defaultValue) {
        return StringUtils.hasText(value) ? integer(value, "数字字段") : defaultValue;
    }

    private int integer(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "必须是整数");
        }
    }

    private BigDecimal decimal(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value) : null;
    }
}
