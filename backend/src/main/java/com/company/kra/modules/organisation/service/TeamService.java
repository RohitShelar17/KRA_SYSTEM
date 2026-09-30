package com.company.kra.modules.organisation.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.organisation.dto.TeamRequest;
import com.company.kra.modules.organisation.dto.TeamResponse;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.entity.Team;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.TeamRepository;
import com.company.kra.security.SecurityUtils;
import com.company.kra.util.Lookups;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeDirectory employeeDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<TeamResponse> list(Long departmentId) {
        Long companyId = SecurityUtils.requireCompanyId();
        List<Team> teams = departmentId == null
                ? teamRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId)
                : teamRepository.findByCompanyIdAndDepartmentIdAndActiveTrueOrderByNameAsc(companyId, departmentId);
        return toResponses(teams, companyId);
    }

    @Transactional(readOnly = true)
    public List<OptionDto> options(Long departmentId) {
        return list(departmentId).stream()
                .map(t -> new OptionDto(t.id(), t.code(), t.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Team team = require(id, companyId);
        return toResponses(List.of(team), companyId).get(0);
    }

    @Transactional
    public TeamResponse create(TeamRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (teamRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Team", "code", request.code());
        }
        requireDepartment(request.departmentId(), companyId);
        validateLead(request.teamLeadEmployeeId());

        Team team = new Team();
        team.setCompanyId(companyId);
        apply(team, request);
        Team saved = teamRepository.save(team);

        auditService.record(AuditModule.ORGANISATION, AuditAction.CREATE, "Team", saved.getId(),
                null, request, null);
        return get(saved.getId());
    }

    @Transactional
    public TeamResponse update(Long id, TeamRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Team team = require(id, companyId);

        if (!team.getCode().equalsIgnoreCase(request.code())
                && teamRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Team", "code", request.code());
        }
        requireDepartment(request.departmentId(), companyId);
        validateLead(request.teamLeadEmployeeId());

        TeamRequest before = new TeamRequest(team.getDepartmentId(), team.getCode(), team.getName(),
                team.getDescription(), team.getTeamLeadEmployeeId());
        apply(team, request);
        teamRepository.save(team);

        auditService.record(AuditModule.ORGANISATION, AuditAction.UPDATE, "Team", id, before, request, null);
        return get(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Team team = require(id, companyId);
        team.setActive(false);
        teamRepository.save(team);
        auditService.record(AuditModule.ORGANISATION, AuditAction.DELETE, "Team", id, null, null, "Deactivated");
    }

    private void apply(Team team, TeamRequest request) {
        team.setDepartmentId(request.departmentId());
        team.setCode(request.code().toUpperCase());
        team.setName(request.name().trim());
        team.setDescription(request.description());
        team.setTeamLeadEmployeeId(request.teamLeadEmployeeId());
    }

    private void validateLead(Long leadEmployeeId) {
        if (leadEmployeeId != null && !employeeDirectory.exists(leadEmployeeId)) {
            throw new ResourceNotFoundException("Employee", leadEmployeeId);
        }
    }

    private Department requireDepartment(Long departmentId, Long companyId) {
        return departmentRepository.findByIdAndCompanyId(departmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    }

    private Team require(Long id, Long companyId) {
        return teamRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
    }

    private List<TeamResponse> toResponses(List<Team> teams, Long companyId) {
        Map<Long, String> departmentNames = teams.stream()
                .map(Team::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> departmentRepository.findByIdAndCompanyId(id, companyId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Set<Long> leadIds = teams.stream()
                .map(Team::getTeamLeadEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> leadNames = leadIds.isEmpty() ? Map.of() : employeeDirectory.namesOf(leadIds);

        return teams.stream()
                .map(team -> new TeamResponse(team.getId(), team.getDepartmentId(),
                        Lookups.get(departmentNames, team.getDepartmentId()), team.getCode(), team.getName(),
                        team.getDescription(), team.getTeamLeadEmployeeId(),
                        Lookups.get(leadNames, team.getTeamLeadEmployeeId()), team.isActive()))
                .toList();
    }
}
