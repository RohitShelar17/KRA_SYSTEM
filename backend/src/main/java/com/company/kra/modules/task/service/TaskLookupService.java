package com.company.kra.modules.task.service;

import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.task.entity.Project;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.entity.TaskCategory;
import com.company.kra.modules.task.repository.ProjectRepository;
import com.company.kra.modules.task.repository.TaskCategoryRepository;
import com.company.kra.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskLookupService implements TaskLookup {

    private final TaskCategoryRepository categoryRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @Override
    @Transactional(readOnly = true)
    public TaskCategory requireCategory(Long categoryId, Long companyId) {
        TaskCategory category = categoryRepository.findByIdAndCompanyId(categoryId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity category", categoryId));
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That activity category is no longer available");
        }
        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public Project requireProject(Long projectId, Long companyId) {
        return projectRepository.findByIdAndCompanyId(projectId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public Task requireAssignedTask(Long taskId, Long employeeId, Long companyId) {
        Task task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (!task.getAssignee().getId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You can only log time against tasks assigned to you");
        }
        return task;
    }

    @Override
    @Transactional
    public void refreshActualMinutes(Long taskId) {
        if (taskId == null) {
            return;
        }
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setActualMinutes(taskRepository.sumLoggedMinutes(taskId));
            taskRepository.save(task);
        });
    }
}
