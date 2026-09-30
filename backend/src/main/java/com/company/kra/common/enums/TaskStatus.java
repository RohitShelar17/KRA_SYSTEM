package com.company.kra.common.enums;

public enum TaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    ON_HOLD,
    BLOCKED,
    CANCELLED;

    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Counts toward completion rate in the Phase 4 score. */
    public boolean isCountableAsCompleted() {
        return this == COMPLETED;
    }
}
