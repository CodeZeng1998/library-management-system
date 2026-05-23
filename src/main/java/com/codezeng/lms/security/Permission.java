package com.codezeng.lms.security;

import com.codezeng.lms.domain.enums.UserRole;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum Permission {
    DASHBOARD_VIEW(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN, UserRole.READER),
    BOOK_VIEW(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN, UserRole.READER),
    BOOK_EDIT(UserRole.SUPER_ADMIN),
    BOOK_DELETE(UserRole.SUPER_ADMIN),
    BOOK_IMPORT(UserRole.SUPER_ADMIN),
    READER_VIEW(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    READER_EDIT(UserRole.SUPER_ADMIN),
    READER_DELETE(UserRole.SUPER_ADMIN),
    BORROW_MANAGE(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    RESERVATION_MANAGE(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    NOTIFICATION_VIEW(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    FINE_VIEW(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    FINE_PAY(UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    FINE_WAIVE(UserRole.SUPER_ADMIN),
    USER_MANAGE(UserRole.SUPER_ADMIN),
    CONFIG_MANAGE(UserRole.SUPER_ADMIN),
    LOG_VIEW(UserRole.SUPER_ADMIN),
    DATA_ALL(UserRole.SUPER_ADMIN);

    private final Set<UserRole> defaultRoles;

    Permission(UserRole... defaultRoles) {
        this.defaultRoles = defaultRoles.length == 0
                ? EnumSet.noneOf(UserRole.class)
                : EnumSet.copyOf(Arrays.asList(defaultRoles));
    }

    public boolean grantedByDefault(UserRole role) {
        return defaultRoles.contains(role);
    }
}
