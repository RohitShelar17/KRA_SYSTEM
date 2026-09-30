package com.company.kra.modules.notification.repository;

import com.company.kra.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByRecipientUserIdAndReadFalse(Long userId);

    List<Notification> findByIdInAndRecipientUserId(List<Long> ids, Long userId);

    /**
     * One row per recipient per event per entity is the rule for reminders:
     * a daily job must not stack five copies of "your log is missing".
     */
    boolean existsByRecipientUserIdAndEventKeyAndEntityTypeAndEntityIdAndCreatedAtAfter(
            Long userId, String eventKey, String entityType, Long entityId, LocalDateTime since);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.read = TRUE, n.readAt = :now
            WHERE n.recipientUserId = :userId AND n.read = FALSE
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Clears read notifications past the retention window. Unread ones stay:
     * age does not settle a message somebody never saw.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = TRUE AND n.createdAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") LocalDateTime cutoff);
}
