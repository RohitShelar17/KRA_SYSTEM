package com.company.kra.modules.notification.repository;

import com.company.kra.modules.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    List<NotificationDelivery> findByNotificationId(Long notificationId);
}
