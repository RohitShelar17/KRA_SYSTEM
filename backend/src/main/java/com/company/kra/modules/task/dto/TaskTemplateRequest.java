package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.Recurrence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskTemplateRequest(
        Long departmentId,

        @NotNull(message = "Select an activity category")
        Long taskCategoryId,

        @NotBlank(message = "Template name is required")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @Min(0) @Max(1440)
        Integer defaultEstimatedMinutes,

        Priority defaultPriority,

        Recurrence recurrence,

        int displayOrder) {
}
