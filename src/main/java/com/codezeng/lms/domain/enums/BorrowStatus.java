package com.codezeng.lms.domain.enums;

public enum BorrowStatus {
    BORROWED("借阅中"),
    RETURNED("已归还"),
    OVERDUE("已逾期"),
    LOST("已丢失"),
    DAMAGED("已损坏");

    private final String label;

    BorrowStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
