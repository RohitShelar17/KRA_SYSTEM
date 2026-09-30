package com.company.kra.modules.task.repository;

import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.modules.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"assignee", "project", "taskCategory", "assignedBy"})
    Optional<Task> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = {"assignee", "project", "taskCategory", "assignedBy"})
    @Query("""
            SELECT t FROM Task t
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND (:scopeAll = TRUE OR t.assignee.id IN :scopeIds)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:projectId IS NULL OR t.project.id = :projectId)
              AND (:categoryId IS NULL OR t.taskCategory.id = :categoryId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:openOnly = FALSE OR t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                                         com.company.kra.common.enums.TaskStatus.CANCELLED))
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Task> search(@Param("companyId") Long companyId,
                      @Param("scopeAll") boolean scopeAll,
                      @Param("scopeIds") Set<Long> scopeIds,
                      @Param("assigneeId") Long assigneeId,
                      @Param("projectId") Long projectId,
                      @Param("categoryId") Long categoryId,
                      @Param("status") TaskStatus status,
                      @Param("priority") Priority priority,
                      @Param("openOnly") boolean openOnly,
                      @Param("dueBefore") LocalDate dueBefore,
                      @Param("search") String search,
                      Pageable pageable);

    /** Open tasks offered when picking what a work-log entry was spent on. */
    @EntityGraph(attributePaths = {"project", "taskCategory"})
    @Query("""
            SELECT t FROM Task t
            WHERE t.assignee.id = :employeeId
              AND t.active = TRUE
              AND t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                   com.company.kra.common.enums.TaskStatus.CANCELLED)
            ORDER BY t.dueDate ASC, t.priority DESC
            """)
    List<Task> findOpenForEmployee(@Param("employeeId") Long employeeId);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.project.id = :projectId
              AND t.active = TRUE
              AND t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                   com.company.kra.common.enums.TaskStatus.CANCELLED)
            """)
    long countOpenByProject(@Param("projectId") Long projectId);

    long countByAssigneeIdAndActiveTrue(Long assigneeId);

    long countByAssigneeIdAndStatusAndActiveTrue(Long assigneeId, TaskStatus status);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignee.id = :employeeId
              AND t.active = TRUE
              AND t.dueDate < :today
              AND t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                   com.company.kra.common.enums.TaskStatus.CANCELLED)
            """)
    long countOverdue(@Param("employeeId") Long employeeId, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignee.id = :employeeId
              AND t.active = TRUE
              AND t.assignedDate BETWEEN :from AND :to
            """)
    long countAssignedBetween(@Param("employeeId") Long employeeId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignee.id = :employeeId
              AND t.active = TRUE
              AND t.status = com.company.kra.common.enums.TaskStatus.COMPLETED
              AND t.completedAt IS NOT NULL
              AND DATE(t.completedAt) BETWEEN :from AND :to
            """)
    long countCompletedBetween(@Param("employeeId") Long employeeId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** Completed on or before the due date; a task with no due date counts as on time. */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignee.id = :employeeId
              AND t.active = TRUE
              AND t.status = com.company.kra.common.enums.TaskStatus.COMPLETED
              AND t.completedAt IS NOT NULL
              AND DATE(t.completedAt) BETWEEN :from AND :to
              AND (t.dueDate IS NULL OR DATE(t.completedAt) <= t.dueDate)
            """)
    long countCompletedOnTimeBetween(@Param("employeeId") Long employeeId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** Recomputed from the timesheet whenever an entry attributed to the task changes. */
    @Query("""
            SELECT COALESCE(SUM(w.durationMinutes), 0)
            FROM DailyWorkLog w
            WHERE w.task.id = :taskId
            """)
    int sumLoggedMinutes(@Param("taskId") Long taskId);

    // ------------------------------------------------------------- reporting

    /** Tasks in the window, with the figures the completion report compares. */
    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name, t.title,
                   p.name, cat.name, t.assignedDate, t.dueDate, t.completedAt, t.status,
                   t.priority, t.estimatedMinutes, t.actualMinutes, t.completionPct
            FROM Task t JOIN t.assignee e LEFT JOIN e.department dep
                 LEFT JOIN t.project p LEFT JOIN t.taskCategory cat
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:status IS NULL OR t.status = :status)
              AND (:from IS NULL OR t.assignedDate >= :from)
              AND (:to IS NULL OR t.assignedDate <= :to)
            ORDER BY t.dueDate ASC, e.firstName ASC
            """)
    List<Object[]> reportTasks(@Param("companyId") Long companyId,
                               @Param("scopeAll") boolean scopeAll,
                               @Param("scopeIds") Set<Long> scopeIds,
                               @Param("employeeId") Long employeeId,
                               @Param("departmentId") Long departmentId,
                               @Param("status") TaskStatus status,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** Completed, open and overdue counts in one pass, for the dashboards. */
    @Query("""
            SELECT
              SUM(CASE WHEN t.status = com.company.kra.common.enums.TaskStatus.COMPLETED
                       THEN 1 ELSE 0 END),
              SUM(CASE WHEN t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                             com.company.kra.common.enums.TaskStatus.CANCELLED)
                       THEN 1 ELSE 0 END),
              SUM(CASE WHEN t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                             com.company.kra.common.enums.TaskStatus.CANCELLED)
                        AND t.dueDate IS NOT NULL AND t.dueDate < :today
                       THEN 1 ELSE 0 END)
            FROM Task t JOIN t.assignee e LEFT JOIN e.department dep
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:from IS NULL OR t.assignedDate >= :from)
              AND (:to IS NULL OR t.assignedDate <= :to)
            """)
    List<Object[]> reportCounts(@Param("companyId") Long companyId,
                                @Param("scopeAll") boolean scopeAll,
                                @Param("scopeIds") Set<Long> scopeIds,
                                @Param("departmentId") Long departmentId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("today") LocalDate today);

    /** Open tasks due tomorrow or already past their due date. */
    @Query("""
            SELECT t.id, t.title, e.id, mgr.id, t.dueDate, t.status
            FROM Task t JOIN t.assignee e LEFT JOIN e.reportingManager mgr
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND t.status NOT IN (com.company.kra.common.enums.TaskStatus.COMPLETED,
                                   com.company.kra.common.enums.TaskStatus.CANCELLED)
              AND t.dueDate IS NOT NULL
              AND t.dueDate <= :tomorrow
            ORDER BY t.dueDate ASC
            """)
    List<Object[]> findDueOrOverdue(@Param("companyId") Long companyId,
                                    @Param("tomorrow") LocalDate tomorrow);
}
