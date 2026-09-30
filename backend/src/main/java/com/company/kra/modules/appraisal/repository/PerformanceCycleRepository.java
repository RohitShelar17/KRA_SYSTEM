package com.company.kra.modules.appraisal.repository;

import com.company.kra.common.enums.CycleStatus;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.modules.appraisal.entity.PerformanceCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerformanceCycleRepository extends JpaRepository<PerformanceCycle, Long> {

    Optional<PerformanceCycle> findByIdAndCompanyId(Long id, Long companyId);

    List<PerformanceCycle> findByCompanyIdAndActiveTrueOrderByPeriodStartDesc(Long companyId);

    List<PerformanceCycle> findByCompanyIdAndStatusInAndActiveTrueOrderByPeriodStartDesc(
            Long companyId, List<CycleStatus> statuses);

    boolean existsByCompanyIdAndScopeTypeAndPeriodYearAndPeriodIndex(
            Long companyId, PeriodType scopeType, short periodYear, byte periodIndex);
}
