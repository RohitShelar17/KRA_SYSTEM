package com.company.kra.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to run in production with development defaults.
 *
 * <p>A shipped-but-forgotten signing key is the kind of thing that is only
 * noticed after it has been exploited, so the application stops at startup
 * rather than serving traffic it cannot secure. This only applies under the
 * prod profile: development is meant to start with no ceremony.</p>
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionReadinessCheck {

    private static final String DEV_SECRET_MARKER = "change-me";

    private final String jwtSecret;
    private final String datasourcePassword;
    private final String allowedOrigins;

    public ProductionReadinessCheck(@Value("${app.jwt.secret}") String jwtSecret,
                                    @Value("${spring.datasource.password:}") String datasourcePassword,
                                    @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.datasourcePassword = datasourcePassword;
        this.allowedOrigins = allowedOrigins;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        List<String> problems = new ArrayList<>();

        if (!StringUtils.hasText(jwtSecret) || jwtSecret.contains(DEV_SECRET_MARKER)) {
            problems.add("JWT_SECRET is still the development default");
        }
        if (jwtSecret != null && jwtSecret.length() < 32) {
            problems.add("JWT_SECRET is shorter than 32 characters");
        }
        if (!StringUtils.hasText(datasourcePassword)) {
            problems.add("DB_PASSWORD is empty");
        }
        if (allowedOrigins.contains("localhost")) {
            problems.add("CORS_ALLOWED_ORIGINS still allows localhost");
        }

        if (!problems.isEmpty()) {
            // Named individually: a list of what to fix beats one vague refusal.
            problems.forEach(problem -> log.error("Production configuration problem: {}", problem));
            throw new IllegalStateException("Refusing to run in production with "
                    + problems.size() + " unsafe setting(s): " + String.join("; ", problems));
        }
        log.info("Production configuration checks passed");
    }
}
