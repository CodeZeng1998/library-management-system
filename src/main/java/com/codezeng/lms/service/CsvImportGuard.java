package com.codezeng.lms.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CsvImportGuard {

    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 5_000;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a non-empty CSV file.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("CSV file must not exceed 2 MB.");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only .csv files are supported.");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !contentType.equalsIgnoreCase("text/csv")
                && !contentType.equalsIgnoreCase("application/csv")
                && !contentType.equalsIgnoreCase("application/vnd.ms-excel")
                && !contentType.equalsIgnoreCase("text/plain")) {
            throw new IllegalArgumentException("Uploaded file content type is not supported for CSV import.");
        }
    }

    public void validateRows(int rowCount) {
        if (rowCount <= 1) {
            throw new IllegalArgumentException("CSV file must contain a header row and at least one data row.");
        }
        if (rowCount > MAX_ROWS + 1) {
            throw new IllegalArgumentException("CSV file must not contain more than " + MAX_ROWS + " data rows.");
        }
    }
}
