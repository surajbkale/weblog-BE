package com.weblogs.blog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bulk-revoke all active refresh tokens for a user — used on compromise detection and password reset. */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllActiveByUserId(@Param("userId") UUID userId);
}
