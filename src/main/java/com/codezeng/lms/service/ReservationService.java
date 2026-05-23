package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final OperationLogService operationLogService;

    public ReservationService(
            BookRepository bookRepository,
            ReaderRepository readerRepository,
            ReservationRecordRepository reservationRecordRepository,
            OperationLogService operationLogService) {
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public ReservationRecord reserve(Long bookId, Long readerId) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        Reader reader = readerRepository.findById(readerId).orElseThrow();
        if (book.getAvailableQuantity() > 0) {
            throw new IllegalStateException("当前仍有可借库存，无需预约");
        }
        long queueSize = reservationRecordRepository.countByBookAndStatus(book, ReservationStatus.WAITING);
        if (queueSize >= 5) {
            throw new IllegalStateException("该图书预约队列已满");
        }
        ReservationRecord record = new ReservationRecord();
        record.setBook(book);
        record.setReader(reader);
        record.setReservedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusDays(3));
        record.setStatus(ReservationStatus.WAITING);
        ReservationRecord saved = reservationRecordRepository.save(record);
        operationLogService.record("预约管理", "创建预约", book.getTitle() + " -> " + reader.getReaderNo());
        return saved;
    }

    @Transactional
    public void cancel(Long id) {
        ReservationRecord record = reservationRecordRepository.findById(id).orElseThrow();
        record.setStatus(ReservationStatus.CANCELLED);
        reservationRecordRepository.save(record);
        operationLogService.record("预约管理", "取消预约", record.getBook().getTitle());
    }
}
