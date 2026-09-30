package com.company.kra.modules.kra.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KpiActualRequest(
        @NotNull(message = "Enter the period start")
        LocalDate periodStart,

        @NotNull(message = "Enter the period end")
        LocalDate periodEnd,

        @NotNull(message = "Enter the actual value")
        BigDecimal actualValue,

        @Size(max = 400)
        String note) {
}
