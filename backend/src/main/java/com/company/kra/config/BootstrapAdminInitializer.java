package com.company.kra.config;

import com.company.kra.common.enums.UserStatus;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activates the seeded administrator on first start.
 *
 * <p>The seed migration deliberately leaves the password hash empty: committing
 * a hash to source control would ship a known credential with every checkout.
 * The password comes from ADMIN_DEFAULT_PASSWORD, is encoded here, and the
 * account is flagged to force a change at first sign-in.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${ADMIN_DEFAULT_PASSWORD:Admin@12345}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByUsernameIgnoreCase(adminUsername).ifPresent(this::activateIfPending);
    }

    private void activateIfPending(User admin) {
        boolean needsPassword = admin.getPasswordHash() == null || admin.getPasswordHash().isBlank();
        if (!needsPassword) {
            return;
        }
        admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        log.warn("Bootstrap administrator '{}' activated with the default password. "
                + "Change it at first sign-in.", admin.getUsername());
    }
}
