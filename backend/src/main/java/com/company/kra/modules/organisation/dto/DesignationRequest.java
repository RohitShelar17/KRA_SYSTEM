package com.company.kra.modules.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesignationRequest(
        @NotBlank(message = "Designation code is required")
        @Size(max = 30)
        String code,

        @NotBlank(message = "Designation name is required")
        @Size(max = 120)
        String name,

        @Size(max = 20)
        String gradeLevel,

        Long departmentId,

        @Size(max = 400)
        String description) {
}
