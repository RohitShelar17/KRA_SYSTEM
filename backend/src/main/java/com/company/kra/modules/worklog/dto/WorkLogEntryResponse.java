package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.CategoryType;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;

import java.time.LocalTime;

public record WorkLogEntryResponse(Long id, LocalTime startTime, LocalTime endTime, int durationMinutes,
                                   Long taskCategoryId, String taskCategoryName, CategoryType categoryType,
                                   String colourHex, Long projectId, String projectName,
                                   Long taskId, String taskTitle, Long employeeKraId, String kraName,
                                   String title, String description,
                                   Priority priority, TaskStatus status, int completionPct,
                                   boolean productive, boolean billable, String remarks) {
}
