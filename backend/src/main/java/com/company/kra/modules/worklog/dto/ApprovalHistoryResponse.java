package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.ApprovalAction;

import java.time.LocalDateTime;

public record ApprovalHistoryResponse(Long id, ApprovalAction action, String fromStatus, String toStatus,
                                      String actorName, String comments, LocalDateTime createdAt) {
}
