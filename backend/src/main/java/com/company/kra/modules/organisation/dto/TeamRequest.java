package com.company.kra.modules.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamRequest(
        @NotNull(message = "Select a department")
        Long departmentId,

        @NotBlank(message = "Team code is required")
        @Size(max = 30)
        String code,

        @NotBlank(message = "Team name is required")
        @Size(max = 120)
        String name,

        @Size(max = 400)
        String description,

        Long teamLeadEmployeeId) {
}
