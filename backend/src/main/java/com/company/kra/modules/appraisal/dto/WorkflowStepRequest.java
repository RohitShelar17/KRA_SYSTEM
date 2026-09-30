package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.WorkflowStepType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowStepRequest(
        @NotNull(message = "Select a step type")
        WorkflowStepType stepType,

        @NotBlank(message = "Name the step")
        @Size(max = 120)
        String name,

        @Size(max = 400)
        String description,

        boolean required,

        boolean active,

        @Min(1)
        Integer slaDays) {
}
