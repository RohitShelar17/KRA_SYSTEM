package com.company.kra.modules.employee.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.enums.RelationType;
import com.company.kra.common.enums.UserStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.dto.EmployeeRequest;
import com.company.kra.modules.employee.dto.EmployeeResponse;
import com.company.kra.modules.employee.dto.EmployeeSummaryResponse;
import com.company.kra.modules.employee.dto.EmployeeUpdateRequest;
import com.company.kra.modules.employee.dto.ManagerChangeRequest;
import com.company.kra.modules.employee.dto.ReportingLineResponse;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.entity.ReportingHierarchy;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.employee.repository.ReportingHierarchyRepository;
import com.company.kra.modules.identity.entity.Role;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.RoleRepository;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.entity.Designation;
import com.company.kra.modules.organisation.entity.Team;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.modules.organisation.repository.TeamRepository;
import com.company.kra.security.AccessScopeResolver;
import org.springframework.context.ApplicationEventPublisher;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import com.company.kra.util.Lookups;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Employee records and the reporting hierarchy.
 *
 * <p>Creating an employee also creates their sign-in account and opens their
 * reporting line, all in one transaction: a person who exists but cannot log in,
 * or who reports to nobody, is not a state worth persisting.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService implements EmployeeDirectory {

    private static final int MAX_HIERARCHY_DEPTH = 12;

    private final EmployeeRepository employeeRepository;
    private final ReportingHierarchyRepository hierarchyRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final AccountInviteService inviteService;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryResponse> search(String query, Long departmentId, Long teamId,
                                                        Long designationId, Long managerId,
                                                        EmployeeStatus status, boolean activeOnly,
                                                        Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        // An empty scope would match every row under "IN ()", so short-circuit.
        if (!scope.organisationWide() && scope.employeeIds().isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                    0, 0, true, true);
        }

        Page<Employee> page = employeeRepository.search(
                principal.getCompanyId(),
                scope.organisationWide(),
                scope.organisationWide() ? Set.of(-1L) : scope.employeeIds(),
                departmentId, teamId, designationId, managerId, status, activeOnly,
                blankToNull(query), pageable);

        return PageResponse.from(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Employee employee = require(id, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(id);
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse me() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return toResponse(require(principal.getEmployeeId(), principal.getCompanyId()));
    }

    /** Direct reports of an employee, used by the manager dashboard and team screens. */
    @Transactional(readOnly = true)
    public List<EmployeeSummaryResponse> directReports(Long managerId) {
        accessScopeResolver.assertCanAccess(managerId);
        return employeeRepository.findByReportingManagerIdAndActiveTrue(managerId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OptionDto> options() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        return employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(principal.getCompanyId())
                .stream()
                .map(e -> new OptionDto(e.getId(), e.getEmployeeCode(), e.fullName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportingLineResponse> reportingHistory(Long employeeId) {
        accessScopeResolver.assertCanAccess(employeeId);
        List<ReportingHierarchy> rows = hierarchyRepository.findByEmployeeIdOrderByValidFromDesc(employeeId);
        Set<Long> managerIds = rows.stream().map(ReportingHierarchy::getManagerId).collect(Collectors.toSet());
        Map<Long, String> names = namesOf(managerIds);
        return rows.stream()
                .map(row -> new ReportingLineResponse(row.getId(), row.getEmployeeId(), row.getManagerId(),
                        Lookups.get(names, row.getManagerId()), row.getRelationType(), row.getValidFrom(),
                        row.getValidTo(), row.getValidTo() == null))
                .toList();
    }

    // --------------------------------------------------------------- writes

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();

        if (employeeRepository.existsByEmployeeCodeIgnoreCaseAndCompanyId(request.employeeCode(), companyId)) {
            throw new DuplicateResourceException("Employee", "code", request.employeeCode());
        }
        if (employeeRepository.existsByEmailIgnoreCase(request.email())
                || userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Employee", "email", request.email());
        }

        Department department = requireDepartment(request.departmentId(), companyId);
        Designation designation = requireDesignation(request.designationId(), companyId);
        Team team = request.teamId() == null ? null : requireTeam(request.teamId(), companyId);
        Employee manager = request.reportingManagerId() == null ? null
                : require(request.reportingManagerId(), companyId);

        User user = createUserAccount(request, companyId);

        Employee employee = new Employee();
        employee.setCompanyId(companyId);
        employee.setEmployeeCode(request.employeeCode().toUpperCase());
        employee.setEmail(request.email().toLowerCase());
        employee.setUser(user);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setPhone(emptyToNull(request.phone()));
        employee.setDepartment(department);
        employee.setTeam(team);
        employee.setDesignation(designation);
        employee.setReportingManager(manager);
        employee.setJoiningDate(request.joiningDate());
        employee.setEmploymentType(request.employmentType());
        employee.setWorkLocation(request.workLocation());
        employee.setWorkMode(request.workMode());
        employee.setShiftStartTime(request.shiftStartTime());
        employee.setShiftEndTime(request.shiftEndTime());
        employee.setStatus(request.status() == null ? EmployeeStatus.ACTIVE : request.status());

        Employee saved = employeeRepository.save(employee);

        if (manager != null) {
            openReportingLine(saved.getId(), manager.getId(), companyId, request.joiningDate());
        }

        auditService.record(AuditModule.EMPLOYEE, AuditAction.CREATE, "Employee", saved.getId(),
                null, summaryForAudit(saved), null);
        // Raised rather than sent: the listener runs after this commits, so
        // nobody is invited to an account that then rolled back.
        events.publishEvent(inviteService.inviteEvent(saved, user));
        log.info("Employee {} created with account {}", saved.getEmployeeCode(), user.getUsername());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = require(id, companyId);

        Map<String, Object> before = summaryForAudit(employee);

        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setPhone(emptyToNull(request.phone()));
        employee.setDepartment(requireDepartment(request.departmentId(), companyId));
        employee.setTeam(request.teamId() == null ? null : requireTeam(request.teamId(), companyId));
        employee.setDesignation(requireDesignation(request.designationId(), companyId));
        employee.setJoiningDate(request.joiningDate());
        employee.setExitDate(request.exitDate());
        employee.setEmploymentType(request.employmentType());
        employee.setWorkLocation(request.workLocation());
        employee.setWorkMode(request.workMode());
        employee.setShiftStartTime(request.shiftStartTime());
        employee.setShiftEndTime(request.shiftEndTime());
        employee.setStatus(request.status());

        if (request.roleCodes() != null && !request.roleCodes().isEmpty() && employee.getUser() != null) {
            assignRoles(employee.getUser(), request.roleCodes(), companyId);
            userRepository.save(employee.getUser());
        }

        Employee saved = employeeRepository.save(employee);
        auditService.record(AuditModule.EMPLOYEE, AuditAction.UPDATE, "Employee", id,
                before, summaryForAudit(saved), null);
        return toResponse(saved);
    }

    /**
     * Manager changes close the current hierarchy row and open a new one, so a
     * historical appraisal still resolves the manager of the day.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public EmployeeResponse changeManager(Long employeeId, ManagerChangeRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = require(employeeId, companyId);
        Employee newManager = require(request.managerId(), companyId);

        if (employeeId.equals(request.managerId())) {
            throw new BusinessException(ErrorCode.CIRCULAR_REPORTING, "An employee cannot report to themselves");
        }
        assertNoReportingCycle(employeeId, request.managerId());

        Long previousManagerId = employee.getReportingManager() == null ? null
                : employee.getReportingManager().getId();

        hierarchyRepository.findOpenPrimary(employeeId).ifPresent(open -> {
            open.setValidTo(request.effectiveFrom().minusDays(1));
            hierarchyRepository.save(open);
        });
        openReportingLine(employeeId, newManager.getId(), companyId, request.effectiveFrom());

        employee.setReportingManager(newManager);
        Employee saved = employeeRepository.save(employee);

        auditService.record(AuditModule.EMPLOYEE, AuditAction.ASSIGN, "Employee", employeeId,
                Map.of("reportingManagerId", String.valueOf(previousManagerId)),
                Map.of("reportingManagerId", String.valueOf(newManager.getId()),
                        "effectiveFrom", request.effectiveFrom().toString()),
                request.reason());
        log.info("Employee {} now reports to {}", employee.getEmployeeCode(), newManager.getEmployeeCode());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public void deactivate(Long id, String reason) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = require(id, companyId);

        List<Employee> reports = employeeRepository.findByReportingManagerIdAndActiveTrue(id);
        if (!reports.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPLOYEE_HAS_REPORTS,
                    "Reassign the " + reports.size() + " employee(s) reporting to this person first");
        }

        employee.setActive(false);
        employee.setStatus(EmployeeStatus.INACTIVE);
        if (employee.getExitDate() == null) {
            employee.setExitDate(LocalDate.now());
        }
        employeeRepository.save(employee);

        hierarchyRepository.findOpenPrimary(id).ifPresent(open -> {
            open.setValidTo(LocalDate.now());
            hierarchyRepository.save(open);
        });

        if (employee.getUser() != null) {
            User user = employee.getUser();
            user.setStatus(UserStatus.INACTIVE);
            user.setActive(false);
            userRepository.save(user);
        }

        auditService.record(AuditModule.EMPLOYEE, AuditAction.DELETE, "Employee", id, null, null, reason);
        log.info("Employee {} deactivated", employee.getEmployeeCode());
    }

    // ------------------------------------------------------- directory API

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Set<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        employeeRepository.findAllById(employeeIds)
                .forEach(employee -> names.put(employee.getId(), employee.fullName()));
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EmployeeDirectory.Account> accountsOf(Set<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EmployeeDirectory.Account> accounts = new HashMap<>();
        for (Employee employee : employeeRepository.findAllById(employeeIds)) {
            User user = employee.getUser();
            if (user == null) {
                // A record with no account cannot be reached; the caller sees
                // the id missing rather than a half-built recipient.
                continue;
            }
            accounts.put(employee.getId(), new EmployeeDirectory.Account(
                    employee.getId(), user.getId(), employee.fullName(), user.getEmail()));
        }
        return accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public String nameOf(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employeeRepository.findById(employeeId).map(Employee::fullName).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long employeeId) {
        return employeeId != null && employeeRepository.existsById(employeeId);
    }

    // ------------------------------------------------------------ internals

    private User createUserAccount(EmployeeRequest request, Long companyId) {
        String username = request.email().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("User account", "username", username);
        }

        User user = new User();
        user.setCompanyId(companyId);
        user.setUsername(username);
        user.setEmail(username);
        // A random password no one holds: the person sets their own through the
        // reset flow, so no temporary credential is ever transmitted.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        // Not active until the invite is used. An account that cannot be signed
        // into should not read as open in the directory, and the reset flow
        // flips this to ACTIVE the moment a password is chosen.
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setMustChangePassword(true);
        inviteService.issueToken(user);
        assignRoles(user, request.roleCodes(), companyId);
        return userRepository.save(user);
    }

    private void assignRoles(User user, Set<String> roleCodes, Long companyId) {
        List<Role> roles = roleRepository.findByCodeInAndCompanyId(new ArrayList<>(roleCodes), companyId);
        if (roles.size() != roleCodes.size()) {
            Set<String> found = roles.stream().map(Role::getCode).collect(Collectors.toSet());
            Set<String> missing = new LinkedHashSet<>(roleCodes);
            missing.removeAll(found);
            throw new ResourceNotFoundException("Role", String.join(", ", missing));
        }
        user.setRoles(new LinkedHashSet<>(roles));
    }

    private void openReportingLine(Long employeeId, Long managerId, Long companyId, LocalDate from) {
        ReportingHierarchy line = new ReportingHierarchy();
        line.setCompanyId(companyId);
        line.setEmployeeId(employeeId);
        line.setManagerId(managerId);
        line.setRelationType(RelationType.PRIMARY);
        line.setValidFrom(from == null ? LocalDate.now() : from);
        hierarchyRepository.save(line);
    }

    /** Walks up from the proposed manager; meeting the employee means a cycle. */
    private void assertNoReportingCycle(Long employeeId, Long proposedManagerId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = proposedManagerId;
        int depth = 0;

        while (cursor != null && depth++ < MAX_HIERARCHY_DEPTH) {
            if (cursor.equals(employeeId)) {
                throw new BusinessException(ErrorCode.CIRCULAR_REPORTING,
                        "That manager already reports to this employee");
            }
            if (!visited.add(cursor)) {
                break;
            }
            Long next = employeeRepository.findById(cursor)
                    .map(Employee::getReportingManager)
                    .map(Employee::getId)
                    .orElse(null);
            cursor = next;
        }
    }

    private Employee require(Long id, Long companyId) {
        return employeeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private Department requireDepartment(Long id, Long companyId) {
        return departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private Designation requireDesignation(Long id, Long companyId) {
        return designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation", id));
    }

    private Team requireTeam(Long id, Long companyId) {
        return teamRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
    }

    private EmployeeSummaryResponse toSummary(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.fullName(),
                employee.getEmail(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee.getTeam() == null ? null : employee.getTeam().getName(),
                employee.getDesignation() == null ? null : employee.getDesignation().getName(),
                employee.getReportingManager() == null ? null : employee.getReportingManager().fullName(),
                employee.getStatus(),
                employee.getJoiningDate(),
                employee.getProfileImageUrl(),
                employee.isActive(),
                employee.getUser() == null ? null : employee.getUser().getStatus().name());
    }

    private EmployeeResponse toResponse(Employee employee) {
        User user = employee.getUser();
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.fullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getDepartment().getId(),
                employee.getDepartment().getName(),
                employee.getTeam() == null ? null : employee.getTeam().getId(),
                employee.getTeam() == null ? null : employee.getTeam().getName(),
                employee.getDesignation().getId(),
                employee.getDesignation().getName(),
                employee.getDesignation().getGradeLevel(),
                employee.getReportingManager() == null ? null : employee.getReportingManager().getId(),
                employee.getReportingManager() == null ? null : employee.getReportingManager().fullName(),
                employee.getJoiningDate(),
                employee.getExitDate(),
                employee.getEmploymentType(),
                employee.getWorkLocation(),
                employee.getWorkMode(),
                employee.getShiftStartTime(),
                employee.getShiftEndTime(),
                employee.getStatus(),
                employee.getProfileImageUrl(),
                employee.isActive(),
                user == null ? null : user.getId(),
                user == null ? null : user.getStatus().name(),
                user == null ? Set.of() : user.roleCodes(),
                employeeRepository.findByReportingManagerIdAndActiveTrue(employee.getId()).size());
    }

    private Map<String, Object> summaryForAudit(Employee employee) {
        Map<String, Object> map = new HashMap<>();
        map.put("employeeCode", employee.getEmployeeCode());
        map.put("fullName", employee.fullName());
        map.put("email", employee.getEmail());
        map.put("departmentId", employee.getDepartment() == null ? null : employee.getDepartment().getId());
        map.put("designationId", employee.getDesignation() == null ? null : employee.getDesignation().getId());
        map.put("status", employee.getStatus().name());
        return map;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
