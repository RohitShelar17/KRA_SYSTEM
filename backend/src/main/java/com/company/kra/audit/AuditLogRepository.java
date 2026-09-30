package com.company.kra.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Insert and read only.
 *
 * <p>It extends the bare {@code Repository} rather than {@code JpaRepository}
 * on purpose: the audit trail is evidence, and inheriting {@code delete} and
 * {@code deleteAll} would put a deletion path one autocomplete away from
 * existing. Retention is an operational archive, not an application feature.</p>
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);

    Optional<AuditLog> findById(Long id);

    long count();

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:module IS NULL OR a.module = :module)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("module") String module,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("entityId") Long entityId,
                          @Param("actorUserId") Long actorUserId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
