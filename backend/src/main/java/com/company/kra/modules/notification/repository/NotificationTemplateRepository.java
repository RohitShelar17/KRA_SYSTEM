package com.company.kra.modules.notification.repository;

import com.company.kra.modules.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCompanyIdAndEventKeyAndActiveTrue(Long companyId, String eventKey);

    List<NotificationTemplate> findByCompanyIdOrderByEventKeyAsc(Long companyId);
}
