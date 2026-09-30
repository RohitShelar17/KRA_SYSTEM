package com.company.kra.modules.worklog.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.task.entity.Project;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.entity.TaskCategory;
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
import java.time.LocalTime;

/**
 * A single time slot in a day.
 *
 * <p>{@code status} describes the progress of this piece of work, which is a
 * different question from whether the day has been filed — that lives on
 * {@link WorkLogDay}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "daily_work_logs")
public class DailyWorkLog extends BaseEntity {

    /**
     * Company scoping without the soft-delete flag of CompanyScopedEntity:
     * removing a work-log row deletes it, so there is no inactive state.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_log_day_id", nullable = false)
    private WorkLogDay workLogDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Always computed server-side; a client-supplied duration is ignored. */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_category_id", nullable = false)
    private TaskCategory taskCategory;

    /**
     * Optional attribution to a result area. Held as a plain id rather than an
     * association: the KRA module owns that entity, and this module only needs
     * to record which one the time belongs to.
     */
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
    private TaskStatus status = TaskStatus.IN_PROGRESS;

    @Column(name = "completion_pct", nullable = false)
    private int completionPct;

    /** Copied from the category at write time so later category edits cannot rewrite history. */
    @Column(name = "is_productive", nullable = false)
    private boolean productive = true;

    @Column(name = "is_billable", nullable = false)
    private boolean billable;

    @Column(length = 500)
    private String remarks;

    /** Half-open overlap test: touching slots (11:00-13:00, 13:00-14:00) do not collide. */
    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        return startTime.isBefore(otherEnd) && otherStart.isBefore(endTime);
    }
}
