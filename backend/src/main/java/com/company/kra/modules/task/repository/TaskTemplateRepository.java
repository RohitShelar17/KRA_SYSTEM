package com.company.kra.modules.task.repository;

import com.company.kra.modules.task.entity.TaskTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {

    @EntityGraph(attributePaths = "taskCategory")
    Optional<TaskTemplate> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = "taskCategory")
    @Query("""
            SELECT t FROM TaskTemplate t
            WHERE t.companyId = :companyId
              AND t.active = TRUE
              AND (:departmentId IS NULL OR t.departmentId IS NULL OR t.departmentId = :departmentId)
            ORDER BY t.displayOrder ASC, t.name ASC
            """)
    List<TaskTemplate> findAvailableFor(@Param("companyId") Long companyId,
                                        @Param("departmentId") Long departmentId);
}
