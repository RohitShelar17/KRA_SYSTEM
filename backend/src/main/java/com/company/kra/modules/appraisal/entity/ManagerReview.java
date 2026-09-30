package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.ReviewerRole;
import com.company.kra.common.enums.SubmissionStatus;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A reviewer's evaluation, one row per reviewer role.
 *
 * <p>Competencies are rated 1 to 5. The scoring engine reads two figures from
 * here — the quality rating and the overall rating — converted to the 0-100
 * scale the formula works in. Everything else is the written record that goes
 * with the number.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "manager_reviews")
public class ManagerReview extends BaseEntity {

    private static final BigDecimal RATING_TO_PERCENT = new BigDecimal("20");

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private AppraisalRecord appraisal;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "reviewer_employee_id", nullable = false)
    private Long reviewerEmployeeId;

    @Column(name = "reviewer_name", length = 150)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false, length = 30)
    private ReviewerRole reviewerRole = ReviewerRole.MANAGER;

    @Column(name = "quality_rating", precision = 3, scale = 1)
    private BigDecimal qualityRating;

    @Column(name = "productivity_rating", precision = 3, scale = 1)
    private BigDecimal productivityRating;

    @Column(name = "timeliness_rating", precision = 3, scale = 1)
    private BigDecimal timelinessRating;

    @Column(name = "teamwork_rating", precision = 3, scale = 1)
    private BigDecimal teamworkRating;

    @Column(name = "communication_rating", precision = 3, scale = 1)
    private BigDecimal communicationRating;

    @Column(name = "initiative_rating", precision = 3, scale = 1)
    private BigDecimal initiativeRating;

    @Column(name = "problem_solving_rating", precision = 3, scale = 1)
    private BigDecimal problemSolvingRating;

    @Column(name = "discipline_rating", precision = 3, scale = 1)
    private BigDecimal disciplineRating;

    @Column(name = "kra_rating", precision = 3, scale = 1)
    private BigDecimal kraRating;

    @Column(name = "kpi_rating", precision = 3, scale = 1)
    private BigDecimal kpiRating;

    @Column(name = "overall_rating", precision = 3, scale = 1)
    private BigDecimal overallRating;

    @Column(length = 4000)
    private String strengths;

    @Column(length = 4000)
    private String improvements;

    @Column(length = 4000)
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }

    /** The 1-5 quality rating on the 0-100 scale the formula uses. */
    public BigDecimal qualityPercent() {
        return toPercent(qualityRating);
    }

    /**
     * The overall rating on the 0-100 scale. When the reviewer left it blank,
     * the mean of the competencies they did rate stands in — a reviewer who
     * filled the form in detail should not count as having said nothing.
     */
    public BigDecimal overallPercent() {
        if (overallRating != null) {
            return toPercent(overallRating);
        }
        List<BigDecimal> rated = new ArrayList<>();
        addIfPresent(rated, qualityRating);
        addIfPresent(rated, productivityRating);
        addIfPresent(rated, timelinessRating);
        addIfPresent(rated, teamworkRating);
        addIfPresent(rated, communicationRating);
        addIfPresent(rated, initiativeRating);
        addIfPresent(rated, problemSolvingRating);
        addIfPresent(rated, disciplineRating);

        if (rated.isEmpty()) {
            return null;
        }
        BigDecimal sum = rated.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(rated.size()), 2, RoundingMode.HALF_UP);
        return toPercent(mean);
    }

    private void addIfPresent(List<BigDecimal> target, BigDecimal rating) {
        if (rating != null) {
            target.add(rating);
        }
    }

    private BigDecimal toPercent(BigDecimal rating) {
        return rating == null ? null : rating.multiply(RATING_TO_PERCENT).setScale(2, RoundingMode.HALF_UP);
    }
}
