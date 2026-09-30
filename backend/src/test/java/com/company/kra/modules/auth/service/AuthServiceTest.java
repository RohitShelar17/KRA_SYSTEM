package com.company.kra.modules.auth.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.UserStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.auth.dto.LoginRequest;
import com.company.kra.modules.auth.dto.ResetPasswordRequest;
import com.company.kra.modules.auth.entity.RefreshToken;
import com.company.kra.modules.auth.repository.RefreshTokenRepository;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.CustomUserDetailsService;
import com.company.kra.security.JwtProperties;
import com.company.kra.security.JwtTokenProvider;
import com.company.kra.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    private AuthService authService;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.setMaxFailedLoginAttempts(3);
        securityProperties.setLockoutMinutes(15);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenDays(7);

        authService = new AuthService(userRepository, employeeRepository, refreshTokenRepository,
                userDetailsService, tokenProvider, jwtProperties, passwordEncoder, securityProperties,
                auditService);

        when(tokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(tokenProvider.hashRefreshToken(anyString())).thenReturn("hashed-refresh");
        when(tokenProvider.newFamilyId()).thenReturn("family-1");
        when(tokenProvider.accessTokenSeconds()).thenReturn(900L);
        when(userDetailsService.toPrincipal(any())).thenAnswer(invocation -> principal());
    }

    @Test
    @DisplayName("Correct credentials issue a token pair and clear the failure counter")
    void successfulLoginIssuesTokens() {
        User user = activeUser();
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsernameIgnoreCase("rahul@company.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "stored-hash")).thenReturn(true);

        var response = authService.login(new LoginRequest("rahul@company.com", "correct", true), null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("A wrong password counts the attempt and does not issue a token")
    void wrongPasswordCountsAttempt() {
        User user = activeUser();
        when(userRepository.findByUsernameIgnoreCase("rahul@company.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("rahul@company.com", "wrong", false), null))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reaching the attempt limit locks the account for the configured window")
    void repeatedFailuresLockTheAccount() {
        User user = activeUser();
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsernameIgnoreCase("rahul@company.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("rahul@company.com", "wrong", false), null))
                .isInstanceOf(BusinessException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    @Test
    @DisplayName("A locked account is refused before the password is even checked")
    void lockedAccountIsRefused() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsernameIgnoreCase("rahul@company.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("rahul@company.com", "correct", false), null))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Replaying a revoked refresh token revokes the whole family")
    void reusedRefreshTokenRevokesFamily() {
        RefreshToken revoked = new RefreshToken();
        revoked.setUserId(1L);
        revoked.setFamilyId("family-1");
        revoked.setTokenHash("hashed-refresh");
        revoked.setExpiresAt(LocalDateTime.now().plusDays(1));
        revoked.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash("hashed-refresh")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("raw-refresh", null))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

        ArgumentCaptor<String> family = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).revokeFamily(family.capture(), any(), anyString());
        assertThat(family.getValue()).isEqualTo("family-1");
    }

    @Test
    @DisplayName("An expired refresh token is rejected without rotating")
    void expiredRefreshTokenIsRejected() {
        RefreshToken expired = new RefreshToken();
        expired.setUserId(1L);
        expired.setFamilyId("family-1");
        expired.setTokenHash("hashed-refresh");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash("hashed-refresh")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-refresh", null))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("An account pending activation cannot sign in")
    void pendingActivationCannotSignIn() {
        User user = activeUser();
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        when(userRepository.findByUsernameIgnoreCase("rahul@company.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("rahul@company.com", "correct", false), null))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    @DisplayName("Setting a password activates an account that was waiting for its invite")
    void resetActivatesAPendingAccount() {
        User user = activeUser();
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setResetToken("token");
        user.setResetTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));

        authService.resetPassword(new ResetPasswordRequest("token", "Chosen@Pass99"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getResetToken()).isNull();
    }

    @Test
    @DisplayName("Setting a password does not reinstate an account somebody deactivated")
    void resetDoesNotReviveADeactivatedAccount() {
        // A leaver holding a token from before their exit can still spend it,
        // and the active flag still keeps them out; what must not happen is the
        // status reading ACTIVE afterwards.
        User user = activeUser();
        user.setStatus(UserStatus.INACTIVE);
        user.setActive(false);
        user.setResetToken("token");
        user.setResetTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));

        authService.resetPassword(new ResetPasswordRequest("token", "Chosen@Pass99"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("Setting a password clears a lockout")
    void resetClearsALockout() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        user.setResetToken("token");
        user.setResetTokenExpiresAt(LocalDateTime.now().plusDays(1));
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));

        authService.resetPassword(new ResetPasswordRequest("token", "Chosen@Pass99"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Signing out revokes the presented token exactly once")
    void logoutRevokesToken() {
        RefreshToken token = new RefreshToken();
        token.setUserId(1L);
        token.setFamilyId("family-1");
        token.setTokenHash("hashed-refresh");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash("hashed-refresh")).thenReturn(Optional.of(token));

        authService.logout("raw-refresh");

        assertThat(token.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(1)).save(token);
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setCompanyId(1L);
        user.setUsername("rahul@company.com");
        user.setEmail("rahul@company.com");
        user.setPasswordHash("stored-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        return user;
    }

    private AppUserPrincipal principal() {
        return AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(5L)
                .companyId(1L)
                .departmentId(2L)
                .username("rahul@company.com")
                .email("rahul@company.com")
                .displayName("Rahul Sharma")
                .passwordHash("stored-hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.REPORTS)
                .roles(Set.of("REPORTING_MANAGER"))
                .permissions(Set.of("DASHBOARD_VIEW"))
                .build();
    }
}
