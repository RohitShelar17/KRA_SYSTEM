package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.AppraisalStatus;
import com.company.kra.common.enums.AppraisalStep;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One employee's appraisal for one cycle. This is the thing that moves through
 * the workflow; the self assessment and the reviews hang off it.
 */
@Getter
@Setter
@Entity
@Table(name = "appraisal_records")
public class AppraisalRecord extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private PerformanceCycle cycle;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Resolved when the cycle opens, so a later reassignment cannot orphan the review. */
    @Column(name = "manager_id")
    private Long managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    private AppraisalStep currentStep = AppraisalStep.SELF_ASSESSMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppraisalStatus status = AppraisalStatus.NOT_STARTED;

    /** What the engine computed, copied in when the appraisal closes. */
    @Column(name = "computed_score", precision = 5, scale = 2)
    private BigDecimal computedScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "final_rating_band_id")
    private Long finalRatingBandId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean isComplete() {
        return currentStep == AppraisalStep.COMPLETED;
    }
}
