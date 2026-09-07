package com.weblogs.blog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTypeAndUsedFalse(String tokenHash, AuthTokenType type);

    /**
     * Hard-deletes all auth tokens whose expiry timestamp is before {@code cutoff}.
     * Covers both used and unused expired tokens (unused expired tokens are useless
     * — the {@code expiresAt} check in the service layer rejects them anyway).
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
