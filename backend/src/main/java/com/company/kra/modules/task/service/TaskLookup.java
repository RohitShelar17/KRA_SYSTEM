package com.company.kra.modules.task.service;

import com.company.kra.modules.task.entity.Project;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.entity.TaskCategory;

/**
 * The task module's contract for other modules. The work-log module needs to
 * resolve a category, a project and a task when an entry is written, and needs
 * the task's logged minutes refreshed afterwards; it gets exactly that and no
 * access to this module's repositories.
 */
public interface TaskLookup {

    TaskCategory requireCategory(Long categoryId, Long companyId);

    Project requireProject(Long projectId, Long companyId);

    /** Resolves a task and refuses one that belongs to somebody else. */
    Task requireAssignedTask(Long taskId, Long employeeId, Long companyId);

    /** Recomputes a task's actual minutes from the timesheet. */
    void refreshActualMinutes(Long taskId);
}
