package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.ReviewerRole;
import com.company.kra.common.enums.SubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ManagerReviewResponse(Long id, Long appraisalId, Long employeeId, String employeeName,
                                    Long reviewerEmployeeId, String reviewerName,
                                    ReviewerRole reviewerRole, Map<String, BigDecimal> ratings,
                                    BigDecimal overallRating, String strengths, String improvements,
                                    String comments, SubmissionStatus status, boolean editable,
                                    LocalDateTime submittedAt) {
}
