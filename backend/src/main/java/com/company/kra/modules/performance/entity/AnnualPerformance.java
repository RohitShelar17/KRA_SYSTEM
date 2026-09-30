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
@Table(name = "annual_performance")
public class AnnualPerformance extends PerformanceSnapshot {

    @Column(name = "perf_year", nullable = false)
    private short perfYear;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "quarters_included", nullable = false)
    private int quartersIncluded;

    @Column(name = "quarterly_average_score", precision = 5, scale = 2)
    private BigDecimal quarterlyAverageScore;

    @Column(name = "kra_score", precision = 5, scale = 2)
    private BigDecimal kraScore;

    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore;

    @Column(name = "manager_rating", precision = 5, scale = 2)
    private BigDecimal managerRating;

    @Column(name = "department_head_rating", precision = 5, scale = 2)
    private BigDecimal departmentHeadRating;

    @Column(name = "self_assessment_score", precision = 5, scale = 2)
    private BigDecimal selfAssessmentScore;
}
