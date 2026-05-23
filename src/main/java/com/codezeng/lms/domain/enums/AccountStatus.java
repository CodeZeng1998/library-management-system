package com.codezeng.lms.domain.enums;

public enum AccountStatus {
    PENDING("待审核"),
    NORMAL("正常"),
    FROZEN("冻结"),
    BLACKLISTED("黑名单"),
    DISABLED("禁用");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
