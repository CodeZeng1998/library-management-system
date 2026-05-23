package com.codezeng.lms.domain.enums;

public enum NotificationStatus {
    UNREAD("未读"),
    READ("已读");

    private final String label;

    NotificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
