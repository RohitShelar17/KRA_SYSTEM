package com.company.kra.modules.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * A manager reopening one date for one person.
 *
 * <p>The reason is required rather than optional. An exception to a rule that
 * nobody has to justify stops being an exception, and the row is the only
 * record of why the window was set aside.</p>
 */
public record UnlockRequest(
        @NotNull(message = "Choose whose day to reopen")
        Long employeeId,

        @NotNull(message = "Choose the date to reopen")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate workDate,

        @NotBlank(message = "Say why this date is being reopened")
        @Size(max = 500, message = "Keep the reason under 500 characters")
        String reason) {
}
