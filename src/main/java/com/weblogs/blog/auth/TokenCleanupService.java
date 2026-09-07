package com.weblogs.blog.auth;

import com.weblogs.blog.user.AuthTokenRepository;
import com.weblogs.blog.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly scheduled purge of expired and revoked token rows.
 *
 * <h3>Why this is needed</h3>
 * <p>{@code auth_tokens} (email verification, password reset) and
 * {@code refresh_tokens} are never automatically deleted. Without periodic
 * cleanup they grow unboundedly — in a busy production system this translates
 * to hundreds of millions of rows over a few years, degrading index performance
 * and increasing backup sizes.
 *
 * <h3>Retention policy</h3>
 * <ul>
 *   <li><b>AuthToken</b>: any row whose {@code expires_at} is older than
 *       {@link #AUTH_TOKEN_GRACE} (7 days past expiry) is deleted. Expired tokens
 *       are already rejected by the service layer; keeping them 7 days gives a
 *       short audit window without burning disk space.</li>
 *   <li><b>RefreshToken</b>: rows whose {@code expires_at} is before
 *       {@link #REFRESH_TOKEN_GRACE} (7 days past expiry) are deleted.
 *       Active (non-expired) tokens are never touched regardless of their revoked
 *       state — a revoked-but-unexpired token may still be needed for the
 *       grace-window race-condition check in {@code AuthService.refresh}.</li>
 * </ul>
 *
 * <h3>Schedule</h3>
 * <p>Runs at 03:00 UTC daily — off-peak to minimise table-lock contention.
 * The cron expression can be overridden via {@code app.scheduling.token-cleanup-cron}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    /**
     * Delete auth tokens that expired more than this many days ago.
     * 7 days provides a short audit window while preventing unbounded growth.
     */
    private static final Duration AUTH_TOKEN_GRACE    = Duration.ofDays(7);

    /**
     * Delete refresh tokens that expired more than this many days ago.
     * Matching the 7-day grace keeps the two tables consistent.
     */
    private static final Duration REFRESH_TOKEN_GRACE = Duration.ofDays(7);

    private final AuthTokenRepository   authTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Purges expired token rows.
     *
     * <p>Runs at 03:00 UTC every day. Uses {@code @Transactional} so that
     * both deletes are committed atomically — if the second delete fails the
     * first is rolled back and the job will retry on the next scheduled run.
     *
     * <p>Each {@code @Query DELETE} issues a single {@code DELETE ... WHERE}
     * statement — no rows are loaded into the JPA first-level cache.
     */
    @Scheduled(cron = "0 0 3 * * *") // 03:00 UTC daily
    @Transactional
    public void purgeExpiredTokens() {
        Instant authCutoff    = Instant.now().minus(AUTH_TOKEN_GRACE);
        Instant refreshCutoff = Instant.now().minus(REFRESH_TOKEN_GRACE);

        int authDeleted    = authTokenRepository.deleteByExpiresAtBefore(authCutoff);
        int refreshDeleted = refreshTokenRepository.deleteByExpiresAtBefore(refreshCutoff);

        if (authDeleted > 0 || refreshDeleted > 0) {
            log.info("Token cleanup: deleted {} auth_tokens and {} refresh_tokens",
                    authDeleted, refreshDeleted);
        } else {
            log.debug("Token cleanup: no expired tokens found");
        }
    }
}
