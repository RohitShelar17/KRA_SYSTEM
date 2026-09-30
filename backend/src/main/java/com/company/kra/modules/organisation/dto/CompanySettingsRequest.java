package com.company.kra.modules.organisation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CompanySettingsRequest(
        @NotNull(message = "Work start time is required")
        LocalTime workStartTime,

        @NotNull(message = "Work end time is required")
        LocalTime workEndTime,

        @Min(0) @Max(240)
        int breakMinutes,

        @NotBlank(message = "Select at least one working day")
        String workingDays,

        boolean worklogApprovalEnabled,

        boolean overtimeAllowed,

        @Min(0) @Max(480)
        int maxOvertimeMinutes,

        @Min(0) @Max(30)
        int backdateWindowDays,

        @NotNull(message = "Work log cut-off time is required")
        LocalTime worklogCutoffTime,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal minSubmissionRatePct) {
}
