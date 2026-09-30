package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.SubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SelfAssessmentResponse(Long id, Long appraisalId, Long employeeId, String employeeName,
                                     String achievements, String challenges, String skillsDeveloped,
                                     String improvementAreas, String trainingNeeded, String futureGoals,
                                     String comments, BigDecimal selfRating, SubmissionStatus status,
                                     boolean editable, LocalDateTime submittedAt) {
}
