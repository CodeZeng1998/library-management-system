package com.codezeng.lms.service;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private int successCount;
    private final List<String> errors = new ArrayList<>();

    public int getSuccessCount() {
        return successCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public List<String> getErrors() {
        return errors;
    }

    public int getFailureCount() {
        return errors.size();
    }

    public void addError(int rowNumber, String reason) {
        errors.add("第" + rowNumber + "行：" + reason);
    }

    public String toMessage() {
        if (errors.isEmpty()) {
            return "导入完成，成功 " + successCount + " 条";
        }
        return "导入完成，成功 " + successCount + " 条，失败 " + getFailureCount() + " 条：" + String.join("；", errors);
    }
}
