package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.AppraisalStatus;
import com.company.kra.common.enums.AppraisalStep;

import java.math.BigDecimal;

/** Row shape for the review queue and the cycle roster. */
public record AppraisalSummaryResponse(Long id, Long cycleId, String cycleName, String periodLabel,
                                       Long employeeId, String employeeName, String employeeCode,
                                       String designation, AppraisalStep currentStep,
                                       String currentStepName, AppraisalStatus status,
                                       BigDecimal computedScore, BigDecimal finalScore,
                                       boolean selfAssessmentSubmitted, boolean managerReviewSubmitted,
                                       boolean waitingOnMe) {
}
