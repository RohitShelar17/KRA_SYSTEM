package com.company.kra.modules.worklog.repository;

import com.company.kra.modules.worklog.entity.WorkLogUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkLogUnlockRepository extends JpaRepository<WorkLogUnlock, Long> {

    /**
     * The question asked before every back-dated entry. Deliberately a boolean:
     * the entry path needs to know whether it may proceed, not which grant
     * allowed it.
     */
    @Query("""
            SELECT COUNT(u) > 0 FROM WorkLogUnlock u
            WHERE u.employeeId = :employeeId
              AND u.workDate = :workDate
              AND u.revokedAt IS NULL
              AND u.expiresAt > :now
            """)
    boolean isUnlocked(@Param("employeeId") Long employeeId,
                       @Param("workDate") LocalDate workDate,
                       @Param("now") LocalDateTime now);

    /** A live grant for this person and date, so a second request extends rather than stacks. */
    @Query("""
            SELECT u FROM WorkLogUnlock u
            WHERE u.employeeId = :employeeId
              AND u.workDate = :workDate
              AND u.revokedAt IS NULL
              AND u.expiresAt > :now
            ORDER BY u.expiresAt DESC
            LIMIT 1
            """)
    Optional<WorkLogUnlock> findLive(@Param("employeeId") Long employeeId,
                                     @Param("workDate") LocalDate workDate,
                                     @Param("now") LocalDateTime now);

    Optional<WorkLogUnlock> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * What is currently open, for the manager who granted it and for anybody
     * auditing the exceptions. Scope is applied by the caller, which is where
     * the access rules live.
     */
    @Query("""
            SELECT u FROM WorkLogUnlock u
            WHERE u.companyId = :companyId
              AND (:scopeAll = TRUE OR u.employeeId IN :scopeIds)
              AND (:liveOnly = FALSE OR (u.revokedAt IS NULL AND u.expiresAt > :now))
            ORDER BY u.workDate DESC, u.id DESC
            """)
    List<WorkLogUnlock> search(@Param("companyId") Long companyId,
                               @Param("scopeAll") boolean scopeAll,
                               @Param("scopeIds") java.util.Set<Long> scopeIds,
                               @Param("liveOnly") boolean liveOnly,
                               @Param("now") LocalDateTime now);
}
