package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.ProjectStatus;

import java.time.LocalDate;

public record ProjectResponse(Long id, String code, String name, String description,
                              Long departmentId, String departmentName,
                              Long projectManagerId, String projectManagerName,
                              String clientName, LocalDate startDate, LocalDate endDate,
                              ProjectStatus status, boolean billable, boolean active,
                              long openTaskCount) {
}
