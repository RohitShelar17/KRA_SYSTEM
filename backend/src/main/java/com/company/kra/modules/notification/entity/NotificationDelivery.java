package com.company.kra.modules.notification.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.DeliveryStatus;
import com.company.kra.common.enums.NotificationChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * What happened when one notification was pushed down one channel.
 *
 * <p>This is the row that answers "nobody told me": it records the attempt,
 * the outcome and, when a channel declined, why.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 500)
    private String detail;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
