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
import java.util.List;

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

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        List<String[]> rows = CsvSupport.readRows(file);
        ImportResult result = new ImportResult();
        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rows.get(i);
            try {
                if (row.length < 5) {
                    result.addError(rowNumber, "至少需要读者编号、姓名、电话、邮箱、证件号");
                    continue;
                }
                String readerNo = value(row, 0);
                if (StringUtils.hasText(readerNo) && readerRepository.findByReaderNoAndDeletedFalse(readerNo).isPresent()) {
                    result.addError(rowNumber, "读者编号已存在：" + readerNo);
                    continue;
                }
                Reader reader = new Reader();
                reader.setReaderNo(readerNo);
                reader.setName(required(row, 1, "姓名"));
                reader.setGender(value(row, 2));
                reader.setPhone(value(row, 3));
                reader.setEmail(required(row, 4, "邮箱"));
                reader.setIdentityNo(required(row, 5, "证件号"));
                reader.setReaderType(enumValue(ReaderType.class, value(row, 6), ReaderType.PUBLIC));
                reader.setMemberLevel(enumValue(MemberLevel.class, value(row, 7), MemberLevel.NORMAL));
                reader.setStatus(enumValue(AccountStatus.class, value(row, 8), AccountStatus.NORMAL));
                reader.setDepositAmount(decimal(value(row, 9), BigDecimal.ZERO));
                save(reader);
                result.incrementSuccessCount();
            } catch (RuntimeException ex) {
                result.addError(rowNumber, ex.getMessage());
            }
        }
        operationLogService.record("读者管理", "批量导入读者", result.toMessage());
        return result;
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF读者编号,姓名,性别,电话,邮箱,证件号,类型,等级,状态,押金\n");
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
            throw new IllegalArgumentException(fieldName + "不能为空");
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
