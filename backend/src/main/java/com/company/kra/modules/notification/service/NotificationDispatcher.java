package com.company.kra.modules.notification.service;

import com.company.kra.common.events.NotificationRaised;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for raised events and hands them to the notification service.
 *
 * <p>Two deliberate choices. It runs {@code AFTER_COMMIT}, so nobody is told
 * about an approval that then rolled back. And it runs {@code @Async}, so a
 * slow channel never adds latency to the request that caused it.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationRaised event) {
        try {
            int told = notificationService.dispatch(event);
            log.debug("Event {} notified {} recipient(s)", event.eventKey(), told);
        } catch (RuntimeException failure) {
            // The business fact is already committed. Losing the message is
            // bad; undoing the work behind it would be worse.
            log.error("Failed to dispatch notification for event {}", event.eventKey(), failure);
        }
    }
}
