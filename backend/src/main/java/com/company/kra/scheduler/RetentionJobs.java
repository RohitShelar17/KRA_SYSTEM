package com.company.kra.scheduler;

import com.company.kra.modules.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Keeps the growth tables from growing without end.
 *
 * <p>Only notifications are cleared, and only ones the recipient has already
 * read: an unread message is still owed to somebody however old it is. Audit
 * rows are deliberately untouched — they are evidence, the repository has no
 * delete path, and archiving them is an operational task with its own
 * retention policy rather than something the application decides on its
 * own.</p>
 */
@Slf4j
@Component
public class RetentionJobs {

    private final NotificationRepository notificationRepository;
    private final int retentionDays;

    public RetentionJobs(NotificationRepository notificationRepository,
                         @Value("${app.retention.notification-days:120}") int retentionDays) {
        this.notificationRepository = notificationRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.jobs.retention:0 45 2 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void pruneReadNotifications() {
        if (retentionDays <= 0) {
            log.debug("Notification retention is switched off");
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int removed = notificationRepository.deleteReadBefore(cutoff);

        if (removed > 0) {
            log.info("Retention removed {} read notifications older than {} days", removed,
                    retentionDays);
        }
    }
}
