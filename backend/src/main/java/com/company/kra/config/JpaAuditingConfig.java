package com.company.kra.config;

import com.company.kra.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Fills created_by / updated_by from the authenticated caller. Rows written by
 * scheduled jobs and migrations legitimately have no auditor.
 *
 * @see CreatedBy
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> Optional.ofNullable(SecurityUtils.currentUserId().orElse(null));
    }
}
