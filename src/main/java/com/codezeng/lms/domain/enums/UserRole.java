package com.codezeng.lms.domain.enums;

public enum UserRole {
    SUPER_ADMIN("超级管理员"),
    LIBRARIAN("图书管理员"),
    READER("普通读者");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
