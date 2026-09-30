package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.MeasurementType;
import com.company.kra.common.enums.TargetDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record KpiTemplateItemRequest(
        Long id,

        @NotBlank(message = "KPI name is required")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "Select how this KPI is measured")
        MeasurementType measurementType,

        @NotNull(message = "Say which direction is good")
        TargetDirection targetDirection,

        @Size(max = 30)
        String unit,

        BigDecimal defaultTargetValue,

        @NotNull(message = "Weightage is required")
        @DecimalMin(value = "0.01", message = "Weightage must be above zero")
        BigDecimal weightage,

        BigDecimal capPercent,

        ActualSource actualSource,

        int displayOrder) {
}
