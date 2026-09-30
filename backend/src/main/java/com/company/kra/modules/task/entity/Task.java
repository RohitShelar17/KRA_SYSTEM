package com.company.kra.modules.task.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.modules.employee.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task extends CompanyScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false)
    private Employee assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private Employee assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_category_id", nullable = false)
    private TaskCategory taskCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_template_id")
    private TaskTemplate taskTemplate;

    /** Optional attribution to a result area; see DailyWorkLog for why it is an id. */
    @Column(name = "employee_kra_id")
    private Long employeeKraId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.NOT_STARTED;

    @Column(name = "completion_pct", nullable = false)
    private int completionPct;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    /** Kept in step with the sum of work-log entries attributed to this task. */
    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate = LocalDate.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 500)
    private String remarks;

    public boolean isOverdue() {
        return dueDate != null && !status.isClosed() && dueDate.isBefore(LocalDate.now());
    }

    /** True when the task closed on or before its due date. */
    public boolean isOnTime() {
        if (status != TaskStatus.COMPLETED || completedAt == null) {
            return false;
        }
        return dueDate == null || !completedAt.toLocalDate().isAfter(dueDate);
    }
}
