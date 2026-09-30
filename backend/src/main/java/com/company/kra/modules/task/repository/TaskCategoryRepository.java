package com.company.kra.modules.task.repository;

import com.company.kra.modules.task.entity.TaskCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskCategoryRepository extends JpaRepository<TaskCategory, Long> {

    Optional<TaskCategory> findByIdAndCompanyId(Long id, Long companyId);

    List<TaskCategory> findByCompanyIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long companyId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);

    /** Company-wide categories plus the ones a department defines for itself. */
    @Query("""
            SELECT c FROM TaskCategory c
            WHERE c.companyId = :companyId
              AND c.active = TRUE
              AND (c.departmentId IS NULL OR c.departmentId = :departmentId)
            ORDER BY c.displayOrder ASC, c.name ASC
            """)
    List<TaskCategory> findAvailableFor(@Param("companyId") Long companyId,
                                        @Param("departmentId") Long departmentId);
}
