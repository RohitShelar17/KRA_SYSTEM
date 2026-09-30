package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.CycleStatus;
import com.company.kra.common.enums.PeriodType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CycleResponse(Long id, String name, String description, PeriodType scopeType,
                            int periodYear, int periodIndex, String periodLabel,
                            LocalDate periodStart, LocalDate periodEnd,
                            LocalDate selfAssessmentOpens, LocalDate selfAssessmentCloses,
                            LocalDate reviewOpens, LocalDate reviewCloses,
                            CycleStatus status, boolean selfAssessmentOpen, boolean reviewOpen,
                            LocalDateTime openedAt, LocalDateTime closedAt,
                            long participantCount, long completedCount,
                            double completionPercent) {
}
