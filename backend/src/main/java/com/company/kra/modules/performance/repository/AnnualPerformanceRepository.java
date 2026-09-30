package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.AnnualPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AnnualPerformanceRepository extends JpaRepository<AnnualPerformance, Long> {

    Optional<AnnualPerformance> findByEmployeeIdAndPerfYear(Long employeeId, short perfYear);

    Optional<AnnualPerformance> findByIdAndCompanyId(Long id, Long companyId);

    List<AnnualPerformance> findByEmployeeIdOrderByPerfYearDesc(Long employeeId);

    // ------------------------------------------------------------- reporting

    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name, des.name,
                   a.perfYear, a.overallScore, a.status, a.formulaVersion, a.ratingBandId,
                   a.quarterlyAverageScore, a.quartersIncluded
            FROM AnnualPerformance a, Employee e LEFT JOIN e.department dep
                 LEFT JOIN e.designation des
            WHERE e.id = a.employeeId
              AND a.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND a.perfYear = :year
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:bandId IS NULL OR a.ratingBandId = :bandId)
            ORDER BY a.overallScore DESC
            """)
    List<Object[]> reportRows(@Param("companyId") Long companyId,
                              @Param("scopeAll") boolean scopeAll,
                              @Param("scopeIds") Set<Long> scopeIds,
                              @Param("year") short year,
                              @Param("employeeId") Long employeeId,
                              @Param("departmentId") Long departmentId,
                              @Param("bandId") Long bandId);
}
