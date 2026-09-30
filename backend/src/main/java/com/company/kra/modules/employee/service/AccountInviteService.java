package com.company.kra.modules.employee.service;

import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.UserStatus;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.audit.AuditService;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.dto.InviteResponse;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Getting a new joiner their first sign-in.
 *
 * <p>No temporary password is ever created or transmitted. A new account is
 * given a random secret nobody holds and a single-use token; the person sets
 * their own password through the reset page, so at no point does a password
 * exist in an inbox, a chat message or an administrator's memory.</p>
 *
 * <p>The token is also returned to the administrator who asked for it. That is
 * deliberate rather than careless: email can be switched off, misconfigured or
 * simply slow, and without a fallback the only remaining route is reading it
 * out of the database by hand. It is restricted to {@code EMPLOYEE_MANAGE},
 * every issue is audited with who asked, and the token is single-use and
 * expires — an administrator with this permission can already reset the
 * account by other means, so the capability is not new, only visible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountInviteService {

    /** Long enough to survive a weekend and a first day off. */
    public static final int INVITE_VALID_DAYS = 7;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    private static final String RESET_PATH = "/reset-password?token=";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Value("${app.base-url:}")
    private String baseUrl;

    @Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Mints the token on an account being created. Kept here rather than in
     * {@link EmployeeService} so that the invite issued on day one and the one
     * re-issued six months later are the same thing, with the same lifetime.
     */
    public void issueToken(User user) {
        user.setResetToken(UUID.randomUUID().toString().replace("-", ""));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusDays(INVITE_VALID_DAYS));
    }

    /** The event that asks for the invite to be emailed. Raised on create and on resend. */
    public NotificationRaised inviteEvent(Employee employee, User user) {
        return NotificationRaised.to(employee.getCompanyId(), "ACCOUNT_INVITE")
                .recipient(employee.getId())
                .about("User", user.getId())
                .with("employeeName", employee.fullName())
                .with("signInName", user.getUsername())
                .with("expiresAt", user.getResetTokenExpiresAt().format(WHEN))
                .with("validDays", INVITE_VALID_DAYS)
                .with("link", RESET_PATH + user.getResetToken())
                .build();
    }

    /**
     * Issues a fresh invite for somebody who never used theirs, or whose
     * seven days ran out. Any token already outstanding stops working.
     */
    @Transactional
    public InviteResponse resend(Long employeeId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        User user = employee.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This employee has no sign-in account to invite");
        }
        if (!employee.isActive() || !user.isActive() || user.getStatus() == UserStatus.INACTIVE) {
            // Handing a working link to somebody who has left is the one way
            // this endpoint could become a way back in.
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE,
                    "This account is deactivated. Reactivate the employee before inviting them.");
        }

        issueToken(user);
        userRepository.save(user);

        auditService.record(AuditModule.EMPLOYEE, AuditAction.PASSWORD_RESET, "User", user.getId(),
                null, employee.getEmployeeCode(), "Sign-in invite re-issued");
        events.publishEvent(inviteEvent(employee, user));
        log.info("Sign-in invite re-issued for {}", employee.getEmployeeCode());

        return response(employee, user);
    }

    public InviteResponse response(Employee employee, User user) {
        return new InviteResponse(
                employee.getId(),
                employee.fullName(),
                user.getUsername(),
                link(user.getResetToken()),
                user.getResetTokenExpiresAt(),
                emailEnabled);
    }

    private String link(String token) {
        String root = baseUrl == null ? "" : baseUrl.trim();
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root + RESET_PATH + token;
    }
}
