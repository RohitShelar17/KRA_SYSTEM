package com.company.kra.common.enums;

public enum CycleStatus {
    DRAFT,
    OPEN,
    IN_REVIEW,
    CLOSED;

    public boolean acceptsInput() {
        return this == OPEN || this == IN_REVIEW;
    }
}
