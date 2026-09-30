package com.company.kra.modules.performance.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.PerformanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What every score snapshot carries, whatever its grain.
 *
 * <p>{@code formulaVersion} and {@code scoreBreakdown} are the reason a score
 * can be explained months later: the breakdown holds each parameter's raw
 * value, weight and contribution exactly as it was at computation time.</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class PerformanceSnapshot extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "rating_band_id")
    private Long ratingBandId;

    @Column(name = "formula_version", nullable = false)
    private int formulaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "json")
    private String scoreBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PerformanceStatus status = PerformanceStatus.COMPUTED;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt = LocalDateTime.now();

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by")
    private Long finalizedBy;

    public boolean isFrozen() {
        return status.isFrozen();
    }
}
