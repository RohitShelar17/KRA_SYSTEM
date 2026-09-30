package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.DailyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPerformanceRepository extends JpaRepository<DailyPerformance, Long> {

    Optional<DailyPerformance> findByEmployeeIdAndPerfDate(Long employeeId, LocalDate perfDate);

    Optional<DailyPerformance> findByIdAndCompanyId(Long id, Long companyId);

    List<DailyPerformance> findByEmployeeIdAndPerfDateBetweenOrderByPerfDateAsc(
            Long employeeId, LocalDate from, LocalDate to);

    /** Mean of the daily scores in a window, ignoring days that could not be scored. */
    @Query("""
            SELECT AVG(d.overallScore) FROM DailyPerformance d
            WHERE d.employeeId = :employeeId
              AND d.perfDate BETWEEN :from AND :to
              AND d.overallScore IS NOT NULL
            """)
    BigDecimal averageScore(@Param("employeeId") Long employeeId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);
}
