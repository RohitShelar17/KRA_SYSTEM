package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskStatusRequest(
        @NotNull(message = "Select a status")
        TaskStatus status,

        @Min(0) @Max(100)
        Integer completionPct,

        @Size(max = 500)
        String remarks) {
}
