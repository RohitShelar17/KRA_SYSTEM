package com.company.kra.modules.notification.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.NotificationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The wording of one kind of notification, per company.
 *
 * <p>Text lives here rather than in Java so an administrator can reword a
 * message without a release, and so the same event can read differently for
 * different organisations.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends CompanyScopedEntity {

    @Column(name = "event_key", nullable = false, length = 60)
    private String eventKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(name = "body_template", nullable = false, length = 1000)
    private String bodyTemplate;

    @Column(name = "link_template", length = 200)
    private String linkTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(nullable = false)
    private boolean email = false;
}
