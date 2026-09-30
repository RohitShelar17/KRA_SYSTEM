package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
