package com.company.kra.modules.task.dto;

import com.company.kra.common.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskCategoryRequest(
        @NotBlank(message = "Category code is required")
        @Size(max = 40)
        String code,

        @NotBlank(message = "Category name is required")
        @Size(max = 120)
        String name,

        @Size(max = 400)
        String description,

        @NotNull(message = "Select a category type")
        CategoryType categoryType,

        boolean productive,

        Long departmentId,

        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Use a hex colour such as #4338CA")
        String colourHex,

        int displayOrder) {
}
