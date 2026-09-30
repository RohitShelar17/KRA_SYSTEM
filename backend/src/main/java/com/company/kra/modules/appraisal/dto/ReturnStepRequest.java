package com.company.kra.modules.appraisal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnStepRequest(
        @NotBlank(message = "Say what needs to change")
        @Size(max = 1000)
        String comments) {
}
