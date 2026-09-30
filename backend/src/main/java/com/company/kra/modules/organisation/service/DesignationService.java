package com.company.kra.modules.organisation.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.organisation.dto.DesignationRequest;
import com.company.kra.modules.organisation.dto.DesignationResponse;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.entity.Designation;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.security.SecurityUtils;
import com.company.kra.util.Lookups;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DesignationResponse> list(Long departmentId) {
        Long companyId = SecurityUtils.requireCompanyId();
        List<Designation> designations = departmentId == null
                ? designationRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId)
                : designationRepository.findByCompanyIdAndDepartmentIdAndActiveTrueOrderByNameAsc(
                companyId, departmentId);
        Map<Long, String> departmentNames = departmentNames(designations, companyId);
        return designations.stream().map(d -> toResponse(d, departmentNames)).toList();
    }

    @Transactional(readOnly = true)
    public List<OptionDto> options(Long departmentId) {
        return list(departmentId).stream()
                .map(d -> new OptionDto(d.id(), d.code(), d.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DesignationResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Designation designation = require(id, companyId);
        return toResponse(designation, departmentNames(List.of(designation), companyId));
    }

    @Transactional
    public DesignationResponse create(DesignationRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (designationRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Designation", "code", request.code());
        }
        validateDepartment(request.departmentId(), companyId);

        Designation designation = new Designation();
        designation.setCompanyId(companyId);
        apply(designation, request);
        Designation saved = designationRepository.save(designation);

        auditService.record(AuditModule.ORGANISATION, AuditAction.CREATE, "Designation", saved.getId(),
                null, request, null);
        return get(saved.getId());
    }

    @Transactional
    public DesignationResponse update(Long id, DesignationRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Designation designation = require(id, companyId);

        if (!designation.getCode().equalsIgnoreCase(request.code())
                && designationRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Designation", "code", request.code());
        }
        validateDepartment(request.departmentId(), companyId);

        DesignationRequest before = new DesignationRequest(designation.getCode(), designation.getName(),
                designation.getGradeLevel(), designation.getDepartmentId(), designation.getDescription());
        apply(designation, request);
        designationRepository.save(designation);

        auditService.record(AuditModule.ORGANISATION, AuditAction.UPDATE, "Designation", id,
                before, request, null);
        return get(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Designation designation = require(id, companyId);
        designation.setActive(false);
        designationRepository.save(designation);
        auditService.record(AuditModule.ORGANISATION, AuditAction.DELETE, "Designation", id,
                null, null, "Deactivated");
    }

    private void apply(Designation designation, DesignationRequest request) {
        designation.setCode(request.code().toUpperCase());
        designation.setName(request.name().trim());
        designation.setGradeLevel(request.gradeLevel());
        designation.setDepartmentId(request.departmentId());
        designation.setDescription(request.description());
    }

    private void validateDepartment(Long departmentId, Long companyId) {
        if (departmentId != null && departmentRepository.findByIdAndCompanyId(departmentId, companyId).isEmpty()) {
            throw new ResourceNotFoundException("Department", departmentId);
        }
    }

    private Designation require(Long id, Long companyId) {
        return designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation", id));
    }

    private Map<Long, String> departmentNames(List<Designation> designations, Long companyId) {
        return designations.stream()
                .map(Designation::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> departmentRepository.findByIdAndCompanyId(id, companyId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private DesignationResponse toResponse(Designation designation, Map<Long, String> departmentNames) {
        return new DesignationResponse(designation.getId(), designation.getCode(), designation.getName(),
                designation.getGradeLevel(), designation.getDepartmentId(),
                Lookups.get(departmentNames, designation.getDepartmentId()), designation.getDescription(),
                designation.isActive());
    }
}
