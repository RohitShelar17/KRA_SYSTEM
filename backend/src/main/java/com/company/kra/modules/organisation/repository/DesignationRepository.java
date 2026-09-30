package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignationRepository extends JpaRepository<Designation, Long> {

    Optional<Designation> findByIdAndCompanyId(Long id, Long companyId);

    List<Designation> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    List<Designation> findByCompanyIdAndDepartmentIdAndActiveTrueOrderByNameAsc(Long companyId, Long departmentId);

    boolean existsByCodeIgnoreCaseAndCompanyId(String code, Long companyId);
}
