package com.company.kra.modules.worklog.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.ApprovalAction;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.kra.service.KraLookup;
import com.company.kra.modules.task.service.TaskLookup;
import com.company.kra.modules.worklog.dto.MissingSubmissionResponse;
import com.company.kra.modules.worklog.dto.ReviewDecisionRequest;
import com.company.kra.modules.worklog.dto.SubmitDayRequest;
import com.company.kra.modules.worklog.entity.WorkLogApproval;
import com.company.kra.modules.worklog.entity.WorkLogDay;
import com.company.kra.modules.worklog.repository.DailyWorkLogRepository;
import com.company.kra.modules.worklog.repository.WorkLogApprovalRepository;
import com.company.kra.modules.worklog.repository.WorkLogDayRepository;
import com.company.kra.modules.worklog.validation.TimesheetValidator;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * The submission and review state machine: who may move a day, from where to
 * where, and what has to be recorded when they do &mdash; plus who is reported
 * as not having filed, which is the same question asked of a whole team.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkLogReviewTest {

    private static final Long COMPANY = 1L;
    private static final Long EMPLOYEE = 10L;
    private static final Long MANAGER = 5L;

    @Mock private WorkLogDayRepository dayRepository;
    @Mock private DailyWorkLogRepository entryRepository;
    @Mock private WorkLogApprovalRepository approvalRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private CompanySettingsService settingsService;
    @Mock private TaskLookup taskLookup;
    @Mock private KraLookup kraLookup;
    @Mock private AccessScopeResolver accessScopeResolver;
    @Mock private AuditService auditService;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @Mock private WorkLogUnlockService unlockService;

    private WorkLogService service;

    @BeforeEach
    void setUp() {
        service = new WorkLogService(dayRepository, entryRepository, approvalRepository, employeeRepository,
                holidayRepository, settingsService, taskLookup, kraLookup, new TimesheetValidator(),
                accessScopeResolver, auditService, events,
                new WorkingCalendar(settingsService, holidayRepository), unlockService);

        when(settingsService.settingsFor(anyLong())).thenReturn(settings(true));
        when(entryRepository.findByWorkLogDayIdOrderByStartTimeAsc(anyLong())).thenReturn(List.of());
        when(approvalRepository.findByWorkLogDayIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(holidayRepository.findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
                anyLong(), any(), any())).thenReturn(List.of());
        when(dayRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(employeeRepository.findByIdAndCompanyId(EMPLOYEE, COMPANY))
                .thenReturn(Optional.of(employee(EMPLOYEE, "Priya Nair")));
        when(employeeRepository.findByIdAndCompanyId(MANAGER, COMPANY))
                .thenReturn(Optional.of(employee(MANAGER, "Rahul Sharma")));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Submitting an empty day is refused")
    void emptyDayCannotBeSubmitted() {
        authenticate(principal(EMPLOYEE, DataScope.SELF));
        WorkLogDay day = day(WorkLogDayStatus.DRAFT, 0);
        when(dayRepository.findByEmployeeIdAndWorkDate(EMPLOYEE, day.getWorkDate()))
                .thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.submit(new SubmitDayRequest(day.getWorkDate(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one entry");
    }

    @Test
    @DisplayName("Submission moves a day to SUBMITTED and records the transition")
    void submitMovesToSubmitted() {
        authenticate(principal(EMPLOYEE, DataScope.SELF));
        WorkLogDay day = day(WorkLogDayStatus.DRAFT, 4);
        when(dayRepository.findByEmployeeIdAndWorkDate(EMPLOYEE, day.getWorkDate()))
                .thenReturn(Optional.of(day));

        service.submit(new SubmitDayRequest(day.getWorkDate(), "Done for the day"));

        assertThat(day.getStatus()).isEqualTo(WorkLogDayStatus.SUBMITTED);
        assertThat(day.getSubmittedAt()).isNotNull();

        ArgumentCaptor<WorkLogApproval> captor = ArgumentCaptor.forClass(WorkLogApproval.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(ApprovalAction.SUBMITTED);
        assertThat(captor.getValue().getFromStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getToStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("With approval switched off, submission closes the day immediately")
    void submitApprovesWhenApprovalDisabled() {
        when(settingsService.settingsFor(anyLong())).thenReturn(settings(false));
        authenticate(principal(EMPLOYEE, DataScope.SELF));
        WorkLogDay day = day(WorkLogDayStatus.DRAFT, 3);
        when(dayRepository.findByEmployeeIdAndWorkDate(EMPLOYEE, day.getWorkDate()))
                .thenReturn(Optional.of(day));

        service.submit(new SubmitDayRequest(day.getWorkDate(), null));

        assertThat(day.getStatus()).isEqualTo(WorkLogDayStatus.APPROVED);
    }

    @Test
    @DisplayName("A day cannot be submitted twice")
    void cannotSubmitTwice() {
        authenticate(principal(EMPLOYEE, DataScope.SELF));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByEmployeeIdAndWorkDate(EMPLOYEE, day.getWorkDate()))
                .thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.submit(new SubmitDayRequest(day.getWorkDate(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been submitted");
    }

    @Test
    @DisplayName("Nobody can review their own work log")
    void cannotReviewOwnDay() {
        authenticate(principal(EMPLOYEE, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.APPROVED, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot review your own");
    }

    @Test
    @DisplayName("Approval records the reviewer and the decision")
    void approvalRecordsReviewer() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        service.review(day.getId(), new ReviewDecisionRequest(ApprovalAction.APPROVED, "Looks right"));

        assertThat(day.getStatus()).isEqualTo(WorkLogDayStatus.APPROVED);
        assertThat(day.getReviewedAt()).isNotNull();
        assertThat(day.getReviewedBy()).isNotNull();
        verify(accessScopeResolver).assertCanAccess(EMPLOYEE);
    }

    @Test
    @DisplayName("Returning a day for changes needs a comment")
    void returningRequiresComment() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.MODIFICATION_REQUESTED, "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(approvalRepository, never()).save(any());
    }

    @Test
    @DisplayName("A returned day becomes editable again")
    void returnedDayIsEditable() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.MODIFICATION_REQUESTED, "Split the afternoon block"));

        assertThat(day.getStatus()).isEqualTo(WorkLogDayStatus.MODIFICATION_REQUESTED);
        assertThat(day.isEditable()).isTrue();
    }

    @Test
    @DisplayName("A draft day cannot be approved without being submitted first")
    void draftCannotBeApproved() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.DRAFT, 2);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.APPROVED, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not waiting for review");
    }

    @Test
    @DisplayName("Only an approved day can be reopened")
    void onlyApprovedCanReopen() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.SUBMITTED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.REOPENED, "Correction needed")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only an approved day");
    }

    @Test
    @DisplayName("Reopening an approved day returns it to draft")
    void reopenReturnsToDraft() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        WorkLogDay day = day(WorkLogDayStatus.APPROVED, 4);
        when(dayRepository.findByIdAndCompanyId(day.getId(), COMPANY)).thenReturn(Optional.of(day));

        service.review(day.getId(),
                new ReviewDecisionRequest(ApprovalAction.REOPENED, "Wrong project on the morning block"));

        assertThat(day.getStatus()).isEqualTo(WorkLogDayStatus.DRAFT);
        assertThat(day.isEditable()).isTrue();
    }

    // ------------------------------------------------------------- fixtures

    @Test
    @DisplayName("Nobody is missing on a day the company does not work")
    void holidayLeavesNobodyMissing() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        LocalDate holiday = LocalDate.of(2026, 9, 14); // A Monday.
        Holiday declared = new Holiday();
        declared.setHolidayDate(holiday);
        declared.setName("Ganesh Chaturthi");
        when(holidayRepository.findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
                COMPANY, holiday, holiday)).thenReturn(List.of(declared));
        when(accessScopeResolver.visibleEmployeeIds(any()))
                .thenReturn(new AccessScopeResolver.Scope(false, Set.of(EMPLOYEE)));

        assertThat(service.missingSubmissions(holiday)).isEmpty();
        // The expensive query is never reached: there was nothing to ask.
        verify(dayRepository, never()).findEmployeeIdsWithSubmissionOn(holiday);
    }

    @Test
    @DisplayName("On a working day, whoever has not submitted is still reported")
    void workingDayStillReportsMissing() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        LocalDate workday = LocalDate.of(2026, 9, 15); // A Tuesday.
        when(accessScopeResolver.visibleEmployeeIds(any()))
                .thenReturn(new AccessScopeResolver.Scope(false, Set.of(EMPLOYEE)));
        Employee priya = employee(EMPLOYEE, "Priya Nair");
        priya.setActive(true);
        when(employeeRepository.findAllById(Set.of(EMPLOYEE))).thenReturn(List.of(priya));
        when(dayRepository.findEmployeeIdsWithSubmissionOn(workday)).thenReturn(Set.of());
        when(dayRepository.findByEmployeeIdAndWorkDate(EMPLOYEE, workday)).thenReturn(Optional.empty());

        assertThat(service.missingSubmissions(workday))
                .extracting(MissingSubmissionResponse::employeeId)
                .containsExactly(EMPLOYEE);
    }

    @Test
    @DisplayName("Somebody who had not joined yet is not reported as missing")
    void beforeJoiningIsNotMissing() {
        authenticate(principal(MANAGER, DataScope.REPORTS));
        LocalDate workday = LocalDate.of(2026, 9, 15);
        when(accessScopeResolver.visibleEmployeeIds(any()))
                .thenReturn(new AccessScopeResolver.Scope(false, Set.of(EMPLOYEE)));
        Employee newJoiner = employee(EMPLOYEE, "Priya Nair");
        newJoiner.setActive(true);
        newJoiner.setJoiningDate(workday.plusDays(1));
        when(employeeRepository.findAllById(Set.of(EMPLOYEE))).thenReturn(List.of(newJoiner));
        when(dayRepository.findEmployeeIdsWithSubmissionOn(workday)).thenReturn(Set.of());

        assertThat(service.missingSubmissions(workday)).isEmpty();
    }

    private void authenticate(AppUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    private AppUserPrincipal principal(Long employeeId, DataScope scope) {
        return AppUserPrincipal.builder()
                .userId(employeeId)
                .employeeId(employeeId)
                .companyId(COMPANY)
                .departmentId(2L)
                .username("tester")
                .email("tester@company.com")
                .displayName(employeeId.equals(MANAGER) ? "Rahul Sharma" : "Priya Nair")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(scope)
                .roles(Set.of("EMPLOYEE"))
                .permissions(Set.of("WORKLOG_WRITE_SELF", "WORKLOG_APPROVE"))
                .build();
    }

    private WorkLogDay day(WorkLogDayStatus status, int entryCount) {
        WorkLogDay day = new WorkLogDay();
        day.setId(100L);
        day.setCompanyId(COMPANY);
        day.setEmployee(employee(EMPLOYEE, "Priya Nair"));
        day.setWorkDate(LocalDate.now());
        day.setStatus(status);
        day.setEntryCount(entryCount);
        day.setOfficeMinutes(540);
        day.setLoggedMinutes(entryCount * 120);
        return day;
    }

    private Employee employee(Long id, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setCompanyId(COMPANY);
        employee.setEmployeeCode("EMP-000" + id);
        String[] parts = name.split(" ");
        employee.setFirstName(parts[0]);
        employee.setLastName(parts[1]);
        return employee;
    }

    private CompanySettings settings(boolean approvalEnabled) {
        CompanySettings settings = new CompanySettings();
        settings.setWorkStartTime(LocalTime.of(9, 0));
        settings.setWorkEndTime(LocalTime.of(18, 0));
        settings.setBreakMinutes(60);
        settings.setWorklogApprovalEnabled(approvalEnabled);
        settings.setOvertimeAllowed(true);
        settings.setMaxOvertimeMinutes(180);
        settings.setBackdateWindowDays(3);
        return settings;
    }
}
