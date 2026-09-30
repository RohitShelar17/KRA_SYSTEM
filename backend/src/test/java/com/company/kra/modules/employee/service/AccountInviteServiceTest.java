package com.company.kra.modules.employee.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.UserStatus;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.dto.InviteResponse;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Getting a new joiner signed in without a password ever being transmitted.
 *
 * <p>What matters here is that the invite is a <em>token</em>, single-use and
 * time-limited, and that re-issuing one invalidates whatever was outstanding —
 * otherwise an old link left in an inbox stays a way into the account.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountInviteServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long EMPLOYEE = 10L;

    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher events;

    private AccountInviteService service;

    @BeforeEach
    void setUp() {
        service = new AccountInviteService(employeeRepository, userRepository, auditService, events);
        ReflectionTestUtils.setField(service, "baseUrl", "https://kra.example.com");
        ReflectionTestUtils.setField(service, "emailEnabled", true);
        authenticate();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A fresh token is minted with a seven-day life")
    void issuesATokenWithALifetime() {
        User user = new User();

        service.issueToken(user);

        assertThat(user.getResetToken()).isNotBlank().doesNotContain("-");
        assertThat(user.getResetTokenExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(AccountInviteService.INVITE_VALID_DAYS - 1L))
                .isBefore(LocalDateTime.now().plusDays(AccountInviteService.INVITE_VALID_DAYS + 1L));
    }

    @Test
    @DisplayName("Re-issuing replaces the outstanding token, so an old link stops working")
    void resendInvalidatesThePreviousLink() {
        Employee employee = employee();
        String previous = employee.getUser().getResetToken();
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        InviteResponse response = service.resend(EMPLOYEE);

        assertThat(employee.getUser().getResetToken()).isNotEqualTo(previous);
        assertThat(response.resetLink())
                .isEqualTo("https://kra.example.com/reset-password?token="
                        + employee.getUser().getResetToken());
        assertThat(response.signInName()).isEqualTo("priya.nair@company.com");
        assertThat(response.emailed()).isTrue();
        verify(userRepository).save(employee.getUser());
    }

    @Test
    @DisplayName("The invite is raised as an event, carrying the path rather than the whole URL")
    void raisesTheInviteEvent() {
        Employee employee = employee();
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        service.resend(EMPLOYEE);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        NotificationRaised event = (NotificationRaised) captor.getValue();

        assertThat(event.eventKey()).isEqualTo("ACCOUNT_INVITE");
        assertThat(event.recipientEmployeeIds()).containsExactly(EMPLOYEE);
        assertThat(event.placeholders())
                .containsEntry("employeeName", "Priya Nair")
                .containsEntry("signInName", "priya.nair@company.com")
                .containsEntry("validDays", "7");
        // A path, not an absolute URL: the channel completes it, so the same
        // template row works in every environment.
        assertThat(event.placeholders().get("link"))
                .startsWith("/reset-password?token=")
                .doesNotContain("http");
    }

    @Test
    @DisplayName("Issuing an invite is audited, because it is a way into somebody's account")
    void inviteIsAudited() {
        Employee employee = employee();
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        service.resend(EMPLOYEE);

        verify(auditService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("An employee with no sign-in account is refused rather than silently skipped")
    void employeeWithoutAccountIsRefused() {
        Employee employee = employee();
        employee.setUser(null);
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.resend(EMPLOYEE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no sign-in account");
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("A deactivated account is refused: an invite must not be a way back in")
    void deactivatedAccountIsRefused() {
        Employee employee = employee();
        employee.setActive(false);
        employee.getUser().setStatus(UserStatus.INACTIVE);
        employee.getUser().setActive(false);
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.resend(EMPLOYEE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");
        verify(events, never()).publishEvent(any(Object.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An employee outside the caller's company is not found")
    void unknownEmployeeIsNotFound() {
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend(EMPLOYEE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("With email off the response says so, so the screen can tell the administrator")
    void reportsWhenEmailIsOff() {
        ReflectionTestUtils.setField(service, "emailEnabled", false);
        Employee employee = employee();
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.of(employee));

        assertThat(service.resend(EMPLOYEE).emailed()).isFalse();
    }

    private Employee employee() {
        User user = new User();
        user.setId(99L);
        user.setCompanyId(COMPANY);
        user.setUsername("priya.nair@company.com");
        user.setEmail("priya.nair@company.com");
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setActive(true);
        user.setResetToken("theoldtoken");
        user.setResetTokenExpiresAt(LocalDateTime.now().minusDays(1));

        Employee employee = new Employee();
        employee.setId(EMPLOYEE);
        employee.setCompanyId(COMPANY);
        employee.setEmployeeCode("EMP-0003");
        employee.setFirstName("Priya");
        employee.setLastName("Nair");
        employee.setActive(true);
        employee.setUser(user);
        return employee;
    }

    private void authenticate() {
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(1L)
                .companyId(COMPANY)
                .username("admin")
                .email("admin@company.com")
                .displayName("System Administrator")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.ORGANISATION)
                .roles(Set.of("SUPER_ADMIN"))
                .permissions(Set.of("EMPLOYEE_MANAGE"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }
}
