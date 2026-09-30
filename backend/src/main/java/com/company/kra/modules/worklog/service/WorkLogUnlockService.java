package com.company.kra.modules.worklog.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
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
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Reopening one past date for one person.
 *
 * <p>This is the thing the back-dating error has always told people to ask
 * for. The alternative an administrator has is to widen
 * {@code backdateWindowDays}, which reopens every past day for everybody at
 * once; a grant here reopens one day for one person, records who allowed it and
 * why, and closes again on its own.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkLogUnlockService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    private final WorkLogUnlockRepository unlockRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanySettingsService settingsService;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    /** How long somebody has to actually file once a date is reopened. */
    @Value("${app.worklog.unlock-valid-hours:72}")
    private int validHours;

    /**
     * True when this person may file this date despite the window. Called on
     * the entry path, so it answers only that and does not care which grant
     * allowed it.
     */
    @Transactional(readOnly = true)
    public boolean isUnlocked(Long employeeId, LocalDate workDate) {
        return unlockRepository.isUnlocked(employeeId, workDate, LocalDateTime.now());
    }

    @Transactional
    public UnlockResponse grant(UnlockRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long companyId = principal.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(request.employeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", request.employeeId()));

        accessScopeResolver.assertCanAccess(employee.getId());
        assertNotSelf(principal, employee);
        assertWorthReopening(request.workDate(), companyId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(validHours);

        // A second request for the same day extends the grant rather than
        // stacking another row, so "what is open" stays answerable.
        WorkLogUnlock unlock = unlockRepository.findLive(employee.getId(), request.workDate(), now)
                .orElseGet(WorkLogUnlock::new);
        unlock.setCompanyId(companyId);
        unlock.setEmployeeId(employee.getId());
        unlock.setWorkDate(request.workDate());
        unlock.setReason(request.reason().trim());
        unlock.setGrantedById(principal.getEmployeeId());
        unlock.setGrantedByName(principal.getDisplayName());
        unlock.setExpiresAt(expiry);
        unlock.setRevokedAt(null);
        unlock.setRevokedById(null);
        WorkLogUnlock saved = unlockRepository.save(unlock);

        auditService.record(AuditModule.WORKLOG, AuditAction.REOPEN, "WorkLogUnlock", saved.getId(),
                null, Map.of("employee", employee.getEmployeeCode(),
                        "workDate", request.workDate().toString(),
                        "expiresAt", expiry.toString()),
                request.reason());

        events.publishEvent(NotificationRaised.to(companyId, "WORKLOG_UNLOCKED")
                .recipient(employee.getId())
                .about("WorkLogUnlock", saved.getId())
                .with("workDate", request.workDate().format(DAY))
                // The sentence wants "Wednesday, 9 September"; the link wants
                // something a query string can carry.
                .with("workDateIso", request.workDate())
                .with("grantedBy", principal.getDisplayName())
                .with("reason", request.reason().trim())
                .with("expiresAt", expiry.format(WHEN))
                .build());

        log.info("Work log date {} reopened for {} by user {} until {}", request.workDate(),
                employee.getEmployeeCode(), principal.getUserId(), expiry);
        return toResponse(saved, employee);
    }

    @Transactional
    public void revoke(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        WorkLogUnlock unlock = unlockRepository
                .findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Unlock", id));

        accessScopeResolver.assertCanAccess(unlock.getEmployeeId());
        if (unlock.getRevokedAt() != null) {
            // Already closed; saying so beats silently reporting success.
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This date has already been closed again");
        }

        unlock.setRevokedAt(LocalDateTime.now());
        unlock.setRevokedById(principal.getEmployeeId());
        unlockRepository.save(unlock);

        auditService.record(AuditModule.WORKLOG, AuditAction.UPDATE, "WorkLogUnlock", id,
                Map.of("state", "open"), Map.of("state", "revoked"), "Reopened date closed early");
        log.info("Work log unlock {} revoked by user {}", id, principal.getUserId());
    }

    /** What is open, within the caller's scope. Entries already filed are not affected by either. */
    @Transactional(readOnly = true)
    public List<UnlockResponse> list(boolean liveOnly) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        List<WorkLogUnlock> rows = unlockRepository.search(principal.getCompanyId(),
                scope.organisationWide(), scope.employeeIds(), liveOnly, LocalDateTime.now());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Employee> people = employeeRepository
                .findAllById(rows.stream().map(WorkLogUnlock::getEmployeeId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Employee::getId, employee -> employee));

        return rows.stream().map(row -> toResponse(row, people.get(row.getEmployeeId()))).toList();
    }

    /**
     * A manager may not reopen their own day. An exception to a control that
     * the same person both needs and approves is not a control; whoever they
     * report to can grant it, and an administrator can still widen the window
     * for everybody, which is equally on the record.
     */
    private void assertNotSelf(AppUserPrincipal principal, Employee employee) {
        if (employee.getId().equals(principal.getEmployeeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You cannot reopen your own date. Ask your reporting manager.");
        }
    }

    /**
     * Refuses the two cases where a grant would be meaningless: a date the
     * window already allows, and one that has not happened. Creating a row
     * either way would leave "what is open" full of rows that changed nothing.
     */
    private void assertWorthReopening(LocalDate workDate, Long companyId) {
        LocalDate today = LocalDate.now();
        if (workDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "You cannot reopen a date in the future");
        }
        CompanySettings settings = settingsService.settingsFor(companyId);
        int window = settings.getBackdateWindowDays();
        if (!workDate.isBefore(today.minusDays(window))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That date is still inside the " + window + "-day back-dating window, "
                            + "so it can be filed without reopening it");
        }
    }

    private UnlockResponse toResponse(WorkLogUnlock unlock, Employee employee) {
        return new UnlockResponse(
                unlock.getId(),
                unlock.getEmployeeId(),
                employee == null ? null : employee.fullName(),
                employee == null ? null : employee.getEmployeeCode(),
                unlock.getWorkDate(),
                unlock.getReason(),
                unlock.getGrantedByName(),
                unlock.getCreatedAt(),
                unlock.getExpiresAt(),
                unlock.getRevokedAt(),
                unlock.isLive(LocalDateTime.now()));
    }
}
