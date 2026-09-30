package com.company.kra.modules.performance.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.PerformanceScope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One version of the scoring formula for one grain.
 *
 * <p>Editing a formula creates a new version and deactivates the previous one.
 * Snapshots record the version they were computed under, so a change today
 * cannot rewrite a score from last quarter.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "performance_formula_config")
public class PerformanceFormulaConfig extends CompanyScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PerformanceScope scopeType;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String notes;

    /** Below this submission rate the period score takes the penalty. Zero disables it. */
    @Column(name = "min_submission_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minSubmissionRatePct = BigDecimal.ZERO;

    @Column(name = "submission_penalty_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal submissionPenaltyPct = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @BatchSize(size = 25)
    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<PerformanceFormulaParameter> parameters = new ArrayList<>();

    public BigDecimal totalWeightage() {
        return parameters.stream()
                .map(PerformanceFormulaParameter::getWeightage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
