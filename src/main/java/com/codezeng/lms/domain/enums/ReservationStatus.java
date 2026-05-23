package com.codezeng.lms.domain.enums;

public enum ReservationStatus {
    WAITING("排队中"),
    NOTIFIED("已通知"),
    COMPLETED("已转借阅"),
    CANCELLED("已取消"),
    EXPIRED("已过期");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
