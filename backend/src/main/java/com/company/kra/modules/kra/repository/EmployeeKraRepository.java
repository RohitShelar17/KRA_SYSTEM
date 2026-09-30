package com.company.kra.modules.kra.repository;

import com.company.kra.common.enums.PeriodType;
import com.company.kra.modules.kra.entity.EmployeeKra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EmployeeKraRepository extends JpaRepository<EmployeeKra, Long> {

    @EntityGraph(attributePaths = {"kpis", "employee"})
    Optional<EmployeeKra> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = {"kpis"})
    List<EmployeeKra> findByEmployeeIdAndPeriodYearAndPeriodIndexAndPeriodTypeAndActiveTrueOrderByDisplayOrderAsc(
            Long employeeId, short periodYear, byte periodIndex, PeriodType periodType);

    @EntityGraph(attributePaths = {"kpis"})
    List<EmployeeKra> findByEmployeeIdAndActiveTrueOrderByPeriodStartDescDisplayOrderAsc(Long employeeId);

    /** Assignments covering a date, used by the work log to offer KRA attribution. */
    @EntityGraph(attributePaths = {"kpis"})
    @Query("""
            SELECT k FROM EmployeeKra k
            WHERE k.employee.id = :employeeId
              AND k.active = TRUE
              AND k.status = com.company.kra.common.enums.KraStatus.ACTIVE
              AND :onDate BETWEEN k.periodStart AND k.periodEnd
            ORDER BY k.displayOrder ASC
            """)
    List<EmployeeKra> findActiveOn(@Param("employeeId") Long employeeId, @Param("onDate") LocalDate onDate);

    /** KRAs whose period overlaps a window, so a monthly score can read a quarterly KRA. */
    @EntityGraph(attributePaths = {"kpis"})
    @Query("""
            SELECT k FROM EmployeeKra k
            WHERE k.employee.id = :employeeId
              AND k.active = TRUE
              AND k.periodStart <= :to
              AND k.periodEnd >= :from
            ORDER BY k.displayOrder ASC
            """)
    List<EmployeeKra> findOverlapping(@Param("employeeId") Long employeeId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    boolean existsByEmployeeIdAndPeriodYearAndPeriodIndexAndPeriodTypeAndActiveTrue(
            Long employeeId, short periodYear, byte periodIndex, PeriodType periodType);

    @Query("""
            SELECT DISTINCT k.employee.id FROM EmployeeKra k
            WHERE k.companyId = :companyId
              AND k.active = TRUE
              AND k.employee.id IN :employeeIds
              AND k.periodYear = :year AND k.periodIndex = :index AND k.periodType = :periodType
            """)
    Set<Long> findEmployeeIdsWithAssignment(@Param("companyId") Long companyId,
                                            @Param("employeeIds") Set<Long> employeeIds,
                                            @Param("year") short year,
                                            @Param("index") byte index,
                                            @Param("periodType") PeriodType periodType);
}
