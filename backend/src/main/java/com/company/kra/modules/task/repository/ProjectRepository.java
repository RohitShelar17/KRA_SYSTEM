package com.company.kra.modules.task.repository;

import com.company.kra.modules.task.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndCompanyId(Long id, Long companyId);

    List<Project> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);
}
