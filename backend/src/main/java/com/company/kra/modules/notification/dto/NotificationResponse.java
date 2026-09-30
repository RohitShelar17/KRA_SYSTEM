package com.company.kra.modules.notification.dto;

import com.company.kra.common.enums.NotificationSeverity;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String eventKey, String title, String body, String link,
                                   NotificationSeverity severity, String entityType, Long entityId,
                                   boolean read, LocalDateTime readAt, LocalDateTime createdAt) {
}
