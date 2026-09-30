package com.company.kra.modules.appraisal.dto;

import jakarta.validation.constraints.Size;

/** Moving an appraisal forward, or sending it back a step with a reason. */
public record AdvanceStepRequest(
        @Size(max = 1000) String comments,

        /** Skip the current step; only allowed when the workflow marks it optional. */
        boolean skip) {
}
