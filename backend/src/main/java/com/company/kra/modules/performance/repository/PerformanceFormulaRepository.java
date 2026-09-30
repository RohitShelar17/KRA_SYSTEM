package com.company.kra.modules.performance.repository;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.modules.performance.entity.PerformanceFormulaConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerformanceFormulaRepository extends JpaRepository<PerformanceFormulaConfig, Long> {

    @EntityGraph(attributePaths = "parameters")
    Optional<PerformanceFormulaConfig> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = "parameters")
    Optional<PerformanceFormulaConfig> findByCompanyIdAndScopeTypeAndActiveTrue(
            Long companyId, PerformanceScope scopeType);

    @EntityGraph(attributePaths = "parameters")
    List<PerformanceFormulaConfig> findByCompanyIdOrderByScopeTypeAscVersionDesc(Long companyId);

    @Query("""
            SELECT COALESCE(MAX(c.version), 0) FROM PerformanceFormulaConfig c
            WHERE c.companyId = :companyId AND c.scopeType = :scopeType
            """)
    int currentVersion(@Param("companyId") Long companyId,
                       @Param("scopeType") PerformanceScope scopeType);

    List<PerformanceFormulaConfig> findByCompanyIdAndScopeType(Long companyId, PerformanceScope scopeType);
}
