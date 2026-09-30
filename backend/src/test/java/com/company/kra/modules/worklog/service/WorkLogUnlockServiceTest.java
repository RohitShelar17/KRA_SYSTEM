package com.company.kra.modules.worklog.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.worklog.dto.UnlockRequest;
import com.company.kra.modules.worklog.dto.UnlockResponse;
import com.company.kra.modules.worklog.entity.WorkLogUnlock;
import com.company.kra.modules.worklog.repository.WorkLogUnlockRepository;
import com.company.kra.security.AccessScopeResolver;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reopening one past date for one person.
 *
 * <p>This is an exception to a control, so what it refuses matters as much as
 * what it allows: a manager reopening their own day, a date the window already
 * permits, and a date that has not happened are all worth refusing outright
 * rather than recording a grant that changes nothing.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkLogUnlockServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long MANAGER = 5L;
    private static final Long EMPLOYEE = 10L;

    @Mock private WorkLogUnlockRepository unlockRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CompanySettingsService settingsService;
    @Mock private AccessScopeResolver accessScopeResolver;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher events;

    private WorkLogUnlockService service;

    private final LocalDate oldDate = LocalDate.now().minusDays(10);

    @BeforeEach
    void setUp() {
        service = new WorkLogUnlockService(unlockRepository, employeeRepository, settingsService,
                accessScopeResolver, auditService, events);
        ReflectionTestUtils.setField(service, "validHours", 72);

        when(settingsService.settingsFor(anyLong())).thenReturn(settings(3));
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY))
                .thenReturn(Optional.of(employee(EMPLOYEE, "Priya Nair", "EMP-0003")));
        when(unlockRepository.findLive(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(unlockRepository.save(any())).thenAnswer(call -> {
            WorkLogUnlock row = call.getArgument(0);
            if (row.getId() == null) {
                row.setId(77L);
            }
            return row;
        });
        authenticate(MANAGER);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A grant records who allowed it, why, and when it closes again")
    void grantRecordsTheException() {
        UnlockResponse response = service.grant(new UnlockRequest(EMPLOYEE, oldDate, "Was on client site"));

        ArgumentCaptor<WorkLogUnlock> captor = ArgumentCaptor.forClass(WorkLogUnlock.class);
        verify(unlockRepository).save(captor.capture());
        WorkLogUnlock saved = captor.getValue();

        assertThat(saved.getEmployeeId()).isEqualTo(EMPLOYEE);
        assertThat(saved.getWorkDate()).isEqualTo(oldDate);
        assertThat(saved.getReason()).isEqualTo("Was on client site");
        assertThat(saved.getGrantedById()).isEqualTo(MANAGER);
        assertThat(saved.getGrantedByName()).isEqualTo("Rahul Sharma");
        assertThat(saved.getExpiresAt())
                .isAfter(LocalDateTime.now().plusHours(71))
                .isBefore(LocalDateTime.now().plusHours(73));
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(response.live()).isTrue();
    }

    @Test
    @DisplayName("The employee is told, since the grant runs out whether they use it or not")
    void grantNotifiesTheEmployee() {
        service.grant(new UnlockRequest(EMPLOYEE, oldDate, "Was on client site"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        NotificationRaised event = (NotificationRaised) captor.getValue();

        assertThat(event.eventKey()).isEqualTo("WORKLOG_UNLOCKED");
        assertThat(event.recipientEmployeeIds()).containsExactly(EMPLOYEE);
        assertThat(event.placeholders())
                .containsEntry("grantedBy", "Rahul Sharma")
                .containsEntry("reason", "Was on client site");
        assertThat(event.placeholders().get("expiresAt")).isNotBlank();
        // Two forms of the same date: one for the sentence, one for the link.
        assertThat(event.placeholders().get("workDate")).contains("September");
        assertThat(event.placeholders().get("workDateIso")).isEqualTo(oldDate.toString());
    }

    @Test
    @DisplayName("Granting the same day twice extends the grant rather than stacking rows")
    void secondGrantExtendsTheFirst() {
        WorkLogUnlock existing = new WorkLogUnlock();
        existing.setId(42L);
        existing.setEmployeeId(EMPLOYEE);
        existing.setWorkDate(oldDate);
        existing.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(unlockRepository.findLive(anyLong(), any(), any())).thenReturn(Optional.of(existing));

        service.grant(new UnlockRequest(EMPLOYEE, oldDate, "Still not filed"));

        ArgumentCaptor<WorkLogUnlock> captor = ArgumentCaptor.forClass(WorkLogUnlock.class);
        verify(unlockRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(42L);
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusHours(71));
    }

    @Test
    @DisplayName("A manager cannot reopen their own date")
    void selfGrantIsRefused() {
        when(employeeRepository.findByIdAndCompanyId(MANAGER, COMPANY))
                .thenReturn(Optional.of(employee(MANAGER, "Rahul Sharma", "EMP-0002")));

        assertThatThrownBy(() -> service.grant(new UnlockRequest(MANAGER, oldDate, "Forgot")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot reopen your own date");
        verify(unlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("A date the window already allows is refused rather than recorded as a no-op")
    void dateInsideTheWindowIsRefused() {
        assertThatThrownBy(() ->
                service.grant(new UnlockRequest(EMPLOYEE, LocalDate.now().minusDays(1), "Just in case")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("still inside the 3-day back-dating window");
        verify(unlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("A future date is refused: reopening is about filing late, not early")
    void futureDateIsRefused() {
        assertThatThrownBy(() ->
                service.grant(new UnlockRequest(EMPLOYEE, LocalDate.now().plusDays(1), "Planning ahead")))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Somebody outside the caller's scope is not theirs to reopen")
    void outOfScopeIsRefused() {
        // The scope resolver is the authority; this only checks it is consulted
        // before anything is written.
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.OUT_OF_SCOPE))
                .when(accessScopeResolver).assertCanAccess(EMPLOYEE);

        assertThatThrownBy(() -> service.grant(new UnlockRequest(EMPLOYEE, oldDate, "Not my report")))
                .isInstanceOf(BusinessException.class);
        verify(unlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unknown employee is not found")
    void unknownEmployeeIsRefused() {
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(new UnlockRequest(EMPLOYEE, oldDate, "Anyone?")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Revoking closes the date and records who closed it")
    void revokeClosesTheDate() {
        WorkLogUnlock open = new WorkLogUnlock();
        open.setId(42L);
        open.setEmployeeId(EMPLOYEE);
        open.setWorkDate(oldDate);
        open.setExpiresAt(LocalDateTime.now().plusHours(10));
        when(unlockRepository.findByIdAndCompanyId(42L, COMPANY)).thenReturn(Optional.of(open));

        service.revoke(42L);

        assertThat(open.getRevokedAt()).isNotNull();
        assertThat(open.getRevokedById()).isEqualTo(MANAGER);
        assertThat(open.isLive(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("Revoking twice says so rather than reporting success again")
    void revokingTwiceIsRefused() {
        WorkLogUnlock closed = new WorkLogUnlock();
        closed.setId(42L);
        closed.setEmployeeId(EMPLOYEE);
        closed.setWorkDate(oldDate);
        closed.setExpiresAt(LocalDateTime.now().plusHours(10));
        closed.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        when(unlockRepository.findByIdAndCompanyId(42L, COMPANY)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.revoke(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been closed");
    }

    @Test
    @DisplayName("Live means neither revoked nor expired")
    void liveIsBothConditions() {
        WorkLogUnlock unlock = new WorkLogUnlock();
        unlock.setExpiresAt(LocalDateTime.now().plusHours(1));
        assertThat(unlock.isLive(LocalDateTime.now())).isTrue();

        unlock.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThat(unlock.isLive(LocalDateTime.now())).isFalse();

        unlock.setExpiresAt(LocalDateTime.now().plusHours(1));
        unlock.setRevokedAt(LocalDateTime.now());
        assertThat(unlock.isLive(LocalDateTime.now())).isFalse();
    }

    private void authenticate(Long employeeId) {
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(employeeId)
                .employeeId(employeeId)
                .companyId(COMPANY)
                .departmentId(2L)
                .username("rahul@company.com")
                .email("rahul@company.com")
                .displayName("Rahul Sharma")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.REPORTS)
                .roles(Set.of("REPORTING_MANAGER"))
                .permissions(Set.of("WORKLOG_APPROVE"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    private Employee employee(Long id, String name, String code) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setCompanyId(COMPANY);
        employee.setEmployeeCode(code);
        String[] parts = name.split(" ");
        employee.setFirstName(parts[0]);
        employee.setLastName(parts[1]);
        return employee;
    }

    private CompanySettings settings(int backdateWindowDays) {
        CompanySettings settings = new CompanySettings();
        settings.setWorkStartTime(LocalTime.of(9, 0));
        settings.setWorkEndTime(LocalTime.of(18, 0));
        settings.setBreakMinutes(60);
        settings.setBackdateWindowDays(backdateWindowDays);
        return settings;
    }
}
