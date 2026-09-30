package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.MonthlyPerformance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MonthlyPerformanceRepository extends JpaRepository<MonthlyPerformance, Long> {

    Optional<MonthlyPerformance> findByEmployeeIdAndPerfYearAndPerfMonth(
            Long employeeId, short perfYear, byte perfMonth);

    Optional<MonthlyPerformance> findByIdAndCompanyId(Long id, Long companyId);

    List<MonthlyPerformance> findByEmployeeIdAndPerfYearOrderByPerfMonthAsc(Long employeeId, short perfYear);

    @Query("""
            SELECT m FROM MonthlyPerformance m
            WHERE m.employeeId = :employeeId
              AND m.perfYear = :year
              AND m.perfMonth BETWEEN :fromMonth AND :toMonth
            ORDER BY m.perfMonth ASC
            """)
    List<MonthlyPerformance> findInMonthRange(@Param("employeeId") Long employeeId,
                                              @Param("year") short year,
                                              @Param("fromMonth") byte fromMonth,
                                              @Param("toMonth") byte toMonth);

    /** The HR finalisation queue, restricted to the caller's access scope. */
    @Query("""
            SELECT m FROM MonthlyPerformance m
            WHERE m.companyId = :companyId
              AND (:scopeAll = TRUE OR m.employeeId IN :scopeIds)
              AND (:year IS NULL OR m.perfYear = :year)
              AND (:month IS NULL OR m.perfMonth = :month)
              AND (:status IS NULL OR m.status = :status)
            """)
    Page<MonthlyPerformance> search(@Param("companyId") Long companyId,
                                    @Param("scopeAll") boolean scopeAll,
                                    @Param("scopeIds") Set<Long> scopeIds,
                                    @Param("year") Short year,
                                    @Param("month") Byte month,
                                    @Param("status") com.company.kra.common.enums.PerformanceStatus status,
                                    Pageable pageable);

    // ------------------------------------------------------------- reporting

    /** Monthly scores with the employee they belong to, for the report grid. */
    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name, des.name,
                   m.perfYear, m.perfMonth, m.overallScore, m.status, m.formulaVersion,
                   m.ratingBandId, m.taskCompletionScore, m.kraScore, m.kpiScore,
                   m.productivityScore, m.qualityScore, m.managerRating
            FROM MonthlyPerformance m, Employee e LEFT JOIN e.department dep
                 LEFT JOIN e.designation des
            WHERE e.id = m.employeeId
              AND m.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND m.perfYear = :year
              AND (:month IS NULL OR m.perfMonth = :month)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:bandId IS NULL OR m.ratingBandId = :bandId)
            ORDER BY m.perfMonth ASC, m.overallScore DESC
            """)
    List<Object[]> reportRows(@Param("companyId") Long companyId,
                              @Param("scopeAll") boolean scopeAll,
                              @Param("scopeIds") Set<Long> scopeIds,
                              @Param("year") short year,
                              @Param("month") Byte month,
                              @Param("employeeId") Long employeeId,
                              @Param("departmentId") Long departmentId,
                              @Param("bandId") Long bandId);

    /** Departmental averages over the monthly snapshots of one period. */
    @Query("""
            SELECT dep.name, COUNT(m.id), AVG(m.overallScore), MIN(m.overallScore),
                   MAX(m.overallScore), AVG(m.kraScore), AVG(m.kpiScore), AVG(m.productivityScore)
            FROM MonthlyPerformance m, Employee e LEFT JOIN e.department dep
            WHERE e.id = m.employeeId
              AND m.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND m.perfYear = :year
              AND (:month IS NULL OR m.perfMonth = :month)
              AND m.overallScore IS NOT NULL
            GROUP BY dep.name
            ORDER BY AVG(m.overallScore) DESC
            """)
    List<Object[]> reportDepartmentAverages(@Param("companyId") Long companyId,
                                            @Param("scopeAll") boolean scopeAll,
                                            @Param("scopeIds") Set<Long> scopeIds,
                                            @Param("year") short year,
                                            @Param("month") Byte month);

    /**
     * Ranking within a period. The direction is a parameter rather than two
     * near-identical queries, so "top" and "bottom" can never drift apart.
     */
    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name, des.name,
                   m.overallScore, m.ratingBandId, m.perfYear, m.perfMonth, m.status
            FROM MonthlyPerformance m, Employee e LEFT JOIN e.department dep
                 LEFT JOIN e.designation des
            WHERE e.id = m.employeeId
              AND m.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND m.perfYear = :year
              AND (:month IS NULL OR m.perfMonth = :month)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND m.overallScore IS NOT NULL
            ORDER BY CASE WHEN :ascending = TRUE THEN m.overallScore END ASC,
                     CASE WHEN :ascending = FALSE THEN m.overallScore END DESC
            """)
    List<Object[]> reportRanking(@Param("companyId") Long companyId,
                                 @Param("scopeAll") boolean scopeAll,
                                 @Param("scopeIds") Set<Long> scopeIds,
                                 @Param("year") short year,
                                 @Param("month") Byte month,
                                 @Param("departmentId") Long departmentId,
                                 @Param("ascending") boolean ascending,
                                 Pageable pageable);

    /**
     * How many people landed in each rating band, for the distribution chart.
     * Scores with no band — nothing measurable that month — are left out
     * rather than lumped into the lowest one.
     */
    @Query("""
            SELECT m.ratingBandId, COUNT(m.id)
            FROM MonthlyPerformance m, Employee e LEFT JOIN e.department dep
            WHERE e.id = m.employeeId
              AND m.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND m.perfYear = :year
              AND (:month IS NULL OR m.perfMonth = :month)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND m.ratingBandId IS NOT NULL
            GROUP BY m.ratingBandId
            """)
    List<Object[]> reportBandDistribution(@Param("companyId") Long companyId,
                                          @Param("scopeAll") boolean scopeAll,
                                          @Param("scopeIds") Set<Long> scopeIds,
                                          @Param("year") short year,
                                          @Param("month") Byte month,
                                          @Param("departmentId") Long departmentId);

    /** The average score across a scope, for a single headline figure. */
    @Query("""
            SELECT AVG(m.overallScore), COUNT(m.id)
            FROM MonthlyPerformance m, Employee e LEFT JOIN e.department dep
            WHERE e.id = m.employeeId
              AND m.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND m.perfYear = :year
              AND (:month IS NULL OR m.perfMonth = :month)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND m.overallScore IS NOT NULL
            """)
    List<Object[]> reportAverageScore(@Param("companyId") Long companyId,
                                      @Param("scopeAll") boolean scopeAll,
                                      @Param("scopeIds") Set<Long> scopeIds,
                                      @Param("year") short year,
                                      @Param("month") Byte month,
                                      @Param("departmentId") Long departmentId);
}
