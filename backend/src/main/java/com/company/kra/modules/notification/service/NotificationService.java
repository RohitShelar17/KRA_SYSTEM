package com.company.kra.modules.notification.service;

import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.DeliveryStatus;
import com.company.kra.common.enums.NotificationChannelType;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.notification.channel.NotificationChannel;
import com.company.kra.modules.notification.dto.NotificationResponse;
import com.company.kra.modules.notification.entity.Notification;
import com.company.kra.modules.notification.entity.NotificationDelivery;
import com.company.kra.modules.notification.entity.NotificationTemplate;
import com.company.kra.modules.notification.repository.NotificationDeliveryRepository;
import com.company.kra.modules.notification.repository.NotificationRepository;
import com.company.kra.modules.notification.repository.NotificationTemplateRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a raised event into rendered messages, and serves the feed.
 *
 * <p>Delivery never blocks the work that caused it. A failure to notify is
 * logged and recorded on the delivery row; it does not roll back the approval,
 * the score or the sign-off that triggered it, because the business fact has
 * already happened and the message is a consequence of it, not a condition.</p>
 */
@Slf4j
@Service
public class NotificationService {

    private static final int MAX_TITLE = 200;
    private static final int MAX_BODY = 1000;

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final EmployeeDirectory employeeDirectory;
    private final TemplateRenderer renderer;
    private final List<NotificationChannel> channels;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationTemplateRepository templateRepository,
                               NotificationDeliveryRepository deliveryRepository,
                               EmployeeDirectory employeeDirectory,
                               TemplateRenderer renderer,
                               List<NotificationChannel> channels) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.deliveryRepository = deliveryRepository;
        this.employeeDirectory = employeeDirectory;
        this.renderer = renderer;
        this.channels = channels;
    }

    // ------------------------------------------------------------ dispatch

    /**
     * Runs in its own transaction: the event that caused it has already
     * committed, and a notification problem must not undo it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatch(NotificationRaised event) {
        Optional<NotificationTemplate> maybeTemplate = templateRepository
                .findByCompanyIdAndEventKeyAndActiveTrue(event.companyId(), event.eventKey());

        if (maybeTemplate.isEmpty()) {
            // A missing template is a configuration gap, not a crash: the work
            // stands, and the gap is visible in the log.
            log.warn("No active notification template for '{}'; nobody was told", event.eventKey());
            return 0;
        }
        if (event.recipientEmployeeIds().isEmpty()) {
            return 0;
        }

        NotificationTemplate template = maybeTemplate.get();
        Map<Long, EmployeeDirectory.Account> accounts =
                employeeDirectory.accountsOf(event.recipientEmployeeIds());

        int sent = 0;
        for (Long employeeId : event.recipientEmployeeIds()) {
            EmployeeDirectory.Account account = accounts.get(employeeId);
            if (account == null) {
                log.debug("Employee {} has no account; '{}' not delivered", employeeId, event.eventKey());
                continue;
            }
            notify(template, account, event);
            sent++;
        }
        return sent;
    }

    private void notify(NotificationTemplate template, EmployeeDirectory.Account account,
                        NotificationRaised event) {
        Map<String, String> values = event.placeholders();

        Notification notification = new Notification();
        notification.setCompanyId(event.companyId());
        notification.setRecipientUserId(account.userId());
        notification.setRecipientEmployeeId(account.employeeId());
        notification.setEventKey(event.eventKey());
        notification.setTitle(renderer.fit(renderer.render(template.getTitleTemplate(), values), MAX_TITLE));
        notification.setBody(renderer.fit(renderer.render(template.getBodyTemplate(), values), MAX_BODY));
        notification.setLink(renderer.render(template.getLinkTemplate(), values));
        notification.setSeverity(template.getSeverity());
        notification.setEntityType(event.entityType());
        notification.setEntityId(event.entityId());
        notificationRepository.save(notification);

        for (NotificationChannel channel : channels) {
            if (!wants(template, channel.type())) {
                continue;
            }
            record(notification, channel, account.email());
        }
    }

    private boolean wants(NotificationTemplate template, NotificationChannelType type) {
        return switch (type) {
            case IN_APP -> template.isInApp();
            case EMAIL -> template.isEmail();
        };
    }

    private void record(Notification notification, NotificationChannel channel, String email) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setCompanyId(notification.getCompanyId());
        delivery.setNotification(notification);
        delivery.setChannel(channel.type());
        delivery.setAttempts(1);

        try {
            NotificationChannel.Outcome outcome = channel.deliver(notification, email);
            delivery.setDetail(outcome.detail());
            if (outcome.delivered()) {
                delivery.setStatus(DeliveryStatus.SENT);
                delivery.setSentAt(LocalDateTime.now());
            } else {
                delivery.setStatus(outcome.retryable() ? DeliveryStatus.FAILED : DeliveryStatus.SKIPPED);
            }
        } catch (RuntimeException failure) {
            // A channel that throws is a bug in that channel; the others still run.
            log.error("Channel {} threw delivering notification {}", channel.type(),
                    notification.getId(), failure);
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setDetail(renderer.fit(String.valueOf(failure.getMessage()), 500));
        }
        deliveryRepository.save(delivery);
    }

    /**
     * True when this person has already been told this about this thing today.
     * Reminder jobs run daily and must not stack copies.
     */
    @Transactional(readOnly = true)
    public boolean alreadyToldToday(Long userId, String eventKey, String entityType, Long entityId) {
        return notificationRepository
                .existsByRecipientUserIdAndEventKeyAndEntityTypeAndEntityIdAndCreatedAtAfter(
                        userId, eventKey, entityType, entityId,
                        LocalDateTime.now().minusHours(20));
    }

    // ---------------------------------------------------------------- feed

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> feed(boolean unreadOnly, int page, int size) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));

        Page<Notification> rows = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(
                        principal.getUserId(), pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(
                        principal.getUserId(), pageable);

        return PageResponse.from(rows, this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByRecipientUserIdAndReadFalse(
                SecurityUtils.requirePrincipal().getUserId());
    }

    /** Only the recipient may mark their own notifications read. */
    @Transactional
    public int markRead(List<Long> ids) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Notification> rows = notificationRepository.findByIdInAndRecipientUserId(ids,
                principal.getUserId());
        List<Notification> changed = new ArrayList<>();
        for (Notification row : rows) {
            if (!row.isRead()) {
                row.markRead();
                changed.add(row);
            }
        }
        notificationRepository.saveAll(changed);
        return changed.size();
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(SecurityUtils.requirePrincipal().getUserId(),
                LocalDateTime.now());
    }

    private NotificationResponse toResponse(Notification row) {
        return new NotificationResponse(row.getId(), row.getEventKey(), row.getTitle(), row.getBody(),
                row.getLink(), row.getSeverity(), row.getEntityType(), row.getEntityId(),
                row.isRead(), row.getReadAt(), row.getCreatedAt());
    }
}
