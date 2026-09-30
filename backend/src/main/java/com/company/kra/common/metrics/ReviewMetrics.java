package com.company.kra.common.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The ratings a reviewer recorded, on the 0-100 scale the scoring formula
 * works in.
 *
 * <p>This is what closes the loop: until an evaluation is submitted these
 * return null and the engine redistributes their weight, and the moment one is
 * submitted the same parameters start counting.</p>
 */
public interface ReviewMetrics {

    /** The manager's quality rating covering a period. */
    BigDecimal qualityRatingPercent(Long employeeId, LocalDate from, LocalDate to);

    /** The manager's overall rating covering a period. */
    BigDecimal managerRatingPercent(Long employeeId, LocalDate from, LocalDate to);

    /** The department head's overall rating, where that step is part of the flow. */
    BigDecimal departmentHeadRatingPercent(Long employeeId, LocalDate from, LocalDate to);

    /** The employee's own rating. Recorded for context; weighted only if configured. */
    BigDecimal selfRatingPercent(Long employeeId, LocalDate from, LocalDate to);
}
