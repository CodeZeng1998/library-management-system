package com.codezeng.lms.service;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.repository.ReaderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReaderService {

    private final ReaderRepository readerRepository;
    private final OperationLogService operationLogService;

    public ReaderService(ReaderRepository readerRepository, OperationLogService operationLogService) {
        this.readerRepository = readerRepository;
        this.operationLogService = operationLogService;
    }

    public Page<Reader> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return readerRepository.findByDeletedFalse(pageable);
        }
        String value = keyword.trim();
        return readerRepository
                .findByDeletedFalseAndNameContainingIgnoreCaseOrDeletedFalseAndReaderNoContainingIgnoreCaseOrDeletedFalseAndPhoneContainingIgnoreCase(
                        value, value, value, pageable);
    }

    @Transactional
    public Reader save(Reader reader) {
        if (reader.getId() != null) {
            Reader existing = readerRepository.findById(reader.getId()).orElseThrow();
            reader.setCreateTime(existing.getCreateTime());
            reader.setDeleted(existing.isDeleted());
        }
        if (!StringUtils.hasText(reader.getReaderNo())) {
            reader.setReaderNo(nextReaderNo());
        }
        if (reader.getRegisteredAt() == null) {
            reader.setRegisteredAt(LocalDateTime.now());
        }
        Reader saved = readerRepository.save(reader);
        operationLogService.record("读者管理", "保存读者", saved.getReaderNo() + " " + saved.getName());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        Reader reader = readerRepository.findById(id).orElseThrow();
        reader.setDeleted(true);
        readerRepository.save(reader);
        operationLogService.record("读者管理", "删除读者", reader.getReaderNo());
    }

    private String nextReaderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long next = readerRepository.count() + 1;
        return "R" + datePart + String.format("%04d", next);
    }
}
