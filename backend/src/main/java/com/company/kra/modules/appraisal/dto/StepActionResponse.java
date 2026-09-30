package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.StepAction;

import java.time.LocalDateTime;

public record StepActionResponse(Long id, String stepType, StepAction action, String fromStep,
                                 String toStep, String actorName, String comments,
                                 LocalDateTime createdAt) {
}
