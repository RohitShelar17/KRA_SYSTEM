package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceParameter;
import com.company.kra.common.enums.PerformanceScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Saving this creates a new version and retires the current one. There is no
 * endpoint that edits a formula in place, because a running configuration is
 * referenced by every snapshot computed under it.
 */
public record FormulaConfigRequest(
        @NotNull(message = "Select which grain this formula scores")
        PerformanceScope scopeType,

        @NotBlank(message = "Give this version a name")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String notes,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal minSubmissionRatePct,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal submissionPenaltyPct,

        LocalDate effectiveFrom,

        @Valid
        @NotEmpty(message = "A formula needs at least one parameter")
        List<Parameter> parameters) {

    public record Parameter(
            @NotNull PerformanceParameter parameterCode,
            @Size(max = 150) String label,
            @NotNull @DecimalMin(value = "0.01", message = "Weight must be above zero")
            BigDecimal weightage) {
    }
}
