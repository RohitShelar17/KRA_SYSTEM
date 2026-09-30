package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Duration is deliberately absent: it is always computed from the time slot,
 * so a client cannot claim two hours for a twenty-minute window.
 */
public record WorkLogEntryRequest(
        @NotNull(message = "Select a date")
        LocalDate workDate,

        @NotNull(message = "Enter a start time")
        LocalTime startTime,

        @NotNull(message = "Enter an end time")
        LocalTime endTime,

        @NotNull(message = "Select an activity category")
        Long taskCategoryId,

        Long projectId,

        Long taskId,

        /** Optional result area this time counts towards. */
        Long employeeKraId,

        @NotBlank(message = "Describe what you worked on")
        @Size(max = 200)
        String title,

        @Size(max = 4000)
        String description,

        Priority priority,

        TaskStatus status,

        @Min(0) @Max(100)
        Integer completionPct,

        boolean billable,

        @Size(max = 500)
        String remarks) {
}
