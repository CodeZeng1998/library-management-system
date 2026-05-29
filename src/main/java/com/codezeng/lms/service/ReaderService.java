package com.codezeng.lms.service;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class ReaderService {

    private final ReaderRepository readerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final FineRecordRepository fineRecordRepository;
    private final ReservationRecordRepository reservationRecordRepository;
    private final NotificationRepository notificationRepository;
    private final OperationLogService operationLogService;
    private final CsvImportGuard csvImportGuard;
    private final I18nMessageService i18n;
    private final SystemConfigService systemConfigService;

    public ReaderService(ReaderRepository readerRepository,
                         BorrowRecordRepository borrowRecordRepository,
                         FineRecordRepository fineRecordRepository,
                         ReservationRecordRepository reservationRecordRepository,
                         NotificationRepository notificationRepository,
                         OperationLogService operationLogService,
                         CsvImportGuard csvImportGuard,
                         I18nMessageService i18n,
                         SystemConfigService systemConfigService) {
        this.readerRepository = readerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.fineRecordRepository = fineRecordRepository;
        this.reservationRecordRepository = reservationRecordRepository;
        this.notificationRepository = notificationRepository;
        this.operationLogService = operationLogService;
        this.csvImportGuard = csvImportGuard;
        this.i18n = i18n;
        this.systemConfigService = systemConfigService;
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

    public Page<Reader> trash(Pageable pageable) {
        return readerRepository.findByDeletedTrue(pageable);
    }

    public Reader getEditable(Long id) {
        return readerRepository.findByIdAndDeletedFalse(id).orElseThrow();
    }

    @Transactional
    public Reader save(Reader reader) {
        validateForSave(reader);
        if (reader.getId() != null) {
            Reader existing = readerRepository.findByIdAndDeletedFalse(reader.getId()).orElseThrow();
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

    private void validateForSave(Reader reader) {
        reader.setReaderNo(trimToNull(reader.getReaderNo()));
        reader.setName(requiredText(reader.getName(), "error.reader.nameRequired"));
        reader.setGender(trimToNull(reader.getGender()));
        reader.setPhone(trimToNull(reader.getPhone()));
        reader.setEmail(requiredText(reader.getEmail(), "error.reader.emailRequired"));
        reader.setIdentityNo(requiredText(reader.getIdentityNo(), "error.reader.identityRequired"));
        if (reader.getReaderType() == null) {
            reader.setReaderType(ReaderType.PUBLIC);
        }
        if (reader.getMemberLevel() == null) {
            reader.setMemberLevel(MemberLevel.NORMAL);
        }
        if (reader.getStatus() == null) {
            reader.setStatus(AccountStatus.NORMAL);
        }
        if (reader.getDepositAmount() == null) {
            reader.setDepositAmount(BigDecimal.ZERO);
        }
        if (reader.getDepositAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(i18n.get("error.reader.negativeDeposit"));
        }
        assertUniqueReaderField(reader, reader.getReaderNo(), readerRepository::findByReaderNo,
                value -> readerRepository.existsByReaderNoAndIdNot(value, reader.getId()),
                "error.reader.duplicateReaderNo", "error.reader.duplicateReaderNoInTrash");
        assertUniqueReaderField(reader, reader.getEmail(), readerRepository::findByEmail,
                value -> readerRepository.existsByEmailAndIdNot(value, reader.getId()),
                "error.reader.duplicateEmail", "error.reader.duplicateEmailInTrash");
        assertUniqueReaderField(reader, reader.getIdentityNo(), readerRepository::findByIdentityNo,
                value -> readerRepository.existsByIdentityNoAndIdNot(value, reader.getId()),
                "error.reader.duplicateIdentityNo", "error.reader.duplicateIdentityNoInTrash");
    }

    private void assertUniqueReaderField(Reader reader,
                                         String value,
                                         Function<String, Optional<Reader>> findByValue,
                                         Predicate<String> existsByValueAndDifferentId,
                                         String activeMessageKey,
                                         String deletedMessageKey) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        boolean duplicate = reader.getId() == null ? findByValue.apply(value).isPresent() : existsByValueAndDifferentId.test(value);
        if (!duplicate) {
            return;
        }
        boolean deletedConflict = findByValue.apply(value)
                .filter(conflict -> !conflict.getId().equals(reader.getId()))
                .filter(Reader::isDeleted)
                .isPresent();
        throw new IllegalArgumentException(i18n.get(deletedConflict ? deletedMessageKey : activeMessageKey));
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

    @Transactional
    public void softDelete(Long id) {
        Reader reader = readerRepository.findByIdAndDeletedFalse(id).orElseThrow();
        ensureReaderCanBeDeleted(reader);
        reader.setDeleted(true);
        readerRepository.save(reader);
        operationLogService.record("Reader management", "Delete reader", reader.getReaderNo());
    }

    @Transactional
    public void purge(Long id) {
        Reader reader = readerRepository.findByIdAndDeletedTrue(id).orElseThrow();
        ensureReaderCanBePurged(reader);
        readerRepository.delete(reader);
        operationLogService.record("Reader management", "Permanently delete reader", reader.getReaderNo());
    }

    @Transactional
    public Reader restore(Long id) {
        Reader reader = readerRepository.findByIdAndDeletedTrue(id).orElseThrow();
        if (StringUtils.hasText(reader.getReaderNo()) && readerRepository.existsByReaderNoAndDeletedFalse(reader.getReaderNo())) {
            throw new IllegalStateException(i18n.get("error.reader.restoreDuplicateReaderNo"));
        }
        if (readerRepository.existsByEmailAndDeletedFalse(reader.getEmail())) {
            throw new IllegalStateException(i18n.get("error.reader.restoreDuplicateEmail"));
        }
        if (readerRepository.existsByIdentityNoAndDeletedFalse(reader.getIdentityNo())) {
            throw new IllegalStateException(i18n.get("error.reader.restoreDuplicateIdentityNo"));
        }
        reader.setDeleted(false);
        Reader restored = readerRepository.save(reader);
        operationLogService.record("Reader management", "Restore reader", restored.getReaderNo());
        return restored;
    }

    private void ensureReaderCanBeDeleted(Reader reader) {
        long activeBorrows = borrowRecordRepository.countByReaderAndStatusInAndDeletedFalse(
                reader, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE));
        if (activeBorrows > 0) {
            throw new IllegalStateException(i18n.get("error.reader.hasActiveBorrows", activeBorrows));
        }
        if (fineRecordRepository.existsByReaderAndStatusAndDeletedFalse(reader, FineStatus.UNPAID)) {
            throw new IllegalStateException(i18n.get("error.reader.hasUnpaidFines"));
        }
        long activeReservations = reservationRecordRepository.countByReaderAndStatusInAndDeletedFalse(
                reader, List.of(ReservationStatus.WAITING, ReservationStatus.NOTIFIED));
        if (activeReservations > 0) {
            throw new IllegalStateException(i18n.get("error.reader.hasActiveReservations", activeReservations));
        }
    }

    private void ensureReaderCanBePurged(Reader reader) {
        long borrowRecords = borrowRecordRepository.countByReader(reader);
        long reservationRecords = reservationRecordRepository.countByReader(reader);
        long fineRecords = fineRecordRepository.countByReader(reader);
        long notificationRecords = notificationRepository.countByReader(reader);
        long references = borrowRecords + reservationRecords + fineRecords + notificationRecords;
        if (references > 0) {
            throw new IllegalStateException(i18n.get("error.reader.purgeHasHistory", references));
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
        operationLogService.record("Reader management", "Batch import readers", result.toMessage());
        return result;
    }

    public ImportResult previewCsv(MultipartFile file) throws IOException {
        List<String[]> rows = CsvSupport.readRows(file);
        csvImportGuard.validateRows(rows.size());
        return processCsv(rows, false);
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
                if (StringUtils.hasText(readerNo) && readerRepository.findByReaderNo(readerNo).isPresent()) {
                    result.addError(rowNumber, csvDuplicateMessage(
                            readerRepository.findByReaderNo(readerNo).orElseThrow(),
                            "error.reader.duplicateReaderNo",
                            "error.reader.duplicateReaderNoInTrash"), row);
                    continue;
                }
                Optional<Reader> emailConflict = readerRepository.findByEmail(email);
                if (emailConflict.isPresent()) {
                    result.addError(rowNumber, csvDuplicateMessage(
                            emailConflict.get(),
                            "error.reader.duplicateEmail",
                            "error.reader.duplicateEmailInTrash"), row);
                    continue;
                }
                Optional<Reader> identityConflict = readerRepository.findByIdentityNo(identityNo);
                if (identityConflict.isPresent()) {
                    result.addError(rowNumber, csvDuplicateMessage(
                            identityConflict.get(),
                            "error.reader.duplicateIdentityNo",
                            "error.reader.duplicateIdentityNoInTrash"), row);
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

    private String csvDuplicateMessage(Reader conflict, String activeMessageKey, String deletedMessageKey) {
        return i18n.get(conflict.isDeleted() ? deletedMessageKey : activeMessageKey);
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFFReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n");
        PageRequest exportPage = PageRequest.of(0, systemConfigService.exportMaxRows(), Sort.by(Sort.Direction.DESC, "createTime"));
        for (Reader reader : readerRepository.findByDeletedFalse(exportPage).getContent()) {
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
