package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.WorkflowStepType;

public record WorkflowStepResponse(Long id, int stepOrder, WorkflowStepType stepType, String name,
                                   String description, boolean required, Integer slaDays,
                                   boolean active) {
}
