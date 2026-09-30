package com.company.kra.modules.performance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdjustScoreRequest(
        @NotNull(message = "Enter the corrected score")
        @DecimalMin(value = "0.00", message = "A score cannot be negative")
        BigDecimal newScore,

        @NotBlank(message = "A reason is required for any manual score change")
        @Size(max = 500)
        String reason) {
}
