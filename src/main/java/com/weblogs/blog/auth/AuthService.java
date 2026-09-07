package com.weblogs.blog.auth;

import com.weblogs.blog.auth.dto.*;
import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.exception.EmailNotVerifiedException;
import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.exception.InvalidTokenException;
import com.weblogs.blog.exception.RateLimitExceededException;
import com.weblogs.blog.security.JwtService;
import com.weblogs.blog.user.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final MailService mailService;
    private final AppProperties appProperties;
    private final CacheService cacheService;
    private final SecureRandom secureRandom = new SecureRandom();

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public String register(RegisterRequest request) {
        String generic = "Registration successful. Please check your email to verify your account.";

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration attempted for already-registered email (suppressed): {}", request.email());
            return generic; // no-op — don't reveal whether the email exists
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .role(Role.USER)
                .build();
        user = userRepository.save(user);

        // Evict cached admin stats so totalUsers reflects the new registration immediately
        cacheService.evict(CacheService.ADMIN_STATS);

        sendAuthToken(user, AuthTokenType.EMAIL_VERIFICATION, VERIFICATION_TOKEN_TTL);
        return generic;
    }

    // ── Verify email ──────────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = findValidAuthToken(rawToken, AuthTokenType.EMAIL_VERIFICATION);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Token references a non-existent user"));

        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        authTokenRepository.save(token);
    }

    // ── Resend verification ───────────────────────────────────────────────────

    @Transactional
    public void resendVerification(String email) {
        // Rate-limit by email to prevent mail-bombing and enumeration via timing.
        // Uses the same Redis-backed limiter as the login endpoint.
        if (!rateLimiter.isAllowed(email)) {
            throw new RateLimitExceededException("Too many requests. Please try again later.");
        }

        // Always returns generic success — no enumeration
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent() && !userOpt.get().isEmailVerified()) {
            sendAuthToken(userOpt.get(), AuthTokenType.EMAIL_VERIFICATION, VERIFICATION_TOKEN_TTL);
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        // Rate limit first
        if (!rateLimiter.isAllowed(request.email())) {
            long retryAfter = rateLimiter.getTtlSeconds(request.email());
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again in " + retryAfter + " seconds.");
        }

        User user = userRepository.findByEmail(request.email())
                .filter(u -> u.getAuthProvider() == AuthProvider.LOCAL)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidTokenException("Invalid email or password"));

        // Reject suspended accounts before issuing any token.
        // The JwtAuthFilter blocks suspended users on subsequent requests via the
        // `active` claim, but without this check a suspended user could obtain a
        // fresh access token by simply hitting the login endpoint again.
        if (!user.isActive()) {
            throw new ForbiddenException(
                    "Your account has been suspended. Please contact support.");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email address before logging in. Check your inbox or request a new verification email.");
        }

        issueAndSetRefreshCookie(user, response);
        String accessToken = jwtService.generateAccessToken(user);
        return LoginResponse.of(accessToken, jwtService.getAccessTokenExpirySeconds());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse refresh(String rawCookieToken, HttpServletResponse response) {
        if (rawCookieToken == null || rawCookieToken.isBlank()) {
            throw new InvalidTokenException("Refresh token missing");
        }

        String hash = TokenHashUtil.sha256Hex(rawCookieToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // ── Reuse detection ───────────────────────────────────────────────────
        if (storedToken.isRevoked()) {
            // Grace window: if the token was revoked within the last 10 seconds,
            // treat it as a benign race condition (React Strict Mode double-invoke,
            // fast page reload, slow network) rather than a real attack.
            // Real reuse attacks happen minutes or hours after revocation.
            boolean isLikelyRace = storedToken.getRevokedAt() != null &&
                    storedToken.getRevokedAt().isAfter(Instant.now().minusSeconds(10));

            if (isLikelyRace) {
                // Silent 401 — the first call already issued a new cookie.
                // The frontend's isRefreshing guard should prevent this path,
                // but this is the safety net for slow networks.
                log.debug("Refresh token race-condition detected (within grace window) for userId={}. Rejecting silently.",
                        storedToken.getUserId());
                throw new InvalidTokenException("Refresh token already used. Please try again.");
            }

            // Revoked OUTSIDE the grace window → real reuse attack → nuke all sessions.
            log.warn("SECURITY: Revoked refresh token reused for userId={}. Revoking all sessions.",
                    storedToken.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(storedToken.getUserId());
            clearRefreshCookie(response);
            throw new InvalidTokenException(
                    "Token reuse detected. All sessions have been invalidated for security. Please log in again.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(storedToken);
            clearRefreshCookie(response);
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        // Revoke old token (stamp revokedAt for the grace-window check above)
        storedToken.setRevoked(true);
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        // Issue new token (rotation)
        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        issueAndSetRefreshCookie(user, response);
        String accessToken = jwtService.generateAccessToken(user);
        return LoginResponse.of(accessToken, jwtService.getAccessTokenExpirySeconds());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String rawCookieToken, HttpServletResponse response) {
        if (rawCookieToken != null && !rawCookieToken.isBlank()) {
            String hash = TokenHashUtil.sha256Hex(rawCookieToken);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        clearRefreshCookie(response);
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(String email) {
        // Rate-limit by email to prevent mail-bombing and enumeration via timing.
        // Same limiter as login — a separate rate-limit config block (app.rate-limit.reset)
        // can be added later if different thresholds are required.
        if (!rateLimiter.isAllowed(email)) {
            throw new RateLimitExceededException("Too many requests. Please try again later.");
        }

        // Always generic response — no enumeration
        userRepository.findByEmail(email)
                .filter(u -> u.getAuthProvider() == AuthProvider.LOCAL)
                .ifPresent(user -> sendAuthToken(user, AuthTokenType.PASSWORD_RESET, RESET_TOKEN_TTL));
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AuthToken token = findValidAuthToken(request.token(), AuthTokenType.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Token references a non-existent user"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        authTokenRepository.save(token);

        // Revoke ALL sessions — force re-login on all devices after password change
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(user.getId());
        log.info("Password reset for userId={}. Revoked {} active sessions.", user.getId(), revoked);
    }

    // ── Public cookie helpers (used by OAuth2SuccessHandler) ─────────────────

    public void issueAndSetRefreshCookie(User user, HttpServletResponse response) {
        String rawToken = generateSecureToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHashUtil.sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
                .build();
        refreshTokenRepository.save(refreshToken);
        setRefreshCookie(response, rawToken);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuthToken findValidAuthToken(String rawToken, AuthTokenType type) {
        String hash = TokenHashUtil.sha256Hex(rawToken);
        AuthToken token = authTokenRepository.findByTokenHashAndTypeAndUsedFalse(hash, type)
                .orElseThrow(() -> new InvalidTokenException("Invalid or already-used token"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Token has expired. Please request a new one.");
        }
        return token;
    }

    private void sendAuthToken(User user, AuthTokenType type, Duration ttl) {
        String rawToken = generateSecureToken();
        AuthToken authToken = AuthToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHashUtil.sha256Hex(rawToken))
                .type(type)
                .expiresAt(Instant.now().plus(ttl))
                .build();
        authTokenRepository.save(authToken);

        if (type == AuthTokenType.EMAIL_VERIFICATION) {
            mailService.sendVerificationEmail(user.getEmail(), rawToken);
        } else {
            mailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32]; // 256-bit token
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        boolean secureCookies = appProperties.getCookies().isSecure();

        // SameSite=None is required when the frontend and backend are on different
        // subdomains (e.g. weblog.lumenvault.live → weblogapi.lumenvault.live).
        // SameSite=Lax only works for same-host origins (localhost dev).
        // SameSite=None REQUIRES Secure=true — set COOKIES_SECURE=true in .env.prod.
        String sameSite = secureCookies ? "None" : "Lax";

        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(REFRESH_TOKEN_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Readable hint cookie — retained for same-origin dev environments.
        // In cross-subdomain production the frontend uses localStorage instead
        // (document.cookie cannot read cookies set by a different subdomain).
        ResponseCookie hint = ResponseCookie.from("session_hint", "1")
                .httpOnly(false)              // must be JS-readable
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/")
                .maxAge(REFRESH_TOKEN_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, hint.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        boolean secureCookies = appProperties.getCookies().isSecure();
        String sameSite = secureCookies ? "None" : "Lax";

        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Clear the hint cookie on logout
        ResponseCookie hint = ResponseCookie.from("session_hint", "")
                .httpOnly(false)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, hint.toString());
    }
}
