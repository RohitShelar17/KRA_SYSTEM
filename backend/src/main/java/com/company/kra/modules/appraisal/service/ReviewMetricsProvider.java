package com.company.kra.modules.appraisal.service;

import com.company.kra.common.enums.ReviewerRole;
import com.company.kra.common.metrics.ReviewMetrics;
import com.company.kra.modules.appraisal.entity.ManagerReview;
import com.company.kra.modules.appraisal.entity.SelfAssessment;
import com.company.kra.modules.appraisal.repository.ManagerReviewRepository;
import com.company.kra.modules.appraisal.repository.SelfAssessmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the scoring engine with the ratings a reviewer recorded.
 *
 * <p>Only submitted reviews count. A draft is a reviewer thinking out loud;
 * scoring against it would let a half-filled form move somebody's number. When
 * nothing has been submitted these return null and the engine redistributes
 * that weight, which is why a score exists before the appraisal does.</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewMetricsProvider implements ReviewMetrics {

    private static final BigDecimal RATING_TO_PERCENT = new BigDecimal("20");

    private final ManagerReviewRepository reviewRepository;
    private final SelfAssessmentRepository selfAssessmentRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal qualityRatingPercent(Long employeeId, LocalDate from, LocalDate to) {
        return latest(employeeId, ReviewerRole.MANAGER, from, to)
                .map(ManagerReview::qualityPercent)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal managerRatingPercent(Long employeeId, LocalDate from, LocalDate to) {
        return latest(employeeId, ReviewerRole.MANAGER, from, to)
                .map(ManagerReview::overallPercent)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal departmentHeadRatingPercent(Long employeeId, LocalDate from, LocalDate to) {
        return latest(employeeId, ReviewerRole.DEPARTMENT_HEAD, from, to)
                .map(ManagerReview::overallPercent)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal selfRatingPercent(Long employeeId, LocalDate from, LocalDate to) {
        return latest(employeeId, ReviewerRole.MANAGER, from, to)
                .map(ManagerReview::getAppraisal)
                .flatMap(appraisal -> selfAssessmentRepository.findByAppraisalId(appraisal.getId()))
                .filter(SelfAssessment::isSubmitted)
                .map(SelfAssessment::getSelfRating)
                .map(rating -> rating.multiply(RATING_TO_PERCENT).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    /**
     * The most recent submitted review whose cycle overlaps the window. A
     * quarterly review therefore also informs the monthly scores inside it,
     * which is the behaviour a manager expects.
     */
    private Optional<ManagerReview> latest(Long employeeId, ReviewerRole role, LocalDate from,
                                           LocalDate to) {
        List<ManagerReview> reviews = reviewRepository.findSubmittedCovering(employeeId, role, from, to);
        return reviews.isEmpty() ? Optional.empty() : Optional.of(reviews.get(0));
    }
}
