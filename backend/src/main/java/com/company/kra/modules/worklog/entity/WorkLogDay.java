package com.company.kra.modules.worklog.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.WorkLogDayStatus;
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

/**
 * One employee, one date. This is the unit that gets submitted, reviewed and
 * — from Phase 4 — scored.
 *
 * <p>Totals are cached here on every write rather than aggregated at read
 * time: dashboards, the approval queue and the scoring engine all read them,
 * and the entry table is the largest in the system.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "work_log_days")
public class WorkLogDay extends BaseEntity {

    /**
     * Company scoping without the soft-delete flag of CompanyScopedEntity:
     * removing a work-log row deletes it, so there is no inactive state.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkLogDayStatus status = WorkLogDayStatus.DRAFT;

    @Column(name = "office_minutes", nullable = false)
    private int officeMinutes;

    @Column(name = "logged_minutes", nullable = false)
    private int loggedMinutes;

    @Column(name = "productive_minutes", nullable = false)
    private int productiveMinutes;

    @Column(name = "meeting_minutes", nullable = false)
    private int meetingMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "unallocated_minutes", nullable = false)
    private int unallocatedMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comments", length = 500)
    private String reviewComments;

    public boolean isEditable() {
        return status.isEditable();
    }
}
