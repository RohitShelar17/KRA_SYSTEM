package com.company.kra.modules.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "monthly_performance")
public class MonthlyPerformance extends PerformanceSnapshot {

    @Column(name = "perf_year", nullable = false)
    private short perfYear;

    @Column(name = "perf_month", nullable = false)
    private byte perfMonth;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "tasks_assigned", nullable = false)
    private int tasksAssigned;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "tasks_pending", nullable = false)
    private int tasksPending;

    @Column(name = "logged_minutes", nullable = false)
    private int loggedMinutes;

    @Column(name = "productive_minutes", nullable = false)
    private int productiveMinutes;

    @Column(name = "expected_minutes", nullable = false)
    private int expectedMinutes;

    @Column(name = "working_days", nullable = false)
    private int workingDays;

    @Column(name = "submitted_days", nullable = false)
    private int submittedDays;

    @Column(name = "submission_rate_pct", precision = 5, scale = 2)
    private BigDecimal submissionRatePct;

    @Column(name = "daily_average_score", precision = 5, scale = 2)
    private BigDecimal dailyAverageScore;

    @Column(name = "task_completion_score", precision = 5, scale = 2)
    private BigDecimal taskCompletionScore;

    @Column(name = "kra_score", precision = 5, scale = 2)
    private BigDecimal kraScore;

    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore;

    @Column(name = "productivity_score", precision = 5, scale = 2)
    private BigDecimal productivityScore;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "manager_rating", precision = 5, scale = 2)
    private BigDecimal managerRating;
}
