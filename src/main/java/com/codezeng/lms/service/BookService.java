package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

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
