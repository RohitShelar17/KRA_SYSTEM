package com.company.kra.common.enums;

/**
 * A snapshot's position in the appraisal workflow. FINALIZED is a freeze: the
 * engine refuses to recompute it and no adjustment is accepted until someone
 * with the right permission reopens it, with a reason.
 */
public enum PerformanceStatus {
    COMPUTED,
    SELF_ASSESSED,
    MANAGER_REVIEWED,
    HEAD_REVIEWED,
    HR_REVIEWED,
    FINALIZED;

    public boolean isFrozen() {
        return this == FINALIZED;
    }
}
