package com.company.kra.modules.employee.dto;

import java.time.LocalDateTime;

/**
 * What an administrator needs after issuing a sign-in invite.
 *
 * <p>{@code emailed} says whether the invite was also sent by email, so the
 * screen can tell the difference between "they have it" and "you will need to
 * pass this on" rather than leaving the administrator to guess.</p>
 *
 * @param resetLink single-use and time-limited; it sets a password, it is not one
 */
public record InviteResponse(Long employeeId,
                             String employeeName,
                             String signInName,
                             String resetLink,
                             LocalDateTime expiresAt,
                             boolean emailed) {
}
