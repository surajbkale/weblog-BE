package com.weblogs.blog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bulk-revoke all active refresh tokens for a user — used on compromise detection and password reset. */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllActiveByUserId(@Param("userId") UUID userId);

    /**
     * Hard-deletes all revoked refresh tokens whose expiry is before {@code cutoff}.
     *
     * <p>Only revoked tokens are purged — active tokens must be kept regardless of age
     * so that long-lived sessions remain valid. Expired-but-not-revoked tokens will be
     * rejected naturally by the expiry check in {@code AuthService.refresh}, but cleaning
     * them prevents unbounded table growth.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
