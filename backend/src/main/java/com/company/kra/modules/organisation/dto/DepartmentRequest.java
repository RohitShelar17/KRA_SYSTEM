package com.company.kra.modules.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "Department code is required")
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters, numbers, hyphen or underscore only")
        String code,

        @NotBlank(message = "Department name is required")
        @Size(max = 120)
        String name,

        @Size(max = 400)
        String description,

        Long parentDepartmentId,

        Long headEmployeeId) {
}
