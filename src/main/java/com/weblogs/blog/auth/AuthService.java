package com.weblogs.blog.auth;

import com.weblogs.blog.auth.dto.*;
import com.weblogs.blog.exception.EmailNotVerifiedException;
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
            log.warn("SECURITY: Revoked refresh token reused for userId={}. Revoking all sessions.",
                    storedToken.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(storedToken.getUserId());
            clearRefreshCookie(response);
            throw new InvalidTokenException(
                    "Token reuse detected. All sessions have been invalidated for security. Please log in again.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            clearRefreshCookie(response);
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        // Revoke old token
        storedToken.setRevoked(true);
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
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                // NOTE: Set secure=true in production (requires HTTPS).
                // For local dev over HTTP, set to false in application-local.yml.
                .secure(true)
                // SameSite=Lax works when frontend and backend share the same registrable
                // domain.
                // Change to SameSite=None (+ Secure=true) if they are on genuinely different
                // origins in prod.
                .sameSite("Lax")
                .path("/api/v1/auth") // Scoped — cookie only sent to auth endpoints
                .maxAge(REFRESH_TOKEN_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
