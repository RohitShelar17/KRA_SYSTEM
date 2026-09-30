package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.SubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The employee's own account of the period.
 *
 * <p>The self rating is recorded and shown alongside the score, but carries no
 * weight in the formula by default — it is context for the conversation, not
 * an input to the number.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "self_assessments")
public class SelfAssessment extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private AppraisalRecord appraisal;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Lob
    @Column(length = 4000)
    private String achievements;

    @Lob
    @Column(length = 4000)
    private String challenges;

    @Lob
    @Column(name = "skills_developed", length = 4000)
    private String skillsDeveloped;

    @Lob
    @Column(name = "improvement_areas", length = 4000)
    private String improvementAreas;

    @Lob
    @Column(name = "training_needed", length = 4000)
    private String trainingNeeded;

    @Lob
    @Column(name = "future_goals", length = 4000)
    private String futureGoals;

    @Lob
    @Column(length = 4000)
    private String comments;

    @Column(name = "self_rating", precision = 3, scale = 1)
    private BigDecimal selfRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }
}
