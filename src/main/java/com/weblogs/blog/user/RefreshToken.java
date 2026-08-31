package com.weblogs.blog.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** FK to users.id — stored as a plain UUID column (no JPA join to avoid eager loads). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hex digest of the raw opaque token. The raw token is NEVER persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /**
     * Timestamp when this token was revoked (null if still active).
     * Used to distinguish a legitimate race condition (two simultaneous
     * refresh calls within a small window) from a true token-reuse attack.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
