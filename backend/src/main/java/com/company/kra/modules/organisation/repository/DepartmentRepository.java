package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Department> findByCodeIgnoreCaseAndCompanyId(String code, Long companyId);

    List<Department> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);

    boolean existsByParentDepartmentId(Long parentDepartmentId);

    @Query("""
            SELECT d FROM Department d
            WHERE d.companyId = :companyId
              AND (:search IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:activeOnly = FALSE OR d.active = TRUE)
            """)
    Page<Department> search(@Param("companyId") Long companyId,
                           @Param("search") String search,
                           @Param("activeOnly") boolean activeOnly,
                           Pageable pageable);
}
