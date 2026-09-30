package com.company.kra.modules.kra.repository;

import com.company.kra.modules.kra.entity.EmployeeKpi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeKpiRepository extends JpaRepository<EmployeeKpi, Long> {

    Optional<EmployeeKpi> findByIdAndCompanyId(Long id, Long companyId);

    List<EmployeeKpi> findByEmployeeKraIdOrderByDisplayOrderAsc(Long employeeKraId);
}
