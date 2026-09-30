package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(Long id, Long assigneeId, String assigneeName, String assigneeCode,
                           Long assignedById, String assignedByName,
                           Long projectId, String projectName,
                           Long taskCategoryId, String taskCategoryName, String colourHex,
                           String title, String description, Priority priority, TaskStatus status,
                           int completionPct, Integer estimatedMinutes, int actualMinutes,
                           LocalDate assignedDate, LocalDate dueDate, LocalDateTime completedAt,
                           String remarks, boolean overdue, boolean selfAssigned) {
}
