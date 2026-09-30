package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.CycleStatus;
import com.company.kra.common.enums.PeriodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An appraisal round: the period being reviewed and the windows for doing it. */
@Getter
@Setter
@Entity
@Table(name = "performance_cycles")
public class PerformanceCycle extends CompanyScopedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PeriodType scopeType = PeriodType.QUARTERLY;

    @Column(name = "period_year", nullable = false)
    private short periodYear;

    @Column(name = "period_index", nullable = false)
    private byte periodIndex;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "self_assessment_opens")
    private LocalDate selfAssessmentOpens;

    @Column(name = "self_assessment_closes")
    private LocalDate selfAssessmentCloses;

    @Column(name = "review_opens")
    private LocalDate reviewOpens;

    @Column(name = "review_closes")
    private LocalDate reviewCloses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleStatus status = CycleStatus.DRAFT;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    public String periodLabel() {
        return scopeType.label(periodYear, periodIndex);
    }

    /** Self assessment is only accepted inside its window, when one is set. */
    public boolean selfAssessmentOpen(LocalDate on) {
        if (!status.acceptsInput()) {
            return false;
        }
        boolean afterOpen = selfAssessmentOpens == null || !on.isBefore(selfAssessmentOpens);
        boolean beforeClose = selfAssessmentCloses == null || !on.isAfter(selfAssessmentCloses);
        return afterOpen && beforeClose;
    }

    public boolean reviewOpen(LocalDate on) {
        if (!status.acceptsInput()) {
            return false;
        }
        boolean afterOpen = reviewOpens == null || !on.isBefore(reviewOpens);
        boolean beforeClose = reviewCloses == null || !on.isAfter(reviewCloses);
        return afterOpen && beforeClose;
    }
}
