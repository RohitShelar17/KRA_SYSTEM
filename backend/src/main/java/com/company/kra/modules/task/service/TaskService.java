package com.company.kra.modules.task.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.task.dto.TaskBoardCountsResponse;
import com.company.kra.modules.task.dto.TaskRequest;
import com.company.kra.modules.task.dto.TaskResponse;
import com.company.kra.modules.task.dto.TaskStatusRequest;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.entity.TaskTemplate;
import com.company.kra.modules.task.repository.TaskRepository;
import com.company.kra.modules.task.repository.TaskTemplateRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tasks, whether an employee created their own or a manager assigned one.
 *
 * <p>Assignment is scope-checked: a manager may only assign to someone inside
 * their reporting subtree, and may only read tasks they are allowed to see.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskTemplateRepository templateRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskLookup taskLookup;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(Long assigneeId, Long projectId, Long categoryId,
                                             TaskStatus status, Priority priority, boolean openOnly,
                                             LocalDate dueBefore, String search, Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        if (!scope.organisationWide() && scope.employeeIds().isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                    0, 0, true, true);
        }
        if (assigneeId != null && !scope.covers(assigneeId)) {
            throw new BusinessException(ErrorCode.OUT_OF_SCOPE,
                    "That employee is outside your access scope");
        }

        Page<Task> page = taskRepository.search(principal.getCompanyId(), scope.organisationWide(),
                scope.organisationWide() ? Set.of(-1L) : scope.employeeIds(), assigneeId, projectId,
                categoryId, status, priority, openOnly, dueBefore, blankToNull(search), pageable);
        return PageResponse.from(page, task -> toResponse(task, principal));
    }

    /** The caller's own task list. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> mine(TaskStatus status, boolean openOnly, Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        requireEmployee(principal);
        return search(principal.getEmployeeId(), null, null, status, null, openOnly, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public TaskBoardCountsResponse counts(Long employeeId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long target = employeeId == null ? requireEmployee(principal) : employeeId;
        accessScopeResolver.assertCanAccess(target);

        return new TaskBoardCountsResponse(
                taskRepository.countByAssigneeIdAndActiveTrue(target),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(target, TaskStatus.NOT_STARTED),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(target, TaskStatus.IN_PROGRESS),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(target, TaskStatus.COMPLETED),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(target, TaskStatus.ON_HOLD),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(target, TaskStatus.BLOCKED),
                taskRepository.countOverdue(target, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Task task = require(id, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(task.getAssignee().getId());
        return toResponse(task, principal);
    }

    /** Open tasks the caller can attribute time to when filling the timesheet. */
    @Transactional(readOnly = true)
    public List<TaskResponse> openForCaller() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployee(principal);
        return taskRepository.findOpenForEmployee(employeeId).stream()
                .map(task -> toResponse(task, principal))
                .toList();
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long companyId = principal.getCompanyId();

        Long assigneeId = request.assigneeId() == null ? requireEmployee(principal) : request.assigneeId();
        boolean assigningToSomeoneElse = !assigneeId.equals(principal.getEmployeeId());

        if (assigningToSomeoneElse) {
            if (!principal.hasPermission("TASK_ASSIGN")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        "You may only create tasks for yourself");
            }
            accessScopeResolver.assertCanAccess(assigneeId);
        }

        Employee assignee = employeeRepository.findByIdAndCompanyId(assigneeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", assigneeId));

        Task task = new Task();
        task.setCompanyId(companyId);
        task.setAssignee(assignee);
        if (principal.getEmployeeId() != null) {
            employeeRepository.findByIdAndCompanyId(principal.getEmployeeId(), companyId)
                    .ifPresent(task::setAssignedBy);
        }
        applyTemplateDefaults(task, request, companyId);
        apply(task, request, companyId);

        Task saved = taskRepository.save(task);
        auditService.record(AuditModule.TASK, assigningToSomeoneElse ? AuditAction.ASSIGN : AuditAction.CREATE,
                "Task", saved.getId(), null,
                Map.of("title", saved.getTitle(), "assigneeId", String.valueOf(assigneeId)), null);

        log.info("Task '{}' created for employee {}", saved.getTitle(), assigneeId);

        // Nobody needs telling about a task they wrote for themselves.
        if (assigningToSomeoneElse) {
            events.publishEvent(taskAssigned(saved, principal));
        }
        return toResponse(saved, principal);
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Task task = require(id, principal.getCompanyId());
        assertCanModify(task, principal);

        Map<String, Object> before = Map.of("title", task.getTitle(), "status", task.getStatus().name(),
                "dueDate", String.valueOf(task.getDueDate()));

        Long reassignedTo = null;

        // Reassignment is a deliberate, separately-authorised act.
        if (request.assigneeId() != null && !request.assigneeId().equals(task.getAssignee().getId())) {
            if (!principal.hasPermission("TASK_ASSIGN")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "You may not reassign this task");
            }
            accessScopeResolver.assertCanAccess(request.assigneeId());
            task.setAssignee(employeeRepository.findByIdAndCompanyId(request.assigneeId(),
                            principal.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", request.assigneeId())));
            reassignedTo = request.assigneeId();
        }

        apply(task, request, principal.getCompanyId());
        taskRepository.save(task);

        auditService.record(AuditModule.TASK, AuditAction.UPDATE, "Task", id, before,
                Map.of("title", task.getTitle(), "status", task.getStatus().name(),
                        "dueDate", String.valueOf(task.getDueDate())), null);

        if (reassignedTo != null && !reassignedTo.equals(principal.getEmployeeId())) {
            events.publishEvent(taskAssigned(task, principal));
        }
        return toResponse(task, principal);
    }

    private NotificationRaised taskAssigned(Task task, AppUserPrincipal principal) {
        return NotificationRaised.to(task.getCompanyId(), "TASK_ASSIGNED")
                .recipient(task.getAssignee().getId())
                .about("Task", task.getId())
                .with("taskTitle", task.getTitle())
                .with("assignerName", principal.getDisplayName())
                .with("dueDate", task.getDueDate() == null ? "with no due date" : task.getDueDate())
                .with("estimatedHours", task.getEstimatedMinutes() == null
                        ? "no" : String.format("%.1f", task.getEstimatedMinutes() / 60.0))
                .build();
    }

    @Transactional
    public TaskResponse changeStatus(Long id, TaskStatusRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Task task = require(id, principal.getCompanyId());
        assertCanModify(task, principal);

        TaskStatus previous = task.getStatus();
        task.setStatus(request.status());

        if (request.completionPct() != null) {
            task.setCompletionPct(clampPercent(request.completionPct()));
        }
        // Completion and percentage stay consistent without the user policing it.
        if (request.status() == TaskStatus.COMPLETED) {
            task.setCompletionPct(100);
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
            }
        } else {
            task.setCompletedAt(null);
            if (request.status() == TaskStatus.NOT_STARTED && request.completionPct() == null) {
                task.setCompletionPct(0);
            }
        }
        if (request.remarks() != null) {
            task.setRemarks(request.remarks());
        }

        taskRepository.save(task);
        auditService.record(AuditModule.TASK, AuditAction.UPDATE, "Task", id,
                Map.of("status", previous.name()), Map.of("status", task.getStatus().name()),
                request.remarks());
        return toResponse(task, principal);
    }

    @Transactional
    public void delete(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Task task = require(id, principal.getCompanyId());
        assertCanModify(task, principal);

        task.setActive(false);
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
        auditService.record(AuditModule.TASK, AuditAction.DELETE, "Task", id, null, null, "Cancelled");
    }

    // ------------------------------------------------------------ internals

    private void assertCanModify(Task task, AppUserPrincipal principal) {
        boolean isOwn = task.getAssignee().getId().equals(principal.getEmployeeId());
        if (isOwn) {
            return;
        }
        if (!principal.hasPermission("TASK_ASSIGN")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You may only change your own tasks");
        }
        accessScopeResolver.assertCanAccess(task.getAssignee().getId());
    }

    private void applyTemplateDefaults(Task task, TaskRequest request, Long companyId) {
        if (request.taskTemplateId() == null) {
            return;
        }
        TaskTemplate template = templateRepository.findByIdAndCompanyId(request.taskTemplateId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Task template", request.taskTemplateId()));
        task.setTaskTemplate(template);
        task.setEstimatedMinutes(template.getDefaultEstimatedMinutes());
        task.setPriority(template.getDefaultPriority());
    }

    private void apply(Task task, TaskRequest request, Long companyId) {
        task.setTaskCategory(taskLookup.requireCategory(request.taskCategoryId(), companyId));
        task.setProject(request.projectId() == null ? null
                : taskLookup.requireProject(request.projectId(), companyId));
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.estimatedMinutes() != null) {
            task.setEstimatedMinutes(request.estimatedMinutes());
        }
        task.setAssignedDate(request.assignedDate() == null ? LocalDate.now() : request.assignedDate());
        task.setDueDate(request.dueDate());
        task.setRemarks(request.remarks());

        if (task.getDueDate() != null && task.getDueDate().isBefore(task.getAssignedDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Due date cannot be before the assigned date");
        }
    }

    private Task require(Long id, Long companyId) {
        return taskRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Long requireEmployee(AppUserPrincipal principal) {
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private TaskResponse toResponse(Task task, AppUserPrincipal principal) {
        return new TaskResponse(
                task.getId(),
                task.getAssignee().getId(),
                task.getAssignee().fullName(),
                task.getAssignee().getEmployeeCode(),
                task.getAssignedBy() == null ? null : task.getAssignedBy().getId(),
                task.getAssignedBy() == null ? null : task.getAssignedBy().fullName(),
                task.getProject() == null ? null : task.getProject().getId(),
                task.getProject() == null ? null : task.getProject().getName(),
                task.getTaskCategory().getId(),
                task.getTaskCategory().getName(),
                task.getTaskCategory().getColourHex(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getCompletionPct(),
                task.getEstimatedMinutes(),
                task.getActualMinutes(),
                task.getAssignedDate(),
                task.getDueDate(),
                task.getCompletedAt(),
                task.getRemarks(),
                task.isOverdue(),
                task.getAssignee().getId().equals(principal.getEmployeeId()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
