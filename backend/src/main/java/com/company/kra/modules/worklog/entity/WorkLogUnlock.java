package com.company.kra.modules.worklog.entity;

import com.company.kra.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Permission for one employee to file one date that the back-dating window
 * would otherwise refuse.
 *
 * <p>Time-boxed rather than single-use: a day is several entries, so a
 * one-shot grant would be spent on the first of them. It expires so a
 * forgotten grant does not become a permanent hole in the rule, and it can be
 * revoked before then.</p>
 *
 * <p>The granting manager's name is copied onto the row rather than joined at
 * read time. The row is the record of who allowed what, and it should still
 * read correctly after that person leaves and their employee row is
 * deactivated.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "work_log_unlocks")
public class WorkLogUnlock extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "granted_by_id")
    private Long grantedById;

    @Column(name = "granted_by_name", length = 150)
    private String grantedByName;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    /** Live means not revoked and not yet expired. */
    public boolean isLive(LocalDateTime at) {
        return revokedAt == null && expiresAt.isAfter(at);
    }
}
