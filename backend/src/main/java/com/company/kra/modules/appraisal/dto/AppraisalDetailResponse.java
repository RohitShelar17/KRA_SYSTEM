package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.AppraisalStatus;
import com.company.kra.common.enums.AppraisalStep;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the appraisal screen needs: the workflow, the inputs and the score. */
public record AppraisalDetailResponse(Long id, Long cycleId, String cycleName, String periodLabel,
                                      LocalDate periodStart, LocalDate periodEnd,
                                      Long employeeId, String employeeName, String employeeCode,
                                      String designation, String managerName,
                                      AppraisalStep currentStep, AppraisalStatus status,
                                      int stepPosition, int stepCount,
                                      List<WorkflowStepResponse> workflow,
                                      SelfAssessmentResponse selfAssessment,
                                      List<ManagerReviewResponse> reviews,
                                      List<StepActionResponse> history,
                                      BigDecimal computedScore, BigDecimal finalScore,
                                      String finalRatingBandName, String finalRatingBandColour,
                                      LocalDateTime completedAt,
                                      boolean canSubmitSelfAssessment, boolean canReview,
                                      boolean canAdvance) {
}
