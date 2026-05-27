package com.codezeng.lms.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CsvImportGuard {

    private static final long MAX_BYTES = 2 * 1024 * 1024;

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
    }
}
