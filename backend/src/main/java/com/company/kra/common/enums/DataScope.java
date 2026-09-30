package com.company.kra.common.enums;

/**
 * Answers "to whom" a permission applies. A user holds the widest scope
 * granted by any of their roles.
 */
public enum DataScope {
    SELF(0),
    REPORTS(1),
    DEPARTMENT(2),
    ORGANISATION(3);

    private final int rank;

    DataScope(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(DataScope other) {
        return this.rank >= other.rank;
    }

    public static DataScope widest(DataScope a, DataScope b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.rank >= b.rank ? a : b;
    }
}
