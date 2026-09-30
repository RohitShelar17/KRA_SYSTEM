package com.company.kra.modules.notification.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.NotificationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One rendered message for one recipient.
 *
 * <p>The text is stored rather than re-rendered on read: a notification is a
 * record of what somebody was told, so editing a template later must not
 * rewrite what was already delivered.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "recipient_employee_id")
    private Long recipientEmployeeId;

    @Column(name = "event_key", nullable = false, length = 60)
    private String eventKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(length = 200)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public void markRead() {
        if (!read) {
            read = true;
            readAt = LocalDateTime.now();
        }
    }
}
