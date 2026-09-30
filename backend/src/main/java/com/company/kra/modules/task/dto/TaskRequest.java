package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        /* Omitted for a self-created task: the caller becomes the assignee. */
        Long assigneeId,

        Long projectId,

        @NotNull(message = "Select an activity category")
        Long taskCategoryId,

        Long taskTemplateId,

        @NotBlank(message = "Task title is required")
        @Size(max = 200)
        String title,

        @Size(max = 4000)
        String description,

        Priority priority,

        TaskStatus status,

        @Min(0) @Max(2400)
        Integer estimatedMinutes,

        LocalDate assignedDate,

        LocalDate dueDate,

        @Size(max = 500)
        String remarks) {
}
