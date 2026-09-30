package com.company.kra.modules.employee.repository;

import com.company.kra.modules.employee.entity.ReportingHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReportingHierarchyRepository extends JpaRepository<ReportingHierarchy, Long> {

    @Query("""
            SELECT r FROM ReportingHierarchy r
            WHERE r.employeeId = :employeeId
              AND r.relationType = com.company.kra.common.enums.RelationType.PRIMARY
              AND r.validTo IS NULL
            """)
    Optional<ReportingHierarchy> findOpenPrimary(@Param("employeeId") Long employeeId);

    @Query("""
            SELECT r FROM ReportingHierarchy r
            WHERE r.employeeId = :employeeId
              AND r.validFrom <= :onDate
              AND (r.validTo IS NULL OR r.validTo >= :onDate)
            ORDER BY r.validFrom DESC
            """)
    List<ReportingHierarchy> findEffectiveOn(@Param("employeeId") Long employeeId,
                                             @Param("onDate") LocalDate onDate);

    List<ReportingHierarchy> findByEmployeeIdOrderByValidFromDesc(Long employeeId);
}
