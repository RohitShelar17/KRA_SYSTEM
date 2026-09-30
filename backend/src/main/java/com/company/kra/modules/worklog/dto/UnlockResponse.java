package com.company.kra.modules.worklog.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One back-dating exception, as the manager who granted it sees it.
 *
 * @param live false once it has been revoked or has run out of time
 */
public record UnlockResponse(Long id,
                             Long employeeId,
                             String employeeName,
                             String employeeCode,
                             LocalDate workDate,
                             String reason,
                             String grantedByName,
                             LocalDateTime grantedAt,
                             LocalDateTime expiresAt,
                             LocalDateTime revokedAt,
                             boolean live) {
}
