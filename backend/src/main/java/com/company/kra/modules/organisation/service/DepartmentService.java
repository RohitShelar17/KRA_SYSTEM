package com.company.kra.modules.organisation.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.organisation.dto.DepartmentRequest;
import com.company.kra.modules.organisation.dto.DepartmentResponse;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.security.SecurityUtils;
import com.company.kra.util.Lookups;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService implements DepartmentDirectory {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDirectory employeeDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> search(String query, boolean activeOnly, Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        var page = departmentRepository.search(companyId, blankToNull(query), activeOnly, pageable);
        Map<Long, String> names = resolveNames(page.getContent());
        Map<Long, String> parents = resolveParentNames(page.getContent(), companyId);
        return PageResponse.from(page, department -> toResponse(department, names, parents));
    }

    @Transactional(readOnly = true)
    public List<OptionDto> options() {
        Long companyId = SecurityUtils.requireCompanyId();
        return departmentRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
                .map(d -> new OptionDto(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Department department = require(id, companyId);
        return toResponse(department, resolveNames(List.of(department)),
                resolveParentNames(List.of(department), companyId));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (departmentRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Department", "code", request.code());
        }
        validateHead(request.headEmployeeId());
        validateParent(request.parentDepartmentId(), null, companyId);

        Department department = new Department();
        department.setCompanyId(companyId);
        apply(department, request);
        Department saved = departmentRepository.save(department);

        auditService.record(AuditModule.ORGANISATION, AuditAction.CREATE, "Department", saved.getId(),
                null, request, null);
        log.info("Department {} created", saved.getCode());
        return get(saved.getId());
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Department department = require(id, companyId);

        if (!department.getCode().equalsIgnoreCase(request.code())
                && departmentRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Department", "code", request.code());
        }
        validateHead(request.headEmployeeId());
        validateParent(request.parentDepartmentId(), id, companyId);

        DepartmentRequest before = snapshot(department);
        apply(department, request);
        departmentRepository.save(department);

        auditService.record(AuditModule.ORGANISATION, AuditAction.UPDATE, "Department", id,
                before, request, null);
        return get(id);
    }

    /**
     * Deactivation rather than deletion: work logs, KRAs and historical
     * performance rows all point at this department and must keep resolving.
     */
    @Transactional
    public void deactivate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Department department = require(id, companyId);

        if (employeeRepository.countByDepartmentIdAndActiveTrue(id) > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_EMPTY,
                    "Move or deactivate the employees in this department first");
        }
        if (departmentRepository.existsByParentDepartmentId(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Reassign the sub-departments of this department first");
        }

        department.setActive(false);
        departmentRepository.save(department);
        auditService.record(AuditModule.ORGANISATION, AuditAction.DELETE, "Department", id,
                null, null, "Deactivated");
    }

    private void apply(Department department, DepartmentRequest request) {
        department.setCode(request.code().toUpperCase());
        department.setName(request.name().trim());
        department.setDescription(request.description());
        department.setParentDepartmentId(request.parentDepartmentId());
        department.setHeadEmployeeId(request.headEmployeeId());
    }

    private DepartmentRequest snapshot(Department department) {
        return new DepartmentRequest(department.getCode(), department.getName(), department.getDescription(),
                department.getParentDepartmentId(), department.getHeadEmployeeId());
    }

    private void validateHead(Long headEmployeeId) {
        if (headEmployeeId != null && !employeeDirectory.exists(headEmployeeId)) {
            throw new ResourceNotFoundException("Employee", headEmployeeId);
        }
    }

    private void validateParent(Long parentId, Long selfId, Long companyId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A department cannot report to itself");
        }
        Department parent = require(parentId, companyId);
        // Walk up to make sure the new parent is not already a descendant.
        Set<Long> seen = new HashSet<>();
        Long cursor = parent.getParentDepartmentId();
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(selfId)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "That parent would create a circular department structure");
            }
            cursor = departmentRepository.findByIdAndCompanyId(cursor, companyId)
                    .map(Department::getParentDepartmentId)
                    .orElse(null);
        }
    }

    private Department require(Long id, Long companyId) {
        return departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private Map<Long, String> resolveNames(List<Department> departments) {
        Set<Long> headIds = departments.stream()
                .map(Department::getHeadEmployeeId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return headIds.isEmpty() ? Map.of() : employeeDirectory.namesOf(headIds);
    }

    private Map<Long, String> resolveParentNames(List<Department> departments, Long companyId) {
        Map<Long, String> parents = new HashMap<>();
        departments.stream()
                .map(Department::getParentDepartmentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(parentId -> departmentRepository.findByIdAndCompanyId(parentId, companyId)
                        .ifPresent(parent -> parents.put(parentId, parent.getName())));
        return parents;
    }

    private DepartmentResponse toResponse(Department department, Map<Long, String> headNames,
                                          Map<Long, String> parentNames) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getDescription(),
                department.getParentDepartmentId(),
                Lookups.get(parentNames, department.getParentDepartmentId()),
                department.getHeadEmployeeId(),
                Lookups.get(headNames, department.getHeadEmployeeId()),
                employeeRepository.countByDepartmentIdAndActiveTrue(department.getId()),
                department.isActive());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ----------------------------------------------------- DepartmentDirectory

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> headOf(Long departmentId) {
        if (departmentId == null) {
            return Optional.empty();
        }
        return departmentRepository.findById(departmentId).map(Department::getHeadEmployeeId);
    }

    @Override
    @Transactional(readOnly = true)
    public String nameOf(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
    }
}
