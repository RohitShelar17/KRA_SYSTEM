package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProjectRequest(
        @NotBlank(message = "Project code is required")
        @Size(max = 30)
        String code,

        @NotBlank(message = "Project name is required")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        Long departmentId,

        Long projectManagerId,

        @Size(max = 150)
        String clientName,

        LocalDate startDate,

        LocalDate endDate,

        ProjectStatus status,

        boolean billable) {
}
