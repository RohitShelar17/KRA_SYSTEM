package com.company.kra.common.enums;

public enum WorkLogDayStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    MODIFICATION_REQUESTED;

    /** Entries may only be added, edited or removed while the day is open. */
    public boolean isEditable() {
        return this == DRAFT || this == MODIFICATION_REQUESTED || this == REJECTED;
    }

    public boolean isAwaitingReview() {
        return this == SUBMITTED || this == UNDER_REVIEW;
    }
}
