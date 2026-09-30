package com.company.kra.modules.kra.repository;

import com.company.kra.modules.kra.entity.KpiActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KpiActualRepository extends JpaRepository<KpiActual, Long> {

    Optional<KpiActual> findByIdAndCompanyId(Long id, Long companyId);

    List<KpiActual> findByEmployeeKpiIdOrderByPeriodStartAsc(Long employeeKpiId);

    Optional<KpiActual> findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(
            Long employeeKpiId, LocalDate periodStart, LocalDate periodEnd);

    @Query("""
            SELECT a FROM KpiActual a
            WHERE a.employeeKpi.id IN :kpiIds
              AND a.periodStart >= :from
              AND a.periodEnd <= :to
            """)
    List<KpiActual> findWithin(@Param("kpiIds") List<Long> kpiIds,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}
