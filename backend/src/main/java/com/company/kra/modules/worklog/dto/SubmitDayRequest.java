package com.company.kra.modules.worklog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SubmitDayRequest(
        @NotNull(message = "Select the date to submit")
        LocalDate workDate,

        @Size(max = 500)
        String comments) {
}
