package com.codezeng.lms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImportResult {

    private int successCount;
    private final List<String> errors = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    public int getSuccessCount() {
        return successCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public void addSuccess(int rowNumber, String[] values, String message) {
        incrementSuccessCount();
        rows.add(new Row(rowNumber, true, message, List.of(values)));
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<Row> getRows() {
        return rows;
    }

    public int getFailureCount() {
        return errors.size();
    }

    public void addError(int rowNumber, String reason) {
        addError(rowNumber, reason, new String[0]);
    }

    public void addError(int rowNumber, String reason, String[] values) {
        errors.add("Row " + rowNumber + ": " + reason);
        rows.add(new Row(rowNumber, false, reason, List.of(values)));
    }

    public String toMessage() {
        if (errors.isEmpty()) {
            return "Import completed. Success: " + successCount + ".";
        }
        return "Import completed. Success: " + successCount + ", failed: " + getFailureCount() + ".";
    }

    public String summaryForPreview() {
        return "Valid rows: " + successCount + ", invalid rows: " + getFailureCount() + ".";
    }

    public String allErrorsText() {
        return errors.stream().collect(Collectors.joining("; "));
    }

    public String errorReportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFFrow,status,message,values\n");
        for (Row row : rows) {
            if (!row.success()) {
                csv.append(CsvSupport.csv(String.valueOf(row.rowNumber()))).append(',')
                        .append(CsvSupport.csv("ERROR")).append(',')
                        .append(CsvSupport.csv(row.message())).append(',')
                        .append(CsvSupport.csv(String.join(" | ", row.values()))).append('\n');
            }
        }
        return csv.toString();
    }

    public record Row(int rowNumber, boolean success, String message, List<String> values) {
    }
}
