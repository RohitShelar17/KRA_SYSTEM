package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {

    Optional<CompanySettings> findByCompanyId(Long companyId);
}
