package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.ApprovalAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull(message = "Select a decision")
        ApprovalAction action,

        @Size(max = 500)
        String comments) {
}
