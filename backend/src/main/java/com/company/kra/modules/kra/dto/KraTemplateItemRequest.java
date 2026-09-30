package com.company.kra.modules.kra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record KraTemplateItemRequest(
        Long id,

        @NotBlank(message = "KRA name is required")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "Weightage is required")
        @DecimalMin(value = "0.01", message = "Weightage must be above zero")
        BigDecimal weightage,

        int displayOrder,

        @Valid
        @NotEmpty(message = "A KRA needs at least one KPI")
        List<KpiTemplateItemRequest> kpis) {
}
