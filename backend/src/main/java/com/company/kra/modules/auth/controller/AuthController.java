package com.company.kra.modules.auth.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.auth.dto.ChangePasswordRequest;
import com.company.kra.modules.auth.dto.CurrentUserResponse;
import com.company.kra.modules.auth.dto.ForgotPasswordRequest;
import com.company.kra.modules.auth.dto.LoginRequest;
import com.company.kra.modules.auth.dto.RefreshTokenRequest;
import com.company.kra.modules.auth.dto.ResetPasswordRequest;
import com.company.kra.modules.auth.dto.TokenResponse;
import com.company.kra.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Sign in, token rotation and password lifecycle")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with work email or username")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request, httpRequest)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request.refreshToken(), httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @GetMapping("/me")
    @Operation(summary = "Profile, roles, permissions and access scope of the caller")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.of(authService.currentUser()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change own password; all sessions are revoked")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.message("Password changed. Sign in again."));
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Request a password reset link")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If that address has an account, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Set a new password using a reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.message("Password updated. Sign in with your new password."));
    }
}
