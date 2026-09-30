package com.company.kra.modules.kra.repository;

import com.company.kra.modules.kra.entity.KraTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KraTemplateRepository extends JpaRepository<KraTemplate, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<KraTemplate> findByIdAndCompanyId(Long id, Long companyId);

    List<KraTemplate> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);

    /** Templates offered for a role, most specific first. */
    @Query("""
            SELECT t FROM KraTemplate t
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND t.published = TRUE
              AND (:designationId IS NULL OR t.designationId IS NULL OR t.designationId = :designationId)
            ORDER BY t.designationId DESC, t.name ASC
            """)
    List<KraTemplate> findAssignableFor(@Param("companyId") Long companyId,
                                        @Param("designationId") Long designationId);
}
