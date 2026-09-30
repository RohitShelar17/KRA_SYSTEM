package com.company.kra.modules.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 500)
        String reason) {
}
