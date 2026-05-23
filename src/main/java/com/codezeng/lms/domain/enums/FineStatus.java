package com.codezeng.lms.domain.enums;

public enum FineStatus {
    UNPAID("未缴纳"),
    PAID("已缴纳"),
    WAIVED("已减免");

    private final String label;

    FineStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
