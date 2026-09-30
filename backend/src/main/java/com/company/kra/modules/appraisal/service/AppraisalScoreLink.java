package com.company.kra.modules.appraisal.service;

import com.company.kra.modules.appraisal.entity.AppraisalRecord;
import com.company.kra.modules.performance.service.SnapshotLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The bridge between an appraisal and the score it concluded on.
 *
 * <p>When an appraisal completes the computed score is copied onto the record.
 * From then on the appraisal carries its own figure: a later recalculation, or
 * a formula change, cannot rewrite what was signed off in the conversation.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppraisalScoreLink {

    private final SnapshotLookup snapshotLookup;

    @Transactional
    public void captureFinalScore(AppraisalRecord record) {
        var cycle = record.getCycle();
        Optional<SnapshotLookup.ScoreSnapshot> snapshot = snapshotLookup.find(record.getEmployeeId(),
                cycle.getScopeType(), cycle.getPeriodYear(), cycle.getPeriodIndex());

        if (snapshot.isEmpty()) {
            // Completing without a score is legitimate: a cycle can close on the
            // written record alone if nothing measurable was recorded.
            log.info("Appraisal {} completed with no computed score for {}", record.getId(),
                    cycle.periodLabel());
            return;
        }

        SnapshotLookup.ScoreSnapshot score = snapshot.get();
        record.setComputedScore(score.score());
        record.setFinalScore(score.score());
        record.setFinalRatingBandId(score.bandId());
    }

    @Transactional(readOnly = true)
    public Optional<SnapshotLookup.ScoreSnapshot> bandFor(AppraisalRecord record) {
        if (record.getFinalScore() == null) {
            return Optional.empty();
        }
        return snapshotLookup.bandOf(record.getCompanyId(), record.getFinalScore());
    }
}
