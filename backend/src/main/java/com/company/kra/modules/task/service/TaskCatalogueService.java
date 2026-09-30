package com.company.kra.modules.task.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.Recurrence;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.task.dto.TaskCategoryRequest;
import com.company.kra.modules.task.dto.TaskCategoryResponse;
import com.company.kra.modules.task.dto.TaskTemplateRequest;
import com.company.kra.modules.task.dto.TaskTemplateResponse;
import com.company.kra.modules.task.entity.TaskCategory;
import com.company.kra.modules.task.entity.TaskTemplate;
import com.company.kra.modules.task.repository.TaskCategoryRepository;
import com.company.kra.modules.task.repository.TaskTemplateRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import com.company.kra.util.Lookups;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The activity vocabulary: categories and the department task catalogue.
 *
 * <p>These are the rows that let a Sales day and a QA day be described in their
 * own terms without a code change, which is the whole point of Section 8 of the
 * blueprint.</p>
 */
@Service
@RequiredArgsConstructor
public class TaskCatalogueService {

    private final TaskCategoryRepository categoryRepository;
    private final TaskTemplateRepository templateRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public List<TaskCategoryResponse> categories() {
        Long companyId = SecurityUtils.requireCompanyId();
        return toCategoryResponses(
                categoryRepository.findByCompanyIdAndActiveTrueOrderByDisplayOrderAscNameAsc(companyId),
                companyId);
    }

    /** Company-wide categories plus the caller's own department additions. */
    @Transactional(readOnly = true)
    public List<TaskCategoryResponse> categoriesForCaller() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        return toCategoryResponses(
                categoryRepository.findAvailableFor(principal.getCompanyId(), principal.getDepartmentId()),
                principal.getCompanyId());
    }

    @Transactional
    public TaskCategoryResponse createCategory(TaskCategoryRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (categoryRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Activity category", "code", request.code());
        }
        validateDepartment(request.departmentId(), companyId);

        TaskCategory category = new TaskCategory();
        category.setCompanyId(companyId);
        applyCategory(category, request);
        TaskCategory saved = categoryRepository.save(category);

        auditService.record(AuditModule.TASK, AuditAction.CREATE, "TaskCategory", saved.getId(),
                null, request, null);
        return toCategoryResponses(List.of(saved), companyId).get(0);
    }

    @Transactional
    public TaskCategoryResponse updateCategory(Long id, TaskCategoryRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        TaskCategory category = requireCategory(id, companyId);

        if (!category.getCode().equalsIgnoreCase(request.code())
                && categoryRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Activity category", "code", request.code());
        }
        validateDepartment(request.departmentId(), companyId);

        TaskCategoryRequest before = new TaskCategoryRequest(category.getCode(), category.getName(),
                category.getDescription(), category.getCategoryType(), category.isProductive(),
                category.getDepartmentId(), category.getColourHex(), category.getDisplayOrder());

        applyCategory(category, request);
        categoryRepository.save(category);

        // Productivity classification feeds the score, so the change is audited
        // even though it looks like a cosmetic edit.
        auditService.record(AuditModule.TASK, AuditAction.UPDATE, "TaskCategory", id, before, request, null);
        return toCategoryResponses(List.of(category), companyId).get(0);
    }

    @Transactional
    public void deactivateCategory(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        TaskCategory category = requireCategory(id, companyId);
        category.setActive(false);
        categoryRepository.save(category);
        auditService.record(AuditModule.TASK, AuditAction.DELETE, "TaskCategory", id, null, null,
                "Deactivated");
    }

    // ------------------------------------------------------------- templates

    @Transactional(readOnly = true)
    public List<TaskTemplateResponse> templates(Long departmentId) {
        Long companyId = SecurityUtils.requireCompanyId();
        return toTemplateResponses(templateRepository.findAvailableFor(companyId, departmentId), companyId);
    }

    /** What the caller's own department offers, used by the quick-add pickers. */
    @Transactional(readOnly = true)
    public List<TaskTemplateResponse> templatesForCaller() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        return toTemplateResponses(
                templateRepository.findAvailableFor(principal.getCompanyId(), principal.getDepartmentId()),
                principal.getCompanyId());
    }

    @Transactional
    public TaskTemplateResponse createTemplate(TaskTemplateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateDepartment(request.departmentId(), companyId);

        TaskTemplate template = new TaskTemplate();
        template.setCompanyId(companyId);
        applyTemplate(template, request, companyId);
        TaskTemplate saved = templateRepository.save(template);

        auditService.record(AuditModule.TASK, AuditAction.CREATE, "TaskTemplate", saved.getId(),
                null, request, null);
        return toTemplateResponses(List.of(saved), companyId).get(0);
    }

    @Transactional
    public TaskTemplateResponse updateTemplate(Long id, TaskTemplateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        TaskTemplate template = requireTemplate(id, companyId);
        validateDepartment(request.departmentId(), companyId);

        TaskTemplateRequest before = new TaskTemplateRequest(template.getDepartmentId(),
                template.getTaskCategory().getId(), template.getName(), template.getDescription(),
                template.getDefaultEstimatedMinutes(), template.getDefaultPriority(),
                template.getRecurrence(), template.getDisplayOrder());

        applyTemplate(template, request, companyId);
        templateRepository.save(template);

        auditService.record(AuditModule.TASK, AuditAction.UPDATE, "TaskTemplate", id, before, request, null);
        return toTemplateResponses(List.of(template), companyId).get(0);
    }

    @Transactional
    public void deactivateTemplate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        TaskTemplate template = requireTemplate(id, companyId);
        template.setActive(false);
        templateRepository.save(template);
        auditService.record(AuditModule.TASK, AuditAction.DELETE, "TaskTemplate", id, null, null,
                "Deactivated");
    }

    // ------------------------------------------------------- shared helpers

    /** Used by the work-log module, which must not touch this module's repositories. */
    @Transactional(readOnly = true)
    public TaskCategory requireCategory(Long id, Long companyId) {
        return categoryRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity category", id));
    }

    private TaskTemplate requireTemplate(Long id, Long companyId) {
        return templateRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Task template", id));
    }

    private void applyCategory(TaskCategory category, TaskCategoryRequest request) {
        category.setCode(request.code().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setCategoryType(request.categoryType());
        category.setProductive(request.productive());
        category.setDepartmentId(request.departmentId());
        category.setColourHex(emptyToNull(request.colourHex()));
        category.setDisplayOrder(request.displayOrder());
    }

    private void applyTemplate(TaskTemplate template, TaskTemplateRequest request, Long companyId) {
        template.setDepartmentId(request.departmentId());
        template.setTaskCategory(requireCategory(request.taskCategoryId(), companyId));
        template.setName(request.name().trim());
        template.setDescription(request.description());
        template.setDefaultEstimatedMinutes(request.defaultEstimatedMinutes());
        template.setDefaultPriority(request.defaultPriority() == null
                ? Priority.MEDIUM : request.defaultPriority());
        template.setRecurrence(request.recurrence() == null ? Recurrence.NONE : request.recurrence());
        template.setDisplayOrder(request.displayOrder());
    }

    private void validateDepartment(Long departmentId, Long companyId) {
        if (departmentId != null
                && departmentRepository.findByIdAndCompanyId(departmentId, companyId).isEmpty()) {
            throw new ResourceNotFoundException("Department", departmentId);
        }
    }

    private Map<Long, String> departmentNames(List<Long> departmentIds, Long companyId) {
        return departmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> departmentRepository.findByIdAndCompanyId(id, companyId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private List<TaskCategoryResponse> toCategoryResponses(List<TaskCategory> categories, Long companyId) {
        Map<Long, String> names = departmentNames(
                categories.stream().map(TaskCategory::getDepartmentId).toList(), companyId);
        return categories.stream()
                .map(category -> new TaskCategoryResponse(category.getId(), category.getCode(),
                        category.getName(), category.getDescription(), category.getCategoryType(),
                        category.isProductive(), category.getDepartmentId(),
                        Lookups.get(names, category.getDepartmentId()), category.getColourHex(),
                        category.getDisplayOrder(), category.isActive()))
                .toList();
    }

    private List<TaskTemplateResponse> toTemplateResponses(List<TaskTemplate> templates, Long companyId) {
        Map<Long, String> names = departmentNames(
                templates.stream().map(TaskTemplate::getDepartmentId).toList(), companyId);
        return templates.stream()
                .map(template -> new TaskTemplateResponse(template.getId(), template.getDepartmentId(),
                        Lookups.get(names, template.getDepartmentId()), template.getTaskCategory().getId(),
                        template.getTaskCategory().getName(), template.getTaskCategory().getColourHex(),
                        template.getName(), template.getDescription(),
                        template.getDefaultEstimatedMinutes(), template.getDefaultPriority(),
                        template.getRecurrence(), template.getDisplayOrder(), template.isActive()))
                .toList();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
