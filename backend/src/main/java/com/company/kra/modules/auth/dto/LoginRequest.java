package com.company.kra.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Enter your work email or employee ID")
        @Size(max = 150)
        String identifier,

        @NotBlank(message = "Enter your password")
        @Size(max = 100)
        String password,

        boolean rememberMe) {
}
