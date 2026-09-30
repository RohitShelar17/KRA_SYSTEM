package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PeriodType;
import com.company.kra.modules.performance.entity.AnnualPerformance;
import com.company.kra.modules.performance.entity.MonthlyPerformance;
import com.company.kra.modules.performance.entity.PerformanceRatingBand;
import com.company.kra.modules.performance.entity.QuarterlyPerformance;
import com.company.kra.modules.performance.repository.AnnualPerformanceRepository;
import com.company.kra.modules.performance.repository.MonthlyPerformanceRepository;
import com.company.kra.modules.performance.repository.PerformanceRatingBandRepository;
import com.company.kra.modules.performance.repository.QuarterlyPerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/** Serves computed scores to the appraisal module without exposing the snapshot tables. */
@Service
@RequiredArgsConstructor
public class SnapshotLookupService implements SnapshotLookup {

    private final MonthlyPerformanceRepository monthlyRepository;
    private final QuarterlyPerformanceRepository quarterlyRepository;
    private final AnnualPerformanceRepository annualRepository;
    private final PerformanceRatingBandRepository bandRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ScoreSnapshot> find(Long employeeId, PeriodType scope, int year, int index) {
        return switch (scope) {
            case MONTHLY -> monthlyRepository
                    .findByEmployeeIdAndPerfYearAndPerfMonth(employeeId, (short) year, (byte) index)
                    .map(row -> toSnapshot(row.getOverallScore(), row.getRatingBandId()));
            case QUARTERLY -> quarterlyRepository
                    .findByEmployeeIdAndPerfYearAndPerfQuarter(employeeId, (short) year, (byte) index)
                    .map(row -> toSnapshot(row.getOverallScore(), row.getRatingBandId()));
            case ANNUAL -> annualRepository.findByEmployeeIdAndPerfYear(employeeId, (short) year)
                    .map(row -> toSnapshot(row.getOverallScore(), row.getRatingBandId()));
            // Nobody is appraised on a single day or week.
            case DAILY, WEEKLY -> Optional.empty();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScoreSnapshot> bandOf(Long companyId, BigDecimal score) {
        if (score == null) {
            return Optional.empty();
        }
        return bandRepository.findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(companyId).stream()
                .filter(band -> band.covers(score))
                .findFirst()
                .map(band -> new ScoreSnapshot(score, band.getId(), band.getName(),
                        band.getColourHex()));
    }

    private ScoreSnapshot toSnapshot(BigDecimal score, Long bandId) {
        PerformanceRatingBand band = bandId == null ? null
                : bandRepository.findById(bandId).orElse(null);
        return new ScoreSnapshot(score, bandId, band == null ? null : band.getName(),
                band == null ? null : band.getColourHex());
    }
}
