package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Recalculation is always an explicit, audited action; nothing recomputes a
 * score as a side effect of opening a page.
 */
public record RecalculateRequest(
        @NotNull(message = "Select which grain to recalculate")
        PerformanceScope scope,

        /** Empty means everyone inside the caller's access scope. */
        List<Long> employeeIds,

        /** For DAILY. Defaults to yesterday. */
        LocalDate date,

        Integer year,

        /** Month 1-12 for MONTHLY, quarter 1-4 for QUARTERLY, ignored for ANNUAL. */
        Integer periodIndex,

        @Size(max = 500)
        String reason) {
}
