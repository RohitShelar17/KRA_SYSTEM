package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PeriodType;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What other modules may ask of a computed score.
 *
 * <p>The appraisal module needs to copy the figure it concluded on onto the
 * appraisal record, and to name the band. It gets exactly that, and no access
 * to the snapshot tables.</p>
 */
public interface SnapshotLookup {

    record ScoreSnapshot(BigDecimal score, Long bandId, String bandName, String bandColour) {
    }

    /** The score for a period, if the engine has computed one. */
    Optional<ScoreSnapshot> find(Long employeeId, PeriodType scope, int year, int index);

    /** The band a score falls in, for a figure that was copied rather than looked up. */
    Optional<ScoreSnapshot> bandOf(Long companyId, BigDecimal score);
}
