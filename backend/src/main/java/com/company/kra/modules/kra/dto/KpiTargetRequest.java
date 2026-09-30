package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.PeriodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KpiTargetRequest(
        @NotNull(message = "Select a period type")
        PeriodType periodType,

        @NotNull(message = "Enter the period start")
        LocalDate periodStart,

        @NotNull(message = "Enter the period end")
        LocalDate periodEnd,

        @NotNull(message = "Enter the target")
        BigDecimal targetValue,

        BigDecimal stretchValue,

        @Size(max = 400)
        String notes) {
}
