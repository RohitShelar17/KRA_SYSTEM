package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.Recurrence;

public record TaskTemplateResponse(Long id, Long departmentId, String departmentName,
                                   Long taskCategoryId, String taskCategoryName, String colourHex,
                                   String name, String description, Integer defaultEstimatedMinutes,
                                   Priority defaultPriority, Recurrence recurrence,
                                   int displayOrder, boolean active) {
}
