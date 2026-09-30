package com.company.kra.modules.employee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ManagerChangeRequest(
        @NotNull(message = "Select the new reporting manager")
        Long managerId,

        @NotNull(message = "Effective date is required")
        LocalDate effectiveFrom,

        @Size(max = 500)
        String reason) {
}
