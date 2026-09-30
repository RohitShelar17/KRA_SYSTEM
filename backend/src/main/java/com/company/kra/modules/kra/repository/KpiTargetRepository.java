package com.company.kra.modules.kra.repository;

import com.company.kra.modules.kra.entity.KpiTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KpiTargetRepository extends JpaRepository<KpiTarget, Long> {

    Optional<KpiTarget> findByIdAndCompanyId(Long id, Long companyId);

    List<KpiTarget> findByEmployeeKpiIdOrderByPeriodStartAsc(Long employeeKpiId);

    Optional<KpiTarget> findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(
            Long employeeKpiId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Targets that fall inside a window. A quarterly KRA may hold three
     * monthly targets, so the period sum is what counts, not a single row.
     */
    @Query("""
            SELECT t FROM KpiTarget t
            WHERE t.employeeKpi.id IN :kpiIds
              AND t.periodStart >= :from
              AND t.periodEnd <= :to
            """)
    List<KpiTarget> findWithin(@Param("kpiIds") List<Long> kpiIds,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}
