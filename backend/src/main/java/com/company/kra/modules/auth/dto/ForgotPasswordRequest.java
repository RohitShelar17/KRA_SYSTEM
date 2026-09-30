package com.company.kra.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Enter your work email")
        @Email(message = "Enter a valid email address")
        String email) {
}
