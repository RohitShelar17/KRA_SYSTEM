package com.company.kra.modules.task.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.ProjectStatus;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.task.dto.ProjectRequest;
import com.company.kra.modules.task.dto.ProjectResponse;
import com.company.kra.modules.task.entity.Project;
import com.company.kra.modules.task.repository.ProjectRepository;
import com.company.kra.modules.task.repository.TaskRepository;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeDirectory employeeDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        List<Project> projects = projectRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId);
        return toResponses(projects, companyId);
    }

    @Transactional(readOnly = true)
    public List<OptionDto> options() {
        Long companyId = SecurityUtils.requireCompanyId();
        return projectRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
                .filter(project -> project.getStatus() == ProjectStatus.ACTIVE
                        || project.getStatus() == ProjectStatus.PLANNED)
                .map(project -> new OptionDto(project.getId(), project.getCode(), project.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        return toResponses(List.of(require(id, companyId)), companyId).get(0);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (projectRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Project", "code", request.code());
        }
        validate(request, companyId);

        Project project = new Project();
        project.setCompanyId(companyId);
        apply(project, request);
        Project saved = projectRepository.save(project);

        auditService.record(AuditModule.TASK, AuditAction.CREATE, "Project", saved.getId(),
                null, request, null);
        return get(saved.getId());
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Project project = require(id, companyId);

        if (!project.getCode().equalsIgnoreCase(request.code())
                && projectRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Project", "code", request.code());
        }
        validate(request, companyId);

        ProjectRequest before = snapshot(project);
        apply(project, request);
        projectRepository.save(project);

        auditService.record(AuditModule.TASK, AuditAction.UPDATE, "Project", id, before, request, null);
        return get(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        Project project = require(id, companyId);
        project.setActive(false);
        projectRepository.save(project);
        auditService.record(AuditModule.TASK, AuditAction.DELETE, "Project", id, null, null, "Deactivated");
    }

    private void apply(Project project, ProjectRequest request) {
        project.setCode(request.code().toUpperCase());
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setDepartmentId(request.departmentId());
        project.setProjectManagerId(request.projectManagerId());
        project.setClientName(request.clientName());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(request.status() == null ? ProjectStatus.ACTIVE : request.status());
        project.setBillable(request.billable());
    }

    private ProjectRequest snapshot(Project project) {
        return new ProjectRequest(project.getCode(), project.getName(), project.getDescription(),
                project.getDepartmentId(), project.getProjectManagerId(), project.getClientName(),
                project.getStartDate(), project.getEndDate(), project.getStatus(), project.isBillable());
    }

    private void validate(ProjectRequest request, Long companyId) {
        if (request.departmentId() != null
                && departmentRepository.findByIdAndCompanyId(request.departmentId(), companyId).isEmpty()) {
            throw new ResourceNotFoundException("Department", request.departmentId());
        }
        if (request.projectManagerId() != null && !employeeDirectory.exists(request.projectManagerId())) {
            throw new ResourceNotFoundException("Employee", request.projectManagerId());
        }
    }

    private Project require(Long id, Long companyId) {
        return projectRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    private List<ProjectResponse> toResponses(List<Project> projects, Long companyId) {
        Map<Long, String> departmentNames = projects.stream()
                .map(Project::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> departmentRepository.findByIdAndCompanyId(id, companyId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Set<Long> managerIds = projects.stream()
                .map(Project::getProjectManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> managerNames = managerIds.isEmpty() ? Map.of() : employeeDirectory.namesOf(managerIds);

        return projects.stream()
                .map(project -> new ProjectResponse(project.getId(), project.getCode(), project.getName(),
                        project.getDescription(), project.getDepartmentId(),
                        Lookups.get(departmentNames, project.getDepartmentId()), project.getProjectManagerId(),
                        Lookups.get(managerNames, project.getProjectManagerId()), project.getClientName(),
                        project.getStartDate(), project.getEndDate(), project.getStatus(),
                        project.isBillable(), project.isActive(),
                        taskRepository.countOpenByProject(project.getId())))
                .toList();
    }
}
