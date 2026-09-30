package com.company.kra.common.enums;

/** Who is giving the rating, which decides which formula parameter it feeds. */
public enum ReviewerRole {
    MANAGER,
    DEPARTMENT_HEAD,
    HR;

    public static ReviewerRole forStep(AppraisalStep step) {
        return switch (step) {
            case MANAGER_REVIEW -> MANAGER;
            case DEPARTMENT_HEAD_REVIEW -> DEPARTMENT_HEAD;
            case HR_REVIEW -> HR;
            default -> null;
        };
    }
}
