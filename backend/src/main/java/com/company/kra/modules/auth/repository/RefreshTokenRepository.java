package com.company.kra.modules.auth.repository;

import com.company.kra.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = :now, r.revokedReason = :reason
            WHERE r.familyId = :familyId AND r.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("now") LocalDateTime now,
                     @Param("reason") String reason);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = :now, r.revokedReason = :reason
            WHERE r.userId = :userId AND r.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") LocalDateTime now,
                         @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
