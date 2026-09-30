package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByIdAndCompanyId(Long id, Long companyId);

    List<Team> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    List<Team> findByCompanyIdAndDepartmentIdAndActiveTrueOrderByNameAsc(Long companyId, Long departmentId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);
}
