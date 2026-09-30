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
@Table(name = "quarterly_performance")
public class QuarterlyPerformance extends PerformanceSnapshot {

    @Column(name = "perf_year", nullable = false)
    private short perfYear;

    @Column(name = "perf_quarter", nullable = false)
    private byte perfQuarter;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "months_included", nullable = false)
    private int monthsIncluded;

    @Column(name = "monthly_average_score", precision = 5, scale = 2)
    private BigDecimal monthlyAverageScore;

    @Column(name = "kra_score", precision = 5, scale = 2)
    private BigDecimal kraScore;

    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore;

    @Column(name = "manager_rating", precision = 5, scale = 2)
    private BigDecimal managerRating;
}
