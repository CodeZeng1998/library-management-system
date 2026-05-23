package com.codezeng.lms.domain.enums;

public enum MemberLevel {
    NORMAL("普通", 3, 30, 1),
    VIP("VIP", 5, 60, 2),
    SVIP("SVIP", 10, 90, 3);

    private final String label;
    private final int maxBorrowBooks;
    private final int borrowDays;
    private final int maxRenewCount;

    MemberLevel(String label, int maxBorrowBooks, int borrowDays, int maxRenewCount) {
        this.label = label;
        this.maxBorrowBooks = maxBorrowBooks;
        this.borrowDays = borrowDays;
        this.maxRenewCount = maxRenewCount;
    }

    public String getLabel() {
        return label;
    }

    public int getMaxBorrowBooks() {
        return maxBorrowBooks;
    }

    public int getBorrowDays() {
        return borrowDays;
    }

    public int getMaxRenewCount() {
        return maxRenewCount;
    }
}
