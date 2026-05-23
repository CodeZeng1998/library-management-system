package com.codezeng.lms.domain.enums;

public enum NotificationChannel {
    IN_APP("站内信"),
    EMAIL("邮件");

    private final String label;

    NotificationChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
