package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.PeriodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KraTemplateRequest(
        @NotBlank(message = "Template code is required")
        @Size(max = 40)
        String code,

        @NotBlank(message = "Template name is required")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        Long designationId,

        Long departmentId,

        @NotNull(message = "Select a review period")
        PeriodType periodType,

        boolean published,

        @Valid
        @NotEmpty(message = "A template needs at least one KRA")
        List<KraTemplateItemRequest> items) {
}
