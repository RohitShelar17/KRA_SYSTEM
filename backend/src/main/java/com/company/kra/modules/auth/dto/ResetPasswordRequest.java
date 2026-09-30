package com.company.kra.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        String token,

        @NotBlank(message = "Enter a new password")
        @Size(min = 10, max = 72, message = "Password must be at least 10 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$",
                message = "Use upper and lower case letters, a number and a symbol")
        String newPassword) {
}
