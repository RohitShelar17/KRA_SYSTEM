package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.CategoryType;

public record TaskCategoryResponse(Long id, String code, String name, String description,
                                   CategoryType categoryType, boolean productive,
                                   Long departmentId, String departmentName,
                                   String colourHex, int displayOrder, boolean active) {
}
