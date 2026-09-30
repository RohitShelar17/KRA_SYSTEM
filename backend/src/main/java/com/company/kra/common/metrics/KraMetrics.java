package com.company.kra.common.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Goal achievement over a window, for the scoring engine.
 *
 * <p>Returns null rather than zero when nothing can be measured, so the engine
 * can exclude the parameter instead of scoring an administrative gap as a
 * failure.</p>
 */
public interface KraMetrics {

    /** Weighted achievement across the KRAs covering this window. */
    BigDecimal kraAchievementPercent(Long employeeId, LocalDate from, LocalDate to);

    /** Weighted achievement across every scorable KPI in those KRAs. */
    BigDecimal kpiAchievementPercent(Long employeeId, LocalDate from, LocalDate to);
}
