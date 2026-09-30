package com.company.kra.modules.performance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RatingBandRequest(
        @NotBlank(message = "Band name is required")
        @Size(max = 60)
        String name,

        @Size(max = 300)
        String description,

        @NotNull @DecimalMin("0.00")
        BigDecimal minScore,

        @NotNull @DecimalMin("0.00")
        BigDecimal maxScore,

        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Use a hex colour such as #10B981")
        String colourHex) {
}
