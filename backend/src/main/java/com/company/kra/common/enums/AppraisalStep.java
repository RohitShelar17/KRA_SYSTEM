package com.company.kra.common.enums;

/**
 * Where an appraisal currently sits. COMPLETED is the terminal state and has
 * no workflow step of its own.
 */
public enum AppraisalStep {
    SELF_ASSESSMENT,
    MANAGER_REVIEW,
    DEPARTMENT_HEAD_REVIEW,
    HR_REVIEW,
    COMPLETED;

    public WorkflowStepType asStepType() {
        return this == COMPLETED ? null : WorkflowStepType.valueOf(name());
    }

    public static AppraisalStep from(WorkflowStepType type) {
        return AppraisalStep.valueOf(type.name());
    }
}
