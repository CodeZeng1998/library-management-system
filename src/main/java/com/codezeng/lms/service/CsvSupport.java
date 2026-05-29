package com.codezeng.lms.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CsvSupport {

    private CsvSupport() {
    }

    static List<String[]> readRows(MultipartFile file) throws IOException {
        return readRows(file.getBytes());
    }

    static List<String[]> readRows(byte[] bytes) throws IOException {
        List<String[]> rows = new ArrayList<>();
        StringBuilder record = new StringBuilder();
        int recordStartLine = 1;
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (record.isEmpty()) {
                    recordStartLine = lineNumber;
                } else {
                    record.append('\n');
                }
                record.append(line);
                if (!hasOpenQuote(record)) {
                    String completeRecord = record.toString();
                    record.setLength(0);
                    if (!completeRecord.isBlank()) {
                        rows.add(parseLine(completeRecord));
                    }
                }
            }
        }
        if (!record.isEmpty()) {
            throw new IllegalArgumentException("CSV row starting at line " + recordStartLine + " has an unclosed quoted field.");
        }
        return rows;
    }

    public static String csv(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private static String[] parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values.toArray(String[]::new);
    }

    private static boolean hasOpenQuote(CharSequence text) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else {
                    quoted = !quoted;
                }
            }
        }
        return quoted;
    }
}
