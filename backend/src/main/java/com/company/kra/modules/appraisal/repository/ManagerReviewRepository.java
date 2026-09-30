package com.company.kra.modules.appraisal.repository;

import com.company.kra.common.enums.ReviewerRole;
import com.company.kra.modules.appraisal.entity.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ManagerReviewRepository extends JpaRepository<ManagerReview, Long> {

    Optional<ManagerReview> findByAppraisalIdAndReviewerRole(Long appraisalId, ReviewerRole reviewerRole);

    List<ManagerReview> findByAppraisalIdOrderByIdAsc(Long appraisalId);

    Optional<ManagerReview> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Submitted reviews covering a date range, so the scoring engine can find
     * the rating that applies to a period.
     */
    @Query("""
            SELECT r FROM ManagerReview r
            WHERE r.employeeId = :employeeId
              AND r.reviewerRole = :role
              AND r.status = com.company.kra.common.enums.SubmissionStatus.SUBMITTED
              AND r.appraisal.cycle.periodStart <= :to
              AND r.appraisal.cycle.periodEnd >= :from
            ORDER BY r.appraisal.cycle.periodEnd DESC
            """)
    List<ManagerReview> findSubmittedCovering(@Param("employeeId") Long employeeId,
                                              @Param("role") ReviewerRole role,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
