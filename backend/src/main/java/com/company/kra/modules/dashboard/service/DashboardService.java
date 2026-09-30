package com.company.kra.modules.dashboard.service;

import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.modules.dashboard.dto.FoundationOverviewResponse;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.modules.organisation.repository.TeamRepository;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final DesignationRepository designationRepository;
    private final AccessScopeResolver accessScopeResolver;

    @Transactional(readOnly = true)
    public FoundationOverviewResponse overview() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long companyId = principal.getCompanyId();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        long totalEmployees = employeeRepository.countByCompanyIdAndActiveTrue(companyId);
        long onNotice = employeeRepository.countByCompanyIdAndStatusAndActiveTrue(companyId,
                EmployeeStatus.ON_NOTICE);
        long active = employeeRepository.countByCompanyIdAndStatusAndActiveTrue(companyId,
                EmployeeStatus.ACTIVE);

        List<FoundationOverviewResponse.DepartmentHeadcount> headcounts =
                departmentRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
                        .map(department -> new FoundationOverviewResponse.DepartmentHeadcount(
                                department.getId(), department.getName(),
                                employeeRepository.countByDepartmentIdAndActiveTrue(department.getId())))
                        .toList();

        int directReports = principal.getEmployeeId() == null ? 0
                : employeeRepository.findByReportingManagerIdAndActiveTrue(principal.getEmployeeId()).size();

        return new FoundationOverviewResponse(
                totalEmployees,
                active,
                onNotice,
                departmentRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                teamRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                designationRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                directReports,
                scope.organisationWide() ? (int) totalEmployees : scope.employeeIds().size(),
                principal.getDataScope().name(),
                headcounts);
    }
}
