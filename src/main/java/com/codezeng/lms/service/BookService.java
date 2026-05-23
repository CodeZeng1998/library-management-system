package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final OperationLogService operationLogService;

    public BookService(BookRepository bookRepository, OperationLogService operationLogService) {
        this.bookRepository = bookRepository;
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
}
