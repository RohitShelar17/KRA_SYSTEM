package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.PerformanceRatingBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerformanceRatingBandRepository extends JpaRepository<PerformanceRatingBand, Long> {

    List<PerformanceRatingBand> findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(Long companyId);

    Optional<PerformanceRatingBand> findByIdAndCompanyId(Long id, Long companyId);
}
