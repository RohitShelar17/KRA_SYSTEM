package com.company.kra.modules.auth.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.UserStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.auth.dto.ChangePasswordRequest;
import com.company.kra.modules.auth.dto.CurrentUserResponse;
import com.company.kra.modules.auth.dto.ForgotPasswordRequest;
import com.company.kra.modules.auth.dto.LoginRequest;
import com.company.kra.modules.auth.dto.ResetPasswordRequest;
import com.company.kra.modules.auth.dto.TokenResponse;
import com.company.kra.modules.auth.entity.RefreshToken;
import com.company.kra.modules.auth.repository.RefreshTokenRepository;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.CustomUserDetailsService;
import com.company.kra.security.JwtProperties;
import com.company.kra.security.JwtTokenProvider;
import com.company.kra.security.SecurityProperties;
import com.company.kra.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in, token rotation and password lifecycle.
 *
 * <p>Passwords are compared only through the encoder and never logged. Failed
 * attempts are counted per account and lock it for a configured window, which
 * is the cheapest defence against credential stuffing.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long RESET_TOKEN_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;

    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsernameIgnoreCase(request.identifier())
                .or(() -> userRepository.findByEmailIgnoreCase(request.identifier()))
                .orElseThrow(() -> {
                    auditService.recordAuthEvent(AuditAction.LOGIN_FAILED, null, request.identifier(),
                            "Unknown account");
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (user.isCurrentlyLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is locked until " + user.getLockedUntil() + ". Contact your administrator.");
        }
        if (!user.isActive() || user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE,
                    "This account is not active yet. Use the sign-in invite you were sent to "
                            + "choose a password, or ask your administrator to re-send it.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Successful sign-in clears the lockout counters.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        AppUserPrincipal principal = userDetailsService.toPrincipal(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = issueRefreshToken(user.getId(), tokenProvider.newFamilyId(), httpRequest);

        auditService.recordAuthEvent(AuditAction.LOGIN, user.getId(), principal.getDisplayName(), null);
        log.info("User {} signed in", user.getUsername());

        return TokenResponse.of(accessToken, refreshToken, tokenProvider.accessTokenSeconds(),
                currentUser(principal));
    }

    /**
     * Rotation with reuse detection: the presented token is revoked and a new
     * one issued. Presenting an already-revoked token kills the whole family.
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        String hash = tokenProvider.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Refresh token is not recognised"));

        if (stored.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), LocalDateTime.now(), "Token reuse detected");
            log.warn("Refresh token reuse detected for user {}; family {} revoked",
                    stored.getUserId(), stored.getFamilyId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Session has expired, sign in again");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Account no longer exists"));
        if (!user.isActive() || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        stored.setRevokedAt(LocalDateTime.now());
        stored.setRevokedReason("Rotated");
        refreshTokenRepository.save(stored);

        AppUserPrincipal principal = userDetailsService.toPrincipal(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String newRefreshToken = issueRefreshToken(user.getId(), stored.getFamilyId(), httpRequest);

        return TokenResponse.of(accessToken, newRefreshToken, tokenProvider.accessTokenSeconds(),
                currentUser(principal));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenProvider.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    token.setRevokedReason("Signed out");
                    refreshTokenRepository.save(token);
                    auditService.recordAuthEvent(AuditAction.LOGOUT, token.getUserId(), null, null);
                });
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        return currentUser(SecurityUtils.requirePrincipal());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public void changePassword(ChangePasswordRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "New password must be different from the current one");
        }

        applyNewPassword(user, request.newPassword());
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(), "Password changed");
        auditService.record(AuditModule.AUTH, AuditAction.PASSWORD_RESET, "User", user.getId(),
                null, null, "Password changed by user");
    }

    /**
     * Always reports success: telling an anonymous caller whether an address is
     * registered is an account-enumeration leak.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(request.email());
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for an address with no account");
            return;
        }
        User user = maybeUser.get();
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setResetToken(token);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
        userRepository.save(user);

        // Phase 6 delivers this through the notification module; until then the
        // token is logged at info level for controlled internal testing only.
        log.info("Password reset token issued for user {}", user.getUsername());
        auditService.recordAuthEvent(AuditAction.PASSWORD_RESET, user.getId(), user.getUsername(),
                "Reset token issued");
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "This reset link is not valid. Request a new one."));

        if (user.getResetTokenExpiresAt() == null
                || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED,
                    "This reset link has expired. Request a new one.");
        }

        applyNewPassword(user, request.newPassword());
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // Setting a password activates an account that was waiting for its
        // invite, and clears a lockout. It must not reinstate one somebody
        // deactivated. Sign-in was never the exposure — the active flag blocks
        // that independently — but the row would have claimed ACTIVE for an
        // account that is not, which is the sort of thing an operator reads
        // once and believes.
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION
                || user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(), "Password reset");
        auditService.recordAuthEvent(AuditAction.PASSWORD_RESET, user.getId(), user.getUsername(),
                "Password reset completed");
    }

    private void applyNewPassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.getMaxFailedLoginAttempts()) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(securityProperties.getLockoutMinutes()));
            log.warn("Account {} locked after {} failed attempts", user.getUsername(), attempts);
        }
        userRepository.save(user);
        auditService.recordAuthEvent(AuditAction.LOGIN_FAILED, user.getId(), user.getUsername(),
                "Failed attempt " + attempts);
    }

    private String issueRefreshToken(Long userId, String familyId, HttpServletRequest httpRequest) {
        String raw = tokenProvider.generateRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenProvider.hashRefreshToken(raw));
        token.setFamilyId(familyId);
        token.setIssuedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays()));
        if (httpRequest != null) {
            token.setIpAddress(httpRequest.getRemoteAddr());
            String agent = httpRequest.getHeader("User-Agent");
            token.setUserAgent(agent == null ? null : agent.substring(0, Math.min(agent.length(), 255)));
        }
        refreshTokenRepository.save(token);
        return raw;
    }

    private long refreshTokenDays() {
        return jwtProperties.getRefreshTokenDays();
    }

    private CurrentUserResponse currentUser(AppUserPrincipal principal) {
        Employee employee = principal.getEmployeeId() == null ? null
                : employeeRepository.findByIdAndCompanyId(principal.getEmployeeId(), principal.getCompanyId())
                .orElse(null);

        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getEmployeeId(),
                employee == null ? null : employee.getEmployeeCode(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getEmail(),
                employee == null ? null : employee.getPhone(),
                employee == null ? null : employee.getDepartment().getId(),
                employee == null ? null : employee.getDepartment().getName(),
                employee == null || employee.getTeam() == null ? null : employee.getTeam().getId(),
                employee == null || employee.getTeam() == null ? null : employee.getTeam().getName(),
                employee == null ? null : employee.getDesignation().getName(),
                employee == null || employee.getReportingManager() == null ? null
                        : employee.getReportingManager().getId(),
                employee == null || employee.getReportingManager() == null ? null
                        : employee.getReportingManager().fullName(),
                employee == null ? null : employee.getProfileImageUrl(),
                principal.getDataScope().name(),
                principal.isMustChangePassword(),
                principal.getRoles(),
                principal.getPermissions());
    }
}
