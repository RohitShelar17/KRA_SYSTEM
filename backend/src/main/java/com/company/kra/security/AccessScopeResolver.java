package com.company.kra.security;

import com.company.kra.common.enums.DataScope;
import com.company.kra.exception.AccessScopeException;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Answers "to whom" a permission applies.
 *
 * <p>A permission says what a caller may do; this resolver says which employee
 * records they may do it to. Every query that returns employee-bound data takes
 * the result of {@link #visibleEmployeeIds()} instead of trusting a request
 * parameter, which is the single control that stops a manager reading another
 * team's records.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessScopeResolver {

    /** Guards against a malformed hierarchy turning the subtree walk into a loop. */
    private static final int MAX_DEPTH = 12;

    private final EmployeeRepository employeeRepository;

    public record Scope(boolean organisationWide, Set<Long> employeeIds) {

        public boolean covers(Long employeeId) {
            return organisationWide || (employeeId != null && employeeIds.contains(employeeId));
        }
    }

    @Transactional(readOnly = true)
    public Scope visibleEmployeeIds() {
        return visibleEmployeeIds(SecurityUtils.requirePrincipal());
    }

    @Transactional(readOnly = true)
    public Scope visibleEmployeeIds(AppUserPrincipal principal) {
        DataScope scope = principal.getDataScope();
        if (scope == DataScope.ORGANISATION) {
            return new Scope(true, Set.of());
        }

        Set<Long> ids = new HashSet<>();
        if (principal.getEmployeeId() != null) {
            ids.add(principal.getEmployeeId());
        }

        switch (scope) {
            case DEPARTMENT -> {
                if (principal.getDepartmentId() != null) {
                    ids.addAll(employeeRepository.findIdsByDepartmentId(principal.getDepartmentId()));
                }
            }
            case REPORTS -> {
                if (principal.getEmployeeId() != null) {
                    ids.addAll(reportingSubtree(principal.getEmployeeId()));
                }
            }
            default -> {
                // SELF: only the caller's own record.
            }
        }
        return new Scope(false, ids);
    }

    /** Direct and indirect reports, walked breadth-first with a depth guard. */
    private Set<Long> reportingSubtree(Long managerEmployeeId) {
        Set<Long> collected = new HashSet<>();
        Set<Long> frontier = Set.of(managerEmployeeId);
        int depth = 0;

        while (!frontier.isEmpty() && depth < MAX_DEPTH) {
            Set<Long> next = employeeRepository.findIdsByManagerIds(frontier);
            next.removeAll(collected);
            next.remove(managerEmployeeId);
            if (next.isEmpty()) {
                break;
            }
            collected.addAll(next);
            frontier = next;
            depth++;
        }
        if (depth == MAX_DEPTH) {
            log.warn("Reporting subtree for employee {} hit the depth guard; check for a reporting cycle",
                    managerEmployeeId);
        }
        return collected;
    }

    /** Throws rather than returning a filtered result, so a caller learns nothing about the record. */
    public void assertCanAccess(Long employeeId) {
        if (!visibleEmployeeIds().covers(employeeId)) {
            throw new AccessScopeException("Employee " + employeeId + " is outside your access scope");
        }
    }
}
