package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.QuarterlyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuarterlyPerformanceRepository extends JpaRepository<QuarterlyPerformance, Long> {

    Optional<QuarterlyPerformance> findByEmployeeIdAndPerfYearAndPerfQuarter(
            Long employeeId, short perfYear, byte perfQuarter);

    Optional<QuarterlyPerformance> findByIdAndCompanyId(Long id, Long companyId);

    List<QuarterlyPerformance> findByEmployeeIdAndPerfYearOrderByPerfQuarterAsc(
            Long employeeId, short perfYear);

    // ------------------------------------------------------------- reporting

    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name, des.name,
                   q.perfYear, q.perfQuarter, q.overallScore, q.status, q.formulaVersion,
                   q.ratingBandId, q.monthlyAverageScore, q.monthsIncluded
            FROM QuarterlyPerformance q, Employee e LEFT JOIN e.department dep
                 LEFT JOIN e.designation des
            WHERE e.id = q.employeeId
              AND q.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND q.perfYear = :year
              AND (:quarter IS NULL OR q.perfQuarter = :quarter)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:bandId IS NULL OR q.ratingBandId = :bandId)
            ORDER BY q.perfQuarter ASC, q.overallScore DESC
            """)
    List<Object[]> reportRows(@Param("companyId") Long companyId,
                              @Param("scopeAll") boolean scopeAll,
                              @Param("scopeIds") Set<Long> scopeIds,
                              @Param("year") short year,
                              @Param("quarter") Byte quarter,
                              @Param("employeeId") Long employeeId,
                              @Param("departmentId") Long departmentId,
                              @Param("bandId") Long bandId);
}
