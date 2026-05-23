package com.codezeng.lms.domain.enums;

public enum ReaderType {
    STUDENT("学生"),
    TEACHER("教师"),
    PUBLIC("社会读者");

    private final String label;

    ReaderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
