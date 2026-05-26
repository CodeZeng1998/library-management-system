package com.codezeng.lms.security;

import com.codezeng.lms.domain.enums.UserRole;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum Permission {
    DASHBOARD_VIEW("Dashboard menu", UserRole.SUPER_ADMIN, UserRole.LIBRARIAN, UserRole.READER),
    RECOMMENDATION_VIEW("Recommendation center", UserRole.SUPER_ADMIN, UserRole.LIBRARIAN, UserRole.READER),
    BOOK_VIEW("Book menu and query", UserRole.SUPER_ADMIN, UserRole.LIBRARIAN, UserRole.READER),
    BOOK_EDIT("Create or edit books", UserRole.SUPER_ADMIN),
    BOOK_DELETE("Delete books", UserRole.SUPER_ADMIN),
    BOOK_IMPORT("Import books", UserRole.SUPER_ADMIN),
    READER_VIEW("Reader menu and query", UserRole.SUPER_ADMIN),
    READER_EDIT("Create or edit readers", UserRole.SUPER_ADMIN),
    READER_DELETE("Delete readers", UserRole.SUPER_ADMIN),
    BORROW_MANAGE("Borrow and return books", UserRole.SUPER_ADMIN, UserRole.LIBRARIAN),
    RESERVATION_MANAGE("Manage reservations", UserRole.SUPER_ADMIN),
    NOTIFICATION_VIEW("View notifications", UserRole.SUPER_ADMIN),
    FINE_VIEW("Fine menu and query", UserRole.SUPER_ADMIN),
    FINE_PAY("Collect fines", UserRole.SUPER_ADMIN),
    FINE_WAIVE("Waive fines", UserRole.SUPER_ADMIN),
    USER_MANAGE("Manage users and permissions", UserRole.SUPER_ADMIN),
    CONFIG_MANAGE("Manage system configuration", UserRole.SUPER_ADMIN),
    LOG_VIEW("View operation logs", UserRole.SUPER_ADMIN),
    DATA_ALL("View all branch data", UserRole.SUPER_ADMIN);

    private final String label;
    private final Set<UserRole> defaultRoles;

    Permission(String label, UserRole... defaultRoles) {
        this.label = label;
        this.defaultRoles = defaultRoles.length == 0
                ? EnumSet.noneOf(UserRole.class)
                : EnumSet.copyOf(Arrays.asList(defaultRoles));
    }

    public boolean grantedByDefault(UserRole role) {
        return defaultRoles.contains(role);
    }

    public String getLabel() {
        return label;
    }
}
