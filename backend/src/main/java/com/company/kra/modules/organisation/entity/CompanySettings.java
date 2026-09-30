package com.company.kra.modules.organisation.entity;

import com.company.kra.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Working-hour and policy configuration. Every rule the work-log validator
 * applies in Phase 2 is read from here rather than hard-coded.
 */
@Getter
@Setter
@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime = LocalTime.of(9, 0);

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime = LocalTime.of(18, 0);

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 60;

    @Column(name = "working_days", nullable = false, length = 30)
    private String workingDays = "MON,TUE,WED,THU,FRI";

    @Column(name = "worklog_approval_enabled", nullable = false)
    private boolean worklogApprovalEnabled = true;

    @Column(name = "overtime_allowed", nullable = false)
    private boolean overtimeAllowed = true;

    @Column(name = "max_overtime_minutes", nullable = false)
    private int maxOvertimeMinutes = 180;

    @Column(name = "backdate_window_days", nullable = false)
    private int backdateWindowDays = 3;

    @Column(name = "worklog_cutoff_time", nullable = false)
    private LocalTime worklogCutoffTime = LocalTime.of(20, 0);

    @Column(name = "min_submission_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minSubmissionRatePct = new BigDecimal("80.00");

    /** Contracted minutes in a standard day, break excluded. */
    public int netWorkingMinutes() {
        int gross = (workEndTime.toSecondOfDay() - workStartTime.toSecondOfDay()) / 60;
        return Math.max(gross - breakMinutes, 0);
    }
}
