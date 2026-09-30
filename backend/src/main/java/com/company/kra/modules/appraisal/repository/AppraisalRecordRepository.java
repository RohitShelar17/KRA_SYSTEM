package com.company.kra.modules.appraisal.repository;

import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.modules.appraisal.entity.AppraisalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AppraisalRecordRepository extends JpaRepository<AppraisalRecord, Long> {

    @EntityGraph(attributePaths = "cycle")
    Optional<AppraisalRecord> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = "cycle")
    Optional<AppraisalRecord> findByCycleIdAndEmployeeId(Long cycleId, Long employeeId);

    @EntityGraph(attributePaths = "cycle")
    List<AppraisalRecord> findByEmployeeIdOrderByIdDesc(Long employeeId);

    List<AppraisalRecord> findByCycleId(Long cycleId);

    long countByCycleId(Long cycleId);

    long countByCycleIdAndCurrentStep(Long cycleId, AppraisalStep currentStep);

    /** The reviewer's queue, restricted to the caller's access scope. */
    @EntityGraph(attributePaths = "cycle")
    @Query("""
            SELECT a FROM AppraisalRecord a
            WHERE a.companyId = :companyId
              AND (:scopeAll = TRUE OR a.employeeId IN :scopeIds)
              AND (:cycleId IS NULL OR a.cycle.id = :cycleId)
              AND (:step IS NULL OR a.currentStep = :step)
              AND a.employeeId <> :excludeEmployeeId
            """)
    Page<AppraisalRecord> search(@Param("companyId") Long companyId,
                                 @Param("scopeAll") boolean scopeAll,
                                 @Param("scopeIds") Set<Long> scopeIds,
                                 @Param("cycleId") Long cycleId,
                                 @Param("step") AppraisalStep step,
                                 @Param("excludeEmployeeId") Long excludeEmployeeId,
                                 Pageable pageable);

    // ------------------------------------------------------------- reporting

    /**
     * Where every appraisal in a cycle stands, with the ratings each reviewer
     * recorded. Reviews are joined per role so an employee still appears as
     * one row whether nobody or everybody has reviewed them.
     */
    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name,
                   CONCAT(m.firstName, ' ', m.lastName), c.name, a.currentStep, a.status,
                   sa.status, sa.selfRating,
                   (SELECT r.overallRating FROM ManagerReview r
                     WHERE r.appraisal.id = a.id
                       AND r.reviewerRole = com.company.kra.common.enums.ReviewerRole.MANAGER
                       AND r.status = com.company.kra.common.enums.SubmissionStatus.SUBMITTED),
                   (SELECT r.qualityRating FROM ManagerReview r
                     WHERE r.appraisal.id = a.id
                       AND r.reviewerRole = com.company.kra.common.enums.ReviewerRole.MANAGER
                       AND r.status = com.company.kra.common.enums.SubmissionStatus.SUBMITTED),
                   (SELECT r.overallRating FROM ManagerReview r
                     WHERE r.appraisal.id = a.id
                       AND r.reviewerRole = com.company.kra.common.enums.ReviewerRole.HR
                       AND r.status = com.company.kra.common.enums.SubmissionStatus.SUBMITTED),
                   a.computedScore, a.finalScore, a.completedAt
            FROM AppraisalRecord a
                 JOIN a.cycle c
                 JOIN Employee e ON e.id = a.employeeId
                 LEFT JOIN e.department dep
                 LEFT JOIN Employee m ON m.id = a.managerId
                 LEFT JOIN SelfAssessment sa ON sa.appraisal.id = a.id
            WHERE a.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND (:cycleId IS NULL OR c.id = :cycleId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:employeeId IS NULL OR e.id = :employeeId)
            ORDER BY c.periodYear DESC, c.periodIndex DESC, e.firstName ASC
            """)
    List<Object[]> reportAppraisals(@Param("companyId") Long companyId,
                                    @Param("scopeAll") boolean scopeAll,
                                    @Param("scopeIds") Set<Long> scopeIds,
                                    @Param("cycleId") Long cycleId,
                                    @Param("departmentId") Long departmentId,
                                    @Param("employeeId") Long employeeId);

    /** In-flight and completed counts within a scope, for the dashboards. */
    @Query("""
            SELECT
              SUM(CASE WHEN a.status <> com.company.kra.common.enums.AppraisalStatus.COMPLETED
                       THEN 1 ELSE 0 END),
              SUM(CASE WHEN a.status = com.company.kra.common.enums.AppraisalStatus.COMPLETED
                       THEN 1 ELSE 0 END)
            FROM AppraisalRecord a
                 JOIN Employee e ON e.id = a.employeeId
                 LEFT JOIN e.department dep
            WHERE a.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
            """)
    List<Object[]> reportCounts(@Param("companyId") Long companyId,
                                @Param("scopeAll") boolean scopeAll,
                                @Param("scopeIds") Set<Long> scopeIds,
                                @Param("departmentId") Long departmentId);

    /**
     * Appraisals still in flight, with when they last moved. The last move is
     * when the current step started waiting, which is what an SLA counts from.
     */
    @Query("""
            SELECT a.id, a.employeeId, CONCAT(e.firstName, ' ', e.lastName), a.managerId,
                   a.currentStep, COALESCE(a.updatedAt, a.createdAt), c.name,
                   c.periodYear, c.periodIndex, c.scopeType
            FROM AppraisalRecord a
                 JOIN a.cycle c
                 JOIN Employee e ON e.id = a.employeeId
            WHERE a.companyId = :companyId
              AND a.status <> com.company.kra.common.enums.AppraisalStatus.COMPLETED
              AND c.status <> com.company.kra.common.enums.CycleStatus.CLOSED
            """)
    List<Object[]> findInFlight(@Param("companyId") Long companyId);
}
