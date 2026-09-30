package com.company.kra.modules.worklog.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.ApprovalAction;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.CategoryType;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.kra.service.KraLookup;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.entity.TaskCategory;
import com.company.kra.modules.task.service.TaskLookup;
import com.company.kra.modules.worklog.dto.ApprovalHistoryResponse;
import com.company.kra.modules.worklog.dto.CategoryBreakdownResponse;
import com.company.kra.modules.worklog.dto.DayTotalsResponse;
import com.company.kra.modules.worklog.dto.MissingSubmissionResponse;
import com.company.kra.modules.worklog.dto.ReviewDecisionRequest;
import com.company.kra.modules.worklog.dto.SubmitDayRequest;
import com.company.kra.modules.worklog.dto.TimelineSegmentResponse;
import com.company.kra.modules.worklog.dto.WorkLogDayResponse;
import com.company.kra.modules.worklog.dto.WorkLogDaySummaryResponse;
import com.company.kra.modules.worklog.dto.WorkLogEntryRequest;
import com.company.kra.modules.worklog.dto.WorkLogEntryResponse;
import com.company.kra.modules.worklog.entity.DailyWorkLog;
import com.company.kra.modules.worklog.entity.WorkLogApproval;
import com.company.kra.modules.worklog.entity.WorkLogDay;
import com.company.kra.modules.worklog.repository.DailyWorkLogRepository;
import com.company.kra.modules.worklog.repository.WorkLogApprovalRepository;
import com.company.kra.modules.worklog.repository.WorkLogDayRepository;
import com.company.kra.modules.worklog.validation.TimesheetValidator;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The daily timesheet: entries, day totals, submission and review.
 *
 * <p>Two invariants hold everywhere in here. Totals are recomputed from the
 * entries after every write, never incremented, so they cannot drift. And a day
 * is only writable while it is open — once submitted, the only way back is a
 * manager returning it, which is recorded.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkLogService {

    private final WorkLogDayRepository dayRepository;
    private final DailyWorkLogRepository entryRepository;
    private final WorkLogApprovalRepository approvalRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsService settingsService;
    private final TaskLookup taskLookup;
    private final KraLookup kraLookup;
    private final TimesheetValidator validator;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final WorkingCalendar workingCalendar;
    private final WorkLogUnlockService unlockService;

    // ----------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public WorkLogDayResponse getDay(Long employeeId, LocalDate date) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long target = employeeId == null ? requireEmployeeId(principal) : employeeId;
        if (!target.equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(target);
        }

        Employee employee = requireEmployee(target, principal.getCompanyId());
        CompanySettings settings = settingsService.settingsFor(principal.getCompanyId());
        Optional<WorkLogDay> existing = dayRepository.findByEmployeeIdAndWorkDate(target, date);

        // A day nobody has touched yet is a valid, empty day — not a 404.
        return existing
                .map(day -> toDayResponse(day, employee, settings))
                .orElseGet(() -> emptyDayResponse(employee, date, settings));
    }

    @Transactional(readOnly = true)
    public List<WorkLogDaySummaryResponse> range(Long employeeId, LocalDate from, LocalDate to) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long target = employeeId == null ? requireEmployeeId(principal) : employeeId;
        if (!target.equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(target);
        }
        return dayRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(target, from, to)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /** The manager review queue. */
    @Transactional(readOnly = true)
    public PageResponse<WorkLogDaySummaryResponse> search(Long employeeId, WorkLogDayStatus status,
                                                          LocalDate from, LocalDate to, Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        if (!scope.organisationWide() && scope.employeeIds().isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                    0, 0, true, true);
        }
        if (employeeId != null && !scope.covers(employeeId)) {
            throw new BusinessException(ErrorCode.OUT_OF_SCOPE, "That employee is outside your access scope");
        }

        Page<WorkLogDay> page = dayRepository.search(principal.getCompanyId(), scope.organisationWide(),
                scope.organisationWide() ? Set.of(-1L) : scope.employeeIds(), employeeId, status,
                from, to, pageable);
        return PageResponse.from(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public long pendingReviewCount() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);
        if (!scope.organisationWide() && scope.employeeIds().isEmpty()) {
            return 0;
        }
        return dayRepository.countAwaitingReview(principal.getCompanyId(), scope.organisationWide(),
                scope.organisationWide() ? Set.of(-1L) : scope.employeeIds());
    }

    /**
     * Who inside the caller's scope has not filed a given date. Answered by
     * difference, so someone with a draft still counts as missing.
     *
     * <p>Nobody is missing on a day the company does not work, and nobody is
     * missing for a date before they joined: both would be a person chased for
     * a day nothing ever expected them to file.</p>
     */
    @Transactional(readOnly = true)
    public List<MissingSubmissionResponse> missingSubmissions(LocalDate date) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        if (workingCalendar.workingDaysBetween(principal.getCompanyId(), date, date).isEmpty()) {
            return List.of();
        }

        List<Employee> candidates = scope.organisationWide()
                ? employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(principal.getCompanyId())
                : employeeRepository.findAllById(scope.employeeIds());

        Set<Long> submitted = dayRepository.findEmployeeIdsWithSubmissionOn(date);

        return candidates.stream()
                .filter(Employee::isActive)
                .filter(employee -> employee.getJoiningDate() == null
                        || !date.isBefore(employee.getJoiningDate()))
                .filter(employee -> !submitted.contains(employee.getId()))
                .map(employee -> {
                    WorkLogDay draft = dayRepository
                            .findByEmployeeIdAndWorkDate(employee.getId(), date)
                            .orElse(null);
                    return new MissingSubmissionResponse(employee.getId(), employee.fullName(),
                            employee.getEmployeeCode(),
                            employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                            date, draft != null, draft == null ? 0 : draft.getLoggedMinutes());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryBreakdownResponse> categoryBreakdown(Long employeeId, LocalDate from, LocalDate to) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long target = employeeId == null ? requireEmployeeId(principal) : employeeId;
        if (!target.equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(target);
        }

        List<Object[]> rows = entryRepository.categoryBreakdown(target, from, to);
        int total = rows.stream().mapToInt(row -> ((Number) row[2]).intValue()).sum();
        return rows.stream()
                .map(row -> {
                    int minutes = ((Number) row[2]).intValue();
                    return new CategoryBreakdownResponse((String) row[0], (String) row[1], minutes,
                            total == 0 ? 0 : round1(minutes * 100.0 / total));
                })
                .toList();
    }

    // ---------------------------------------------------------------- writes

    @Transactional
    public WorkLogDayResponse addEntry(WorkLogEntryRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        Employee employee = requireEmployee(employeeId, principal.getCompanyId());
        CompanySettings settings = settingsService.settingsFor(principal.getCompanyId());

        validator.validateDate(request.workDate(), settings,
                unlockService.isUnlocked(employeeId, request.workDate()));
        WorkLogDay day = openDayFor(employee, request.workDate(), settings);
        assertEditable(day);

        TimesheetValidator.ShiftWindow shift = validator.resolveShift(employee, settings);
        List<DailyWorkLog> existing = entryRepository.findSlotsForDay(employeeId, request.workDate(), null);
        validator.validateEntry(request.startTime(), request.endTime(), existing, shift, settings);

        DailyWorkLog entry = new DailyWorkLog();
        entry.setCompanyId(principal.getCompanyId());
        entry.setWorkLogDay(day);
        entry.setEmployee(employee);
        entry.setWorkDate(request.workDate());
        applyEntry(entry, request, principal.getCompanyId(), employeeId);
        entryRepository.save(entry);

        recalculate(day, employee, settings);
        taskLookup.refreshActualMinutes(entry.getTask() == null ? null : entry.getTask().getId());

        log.debug("Work log entry added for employee {} on {}", employeeId, request.workDate());
        return toDayResponse(day, employee, settings);
    }

    @Transactional
    public WorkLogDayResponse updateEntry(Long entryId, WorkLogEntryRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        DailyWorkLog entry = entryRepository.findByIdAndCompanyId(entryId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Work log entry", entryId));

        if (!entry.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You may only edit your own work log");
        }

        Employee employee = entry.getEmployee();
        CompanySettings settings = settingsService.settingsFor(principal.getCompanyId());
        WorkLogDay day = entry.getWorkLogDay();
        assertEditable(day);

        TimesheetValidator.ShiftWindow shift = validator.resolveShift(employee, settings);
        List<DailyWorkLog> others = entryRepository.findSlotsForDay(employeeId, day.getWorkDate(), entryId);
        validator.validateEntry(request.startTime(), request.endTime(), others, shift, settings);

        Long previousTaskId = entry.getTask() == null ? null : entry.getTask().getId();
        applyEntry(entry, request, principal.getCompanyId(), employeeId);
        entryRepository.save(entry);

        recalculate(day, employee, settings);
        taskLookup.refreshActualMinutes(previousTaskId);
        taskLookup.refreshActualMinutes(entry.getTask() == null ? null : entry.getTask().getId());

        return toDayResponse(day, employee, settings);
    }

    @Transactional
    public WorkLogDayResponse deleteEntry(Long entryId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        DailyWorkLog entry = entryRepository.findByIdAndCompanyId(entryId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Work log entry", entryId));

        if (!entry.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You may only edit your own work log");
        }

        WorkLogDay day = entry.getWorkLogDay();
        Employee employee = entry.getEmployee();
        assertEditable(day);

        Long taskId = entry.getTask() == null ? null : entry.getTask().getId();
        entryRepository.delete(entry);
        entryRepository.flush();

        CompanySettings settings = settingsService.settingsFor(principal.getCompanyId());
        recalculate(day, employee, settings);
        taskLookup.refreshActualMinutes(taskId);

        return toDayResponse(day, employee, settings);
    }

    @Transactional
    public WorkLogDayResponse submit(SubmitDayRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        Employee employee = requireEmployee(employeeId, principal.getCompanyId());
        CompanySettings settings = settingsService.settingsFor(principal.getCompanyId());

        WorkLogDay day = dayRepository.findByEmployeeIdAndWorkDate(employeeId, request.workDate())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "There is nothing to submit for " + request.workDate()));

        if (day.getStatus().isAwaitingReview() || day.getStatus() == WorkLogDayStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This day has already been submitted");
        }
        if (day.getEntryCount() == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Add at least one entry before submitting the day");
        }

        WorkLogDayStatus from = day.getStatus();
        // With approval switched off, submission is final and the day is closed here.
        WorkLogDayStatus to = settings.isWorklogApprovalEnabled()
                ? WorkLogDayStatus.SUBMITTED : WorkLogDayStatus.APPROVED;

        day.setStatus(to);
        day.setSubmittedAt(LocalDateTime.now());
        dayRepository.save(day);

        recordApproval(day, ApprovalAction.SUBMITTED, from, to, principal, request.comments());
        auditService.record(AuditModule.WORKLOG, AuditAction.UPDATE, "WorkLogDay", day.getId(),
                Map.of("status", from.name()), Map.of("status", to.name()), request.comments());

        log.info("Employee {} submitted the work log for {}", employeeId, request.workDate());

        // Only worth telling a manager about when there is a review to do.
        if (to == WorkLogDayStatus.SUBMITTED && employee.getReportingManager() != null) {
            events.publishEvent(NotificationRaised
                    .to(principal.getCompanyId(), "WORKLOG_SUBMITTED")
                    .recipient(employee.getReportingManager().getId())
                    .about("WorkLogDay", day.getId())
                    .with("employeeName", employee.fullName())
                    .with("workDate", request.workDate())
                    .with("loggedHours", hours(day.getLoggedMinutes()))
                    .build());
        }
        return toDayResponse(day, employee, settings);
    }

    /** Approve, reject or return a submitted day. */
    @Transactional
    public WorkLogDayResponse review(Long dayId, ReviewDecisionRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        WorkLogDay day = dayRepository.findByIdAndCompanyId(dayId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Work log day", dayId));

        Long ownerId = day.getEmployee().getId();
        if (ownerId.equals(principal.getEmployeeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You cannot review your own work log");
        }
        accessScopeResolver.assertCanAccess(ownerId);

        WorkLogDayStatus from = day.getStatus();
        WorkLogDayStatus to = switch (request.action()) {
            case APPROVED -> WorkLogDayStatus.APPROVED;
            case REJECTED -> WorkLogDayStatus.REJECTED;
            case MODIFICATION_REQUESTED -> WorkLogDayStatus.MODIFICATION_REQUESTED;
            case REOPENED -> WorkLogDayStatus.DRAFT;
            case SUBMITTED -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Submission is an employee action, not a review decision");
        };

        if (request.action() == ApprovalAction.REOPENED) {
            if (from != WorkLogDayStatus.APPROVED) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Only an approved day can be reopened");
            }
        } else if (!from.isAwaitingReview()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This day is not waiting for review (current status: " + from + ")");
        }

        if (to != WorkLogDayStatus.APPROVED && isBlank(request.comments())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Add a comment so the employee knows what to change");
        }

        day.setStatus(to);
        day.setReviewComments(request.comments());
        day.setReviewedAt(LocalDateTime.now());
        if (principal.getEmployeeId() != null) {
            employeeRepository.findByIdAndCompanyId(principal.getEmployeeId(), principal.getCompanyId())
                    .ifPresent(day::setReviewedBy);
        }
        dayRepository.save(day);

        recordApproval(day, request.action(), from, to, principal, request.comments());
        auditService.record(AuditModule.WORKLOG,
                request.action() == ApprovalAction.APPROVED ? AuditAction.APPROVE : AuditAction.REJECT,
                "WorkLogDay", day.getId(), Map.of("status", from.name()), Map.of("status", to.name()),
                request.comments());

        log.info("Work log day {} moved from {} to {} by user {}", dayId, from, to, principal.getUserId());

        Employee owner = day.getEmployee();
        events.publishEvent(NotificationRaised
                .to(principal.getCompanyId(), "WORKLOG_REVIEWED")
                .recipient(ownerId)
                .about("WorkLogDay", day.getId())
                .with("workDate", day.getWorkDate())
                .with("decision", decisionWording(request.action()))
                .with("reviewerName", principal.getDisplayName())
                .with("comments", isBlank(request.comments()) ? "" : " “" + request.comments() + "”")
                .build());

        return toDayResponse(day, owner, settingsService.settingsFor(principal.getCompanyId()));
    }

    /** Reads inside a sentence: "Rahul approved your work log for ...". */
    private String decisionWording(ApprovalAction action) {
        return switch (action) {
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case MODIFICATION_REQUESTED -> "returned for changes";
            case REOPENED -> "reopened";
            case SUBMITTED -> "submitted";
        };
    }

    private String hours(int minutes) {
        return String.format("%.1f", minutes / 60.0);
    }

    // ------------------------------------------------------------ internals

    private WorkLogDay openDayFor(Employee employee, LocalDate date, CompanySettings settings) {
        return dayRepository.findByEmployeeIdAndWorkDate(employee.getId(), date)
                .orElseGet(() -> {
                    WorkLogDay day = new WorkLogDay();
                    day.setCompanyId(employee.getCompanyId());
                    day.setEmployee(employee);
                    day.setWorkDate(date);
                    day.setStatus(WorkLogDayStatus.DRAFT);
                    day.setOfficeMinutes(validator.resolveShift(employee, settings).grossMinutes());
                    return dayRepository.save(day);
                });
    }

    private void assertEditable(WorkLogDay day) {
        if (!day.isEditable()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This day is " + day.getStatus().name().toLowerCase().replace('_', ' ')
                            + " and can no longer be edited");
        }
    }

    private void applyEntry(DailyWorkLog entry, WorkLogEntryRequest request, Long companyId,
                            Long employeeId) {
        TaskCategory category = taskLookup.requireCategory(request.taskCategoryId(), companyId);

        entry.setStartTime(request.startTime());
        entry.setEndTime(request.endTime());
        entry.setDurationMinutes(validator.minutesBetween(request.startTime(), request.endTime()));
        entry.setTaskCategory(category);
        // Copied, not referenced: re-classifying a category later must not rewrite past days.
        entry.setProductive(category.isProductive());
        entry.setProject(request.projectId() == null ? null
                : taskLookup.requireProject(request.projectId(), companyId));

        Task task = request.taskId() == null ? null
                : taskLookup.requireAssignedTask(request.taskId(), employeeId, companyId);
        entry.setTask(task);

        entry.setEmployeeKraId(request.employeeKraId() == null ? null
                : kraLookup.requireAssigned(request.employeeKraId(), employeeId, entry.getWorkDate()).id());

        entry.setTitle(request.title().trim());
        entry.setDescription(request.description());
        entry.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        entry.setStatus(request.status() == null ? TaskStatus.IN_PROGRESS : request.status());
        entry.setCompletionPct(request.completionPct() == null ? 0
                : Math.max(0, Math.min(100, request.completionPct())));
        entry.setBillable(request.billable());
        entry.setRemarks(request.remarks());
    }

    /** Recomputed from the entries every time, so cached totals cannot drift. */
    private void recalculate(WorkLogDay day, Employee employee, CompanySettings settings) {
        List<DailyWorkLog> entries = entryRepository.findByWorkLogDayIdOrderByStartTimeAsc(day.getId());
        TimesheetValidator.ShiftWindow shift = validator.resolveShift(employee, settings);

        int logged = 0;
        int productive = 0;
        int meeting = 0;
        int breaks = 0;

        for (DailyWorkLog entry : entries) {
            int minutes = entry.getDurationMinutes();
            logged += minutes;
            CategoryType type = entry.getTaskCategory().getCategoryType();
            if (type == CategoryType.BREAK) {
                breaks += minutes;
            } else if (type == CategoryType.MEETING) {
                meeting += minutes;
            }
            if (entry.isProductive() && type != CategoryType.BREAK) {
                productive += minutes;
            }
        }

        int office = shift.grossMinutes();
        day.setOfficeMinutes(office);
        day.setLoggedMinutes(logged);
        day.setProductiveMinutes(productive);
        day.setMeetingMinutes(meeting);
        day.setBreakMinutes(breaks);
        day.setUnallocatedMinutes(Math.max(office - logged, 0));
        day.setOvertimeMinutes(Math.max(logged - office, 0));
        day.setEntryCount(entries.size());
        dayRepository.save(day);
    }

    private void recordApproval(WorkLogDay day, ApprovalAction action, WorkLogDayStatus from,
                                WorkLogDayStatus to, AppUserPrincipal principal, String comments) {
        WorkLogApproval approval = new WorkLogApproval();
        approval.setCompanyId(day.getCompanyId());
        approval.setWorkLogDayId(day.getId());
        approval.setAction(action);
        approval.setFromStatus(from.name());
        approval.setToStatus(to.name());
        approval.setActorEmployeeId(principal.getEmployeeId());
        approval.setActorName(principal.getDisplayName());
        approval.setComments(comments);
        approvalRepository.save(approval);
    }

    private Employee requireEmployee(Long employeeId, Long companyId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }

    private Long requireEmployeeId(AppUserPrincipal principal) {
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }

    // ----------------------------------------------------------- projections

    private WorkLogDayResponse emptyDayResponse(Employee employee, LocalDate date,
                                                CompanySettings settings) {
        TimesheetValidator.ShiftWindow shift = validator.resolveShift(employee, settings);
        int office = shift.grossMinutes();
        Optional<Holiday> holiday = findHoliday(employee.getCompanyId(), date);

        DayTotalsResponse totals = new DayTotalsResponse(office, 0, 0, 0, 0, office, 0, 0, 0, 0);
        List<TimelineSegmentResponse> timeline = List.of(
                TimelineSegmentResponse.gap(shift.start(), shift.end(), office));

        return new WorkLogDayResponse(null, employee.getId(), employee.fullName(),
                employee.getEmployeeCode(), date, dayName(date), WorkLogDayStatus.DRAFT, true,
                holiday.isPresent(), holiday.map(Holiday::getName).orElse(null),
                shift.start(), shift.end(), totals, List.of(), timeline, List.of(),
                null, null, null, null, settings.isWorklogApprovalEnabled());
    }

    private WorkLogDayResponse toDayResponse(WorkLogDay day, Employee employee, CompanySettings settings) {
        List<DailyWorkLog> entries = entryRepository.findByWorkLogDayIdOrderByStartTimeAsc(day.getId());
        TimesheetValidator.ShiftWindow shift = validator.resolveShift(employee, settings);
        Optional<Holiday> holiday = findHoliday(day.getCompanyId(), day.getWorkDate());

        List<WorkLogEntryResponse> entryResponses = entries.stream().map(this::toEntryResponse).toList();

        List<ApprovalHistoryResponse> history = approvalRepository
                .findByWorkLogDayIdOrderByCreatedAtAsc(day.getId()).stream()
                .map(row -> new ApprovalHistoryResponse(row.getId(), row.getAction(), row.getFromStatus(),
                        row.getToStatus(), row.getActorName(), row.getComments(), row.getCreatedAt()))
                .toList();

        return new WorkLogDayResponse(day.getId(), employee.getId(), employee.fullName(),
                employee.getEmployeeCode(), day.getWorkDate(), dayName(day.getWorkDate()),
                day.getStatus(), day.isEditable(), holiday.isPresent(),
                holiday.map(Holiday::getName).orElse(null), shift.start(), shift.end(),
                toTotals(day), entryResponses, buildTimeline(entries, shift), history,
                day.getSubmittedAt(),
                day.getReviewedBy() == null ? null : day.getReviewedBy().fullName(),
                day.getReviewedAt(), day.getReviewComments(), settings.isWorklogApprovalEnabled());
    }

    /**
     * Builds the ribbon: every entry in order, with the empty stretches inside
     * the shift returned as explicit gaps so the client never has to infer them.
     */
    private List<TimelineSegmentResponse> buildTimeline(List<DailyWorkLog> entries,
                                                        TimesheetValidator.ShiftWindow shift) {
        List<TimelineSegmentResponse> segments = new ArrayList<>();
        LocalTime cursor = shift.start();

        for (DailyWorkLog entry : entries) {
            if (entry.getStartTime().isAfter(cursor)) {
                segments.add(TimelineSegmentResponse.gap(cursor, entry.getStartTime(),
                        validator.minutesBetween(cursor, entry.getStartTime())));
            }
            segments.add(new TimelineSegmentResponse(entry.getStartTime(), entry.getEndTime(),
                    entry.getDurationMinutes(), entry.getTitle(),
                    entry.getTaskCategory().getCategoryType().name(),
                    entry.getTaskCategory().getColourHex(), entry.getId()));

            if (entry.getEndTime().isAfter(cursor)) {
                cursor = entry.getEndTime();
            }
        }

        if (cursor.isBefore(shift.end())) {
            segments.add(TimelineSegmentResponse.gap(cursor, shift.end(),
                    validator.minutesBetween(cursor, shift.end())));
        }
        return segments;
    }

    private DayTotalsResponse toTotals(WorkLogDay day) {
        double loggedPercent = day.getOfficeMinutes() == 0 ? 0
                : round1(day.getLoggedMinutes() * 100.0 / day.getOfficeMinutes());
        double productivePercent = day.getLoggedMinutes() == 0 ? 0
                : round1(day.getProductiveMinutes() * 100.0 / day.getLoggedMinutes());

        return new DayTotalsResponse(day.getOfficeMinutes(), day.getLoggedMinutes(),
                day.getProductiveMinutes(), day.getMeetingMinutes(), day.getBreakMinutes(),
                day.getUnallocatedMinutes(), day.getOvertimeMinutes(), day.getEntryCount(),
                loggedPercent, productivePercent);
    }

    private WorkLogEntryResponse toEntryResponse(DailyWorkLog entry) {
        return new WorkLogEntryResponse(entry.getId(), entry.getStartTime(), entry.getEndTime(),
                entry.getDurationMinutes(), entry.getTaskCategory().getId(),
                entry.getTaskCategory().getName(), entry.getTaskCategory().getCategoryType(),
                entry.getTaskCategory().getColourHex(),
                entry.getProject() == null ? null : entry.getProject().getId(),
                entry.getProject() == null ? null : entry.getProject().getName(),
                entry.getTask() == null ? null : entry.getTask().getId(),
                entry.getTask() == null ? null : entry.getTask().getTitle(),
                entry.getEmployeeKraId(), kraLookup.nameOf(entry.getEmployeeKraId()),
                entry.getTitle(), entry.getDescription(), entry.getPriority(), entry.getStatus(),
                entry.getCompletionPct(), entry.isProductive(), entry.isBillable(), entry.getRemarks());
    }

    private WorkLogDaySummaryResponse toSummary(WorkLogDay day) {
        Employee employee = day.getEmployee();
        double loggedPercent = day.getOfficeMinutes() == 0 ? 0
                : round1(day.getLoggedMinutes() * 100.0 / day.getOfficeMinutes());

        return new WorkLogDaySummaryResponse(day.getId(), employee.getId(), employee.fullName(),
                employee.getEmployeeCode(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                day.getWorkDate(), day.getStatus(), day.getLoggedMinutes(), day.getProductiveMinutes(),
                day.getOfficeMinutes(), day.getUnallocatedMinutes(), day.getEntryCount(),
                day.getSubmittedAt(), loggedPercent);
    }

    private Optional<Holiday> findHoliday(Long companyId, LocalDate date) {
        return holidayRepository
                .findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(companyId, date, date)
                .stream()
                .findFirst();
    }

    private String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Kept for the Phase 4 engine, which reads day rows in bulk. */
    @Transactional(readOnly = true)
    public Map<LocalDate, WorkLogDay> daysByDate(Long employeeId, LocalDate from, LocalDate to) {
        Map<LocalDate, WorkLogDay> byDate = new HashMap<>();
        dayRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(employeeId, from, to)
                .forEach(day -> byDate.put(day.getWorkDate(), day));
        return byDate;
    }
}
