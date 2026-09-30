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
@Table(name = "daily_performance")
public class DailyPerformance extends PerformanceSnapshot {

    @Column(name = "perf_date", nullable = false)
    private LocalDate perfDate;

    @Column(name = "tasks_assigned", nullable = false)
    private int tasksAssigned;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "logged_minutes", nullable = false)
    private int loggedMinutes;

    @Column(name = "productive_minutes", nullable = false)
    private int productiveMinutes;

    @Column(name = "expected_minutes", nullable = false)
    private int expectedMinutes;

    @Column(name = "task_completion_score", precision = 5, scale = 2)
    private BigDecimal taskCompletionScore;

    @Column(name = "kra_score", precision = 5, scale = 2)
    private BigDecimal kraScore;

    @Column(name = "productivity_score", precision = 5, scale = 2)
    private BigDecimal productivityScore;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "manager_rating", precision = 5, scale = 2)
    private BigDecimal managerRating;
}
