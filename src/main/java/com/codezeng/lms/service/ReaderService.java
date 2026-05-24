package com.codezeng.lms.service;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.ReaderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        operationLogService.record("Reader management", "Save reader", saved.getReaderNo() + " " + saved.getName());
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        Reader reader = readerRepository.findById(id).orElseThrow();
        reader.setDeleted(true);
        readerRepository.save(reader);
        operationLogService.record("Reader management", "Delete reader", reader.getReaderNo());
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        return importCsv(file.getBytes());
    }

    @Transactional
    public ImportResult importCsv(byte[] bytes) throws IOException {
        ImportResult result = processCsv(CsvSupport.readRows(bytes), true);
        operationLogService.record("Reader management", "Batch import readers", result.toMessage());
        return result;
    }

    public ImportResult previewCsv(MultipartFile file) throws IOException {
        return processCsv(CsvSupport.readRows(file), false);
    }

    public String importTemplateCsv() {
        return "\uFEFFReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n"
                + "R20260001,Sample Reader,F,13800000000,sample.reader@example.com,S20260001,STUDENT,NORMAL,NORMAL,100.00\n";
    }

    private ImportResult processCsv(List<String[]> rows, boolean persist) {
        ImportResult result = new ImportResult();
        Set<String> seenReaderNos = new LinkedHashSet<>();
        Set<String> seenEmails = new LinkedHashSet<>();
        Set<String> seenIdentityNos = new LinkedHashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rows.get(i);
            try {
                if (row.length < 6) {
                    result.addError(rowNumber, "At least reader number, name, phone, email and identity number are required.", row);
                    continue;
                }
                String readerNo = value(row, 0);
                String email = required(row, 4, "Email");
                String identityNo = required(row, 5, "Identity number");
                if (StringUtils.hasText(readerNo) && !seenReaderNos.add(readerNo)) {
                    result.addError(rowNumber, "Duplicate reader number in this file: " + readerNo, row);
                    continue;
                }
                if (!seenEmails.add(email)) {
                    result.addError(rowNumber, "Duplicate email in this file: " + email, row);
                    continue;
                }
                if (!seenIdentityNos.add(identityNo)) {
                    result.addError(rowNumber, "Duplicate identity number in this file: " + identityNo, row);
                    continue;
                }
                if (StringUtils.hasText(readerNo) && readerRepository.findByReaderNoAndDeletedFalse(readerNo).isPresent()) {
                    result.addError(rowNumber, "Reader number already exists: " + readerNo, row);
                    continue;
                }
                if (readerRepository.existsByEmailAndDeletedFalse(email)) {
                    result.addError(rowNumber, "Email already exists: " + email, row);
                    continue;
                }
                if (readerRepository.existsByIdentityNoAndDeletedFalse(identityNo)) {
                    result.addError(rowNumber, "Identity number already exists: " + identityNo, row);
                    continue;
                }
                Reader reader = new Reader();
                reader.setReaderNo(readerNo);
                reader.setName(required(row, 1, "Name"));
                reader.setGender(value(row, 2));
                reader.setPhone(value(row, 3));
                reader.setEmail(email);
                reader.setIdentityNo(identityNo);
                reader.setReaderType(enumValue(ReaderType.class, value(row, 6), ReaderType.PUBLIC));
                reader.setMemberLevel(enumValue(MemberLevel.class, value(row, 7), MemberLevel.NORMAL));
                reader.setStatus(enumValue(AccountStatus.class, value(row, 8), AccountStatus.NORMAL));
                reader.setDepositAmount(decimal(value(row, 9), BigDecimal.ZERO));
                if (persist) {
                    save(reader);
                }
                result.addSuccess(rowNumber, row, persist ? "Imported" : "Ready to import");
            } catch (RuntimeException ex) {
                result.addError(rowNumber, ex.getMessage(), row);
            }
        }
        return result;
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFFReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n");
        for (Reader reader : readerRepository.findByDeletedFalse(Pageable.unpaged()).getContent()) {
            csv.append(CsvSupport.csv(reader.getReaderNo())).append(',')
                    .append(CsvSupport.csv(reader.getName())).append(',')
                    .append(CsvSupport.csv(reader.getGender())).append(',')
                    .append(CsvSupport.csv(reader.getPhone())).append(',')
                    .append(CsvSupport.csv(reader.getEmail())).append(',')
                    .append(CsvSupport.csv(reader.getIdentityNo())).append(',')
                    .append(CsvSupport.csv(reader.getReaderType().name())).append(',')
                    .append(CsvSupport.csv(reader.getMemberLevel().name())).append(',')
                    .append(CsvSupport.csv(reader.getStatus().name())).append(',')
                    .append(CsvSupport.csv(String.valueOf(reader.getDepositAmount()))).append('\n');
        }
        return csv.toString();
    }

    private String nextReaderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long next = readerRepository.count() + 1;
        return "R" + datePart + String.format("%04d", next);
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

    private BigDecimal decimal(String value, BigDecimal defaultValue) {
        return StringUtils.hasText(value) ? new BigDecimal(value) : defaultValue;
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, String value, T defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value.trim().toUpperCase());
    }
}
