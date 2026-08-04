package com.weblogs.blog.auth;

import com.weblogs.blog.auth.dto.LoginRequest;
import com.weblogs.blog.auth.dto.LoginResponse;
import com.weblogs.blog.auth.dto.RegisterRequest;
import com.weblogs.blog.auth.dto.ResetPasswordRequest;
import com.weblogs.blog.exception.EmailNotVerifiedException;
import com.weblogs.blog.exception.InvalidTokenException;
import com.weblogs.blog.exception.RateLimitExceededException;
import com.weblogs.blog.security.JwtService;
import com.weblogs.blog.user.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository        userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthTokenRepository   authTokenRepository;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private JwtService            jwtService;
    @Mock private RateLimiter           rateLimiter;
    @Mock private MailService           mailService;
    @Mock private HttpServletResponse   httpResponse;

    @InjectMocks
    private AuthService authService;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        verifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("$2a$12$hashed")
                .displayName("Test User")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .role(Role.USER)
                .build();

        unverifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("unverified@example.com")
                .passwordHash("$2a$12$hashed")
                .displayName("Unverified User")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .role(Role.USER)
                .build();
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Test
    void register_newEmail_createsUserAndSendsEmail() {
        RegisterRequest req = new RegisterRequest("new@example.com", "P@ssword1", "New User");
        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(authTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = authService.register(req);

        assertThat(result).contains("Registration successful");
        verify(userRepository).save(any(User.class));
        verify(mailService).sendVerificationEmail(eq(req.email()), anyString());
    }

    @Test
    void register_duplicateEmail_returnsGenericMessageWithoutCreatingUser() {
        RegisterRequest req = new RegisterRequest("existing@example.com", "P@ssword1", "User");
        when(userRepository.existsByEmail(req.email())).thenReturn(true);

        String result = authService.register(req);

        assertThat(result).contains("Registration successful"); // generic — no leak
        verify(userRepository, never()).save(any());
        verify(mailService, never()).sendVerificationEmail(any(), any());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsAccessToken() {
        LoginRequest req = new LoginRequest("test@example.com", "P@ssword1");
        when(rateLimiter.isAllowed(req.email())).thenReturn(true);
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(verifiedUser));
        when(passwordEncoder.matches(req.password(), verifiedUser.getPasswordHash())).thenReturn(true);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(verifiedUser)).thenReturn("access.token.jwt");
        when(jwtService.getAccessTokenExpirySeconds()).thenReturn(900L);

        LoginResponse response = authService.login(req, httpResponse);

        assertThat(response.accessToken()).isEqualTo("access.token.jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_wrongPassword_throwsInvalidTokenException() {
        LoginRequest req = new LoginRequest("test@example.com", "WrongPass1!");
        when(rateLimiter.isAllowed(req.email())).thenReturn(true);
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(verifiedUser));
        when(passwordEncoder.matches(req.password(), verifiedUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, httpResponse))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_unverifiedEmail_throwsEmailNotVerifiedException() {
        LoginRequest req = new LoginRequest("unverified@example.com", "P@ssword1");
        when(rateLimiter.isAllowed(req.email())).thenReturn(true);
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(unverifiedUser));
        when(passwordEncoder.matches(req.password(), unverifiedUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req, httpResponse))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_rateLimited_throwsRateLimitExceededException() {
        LoginRequest req = new LoginRequest("test@example.com", "P@ssword1");
        when(rateLimiter.isAllowed(req.email())).thenReturn(false);
        when(rateLimiter.getTtlSeconds(req.email())).thenReturn(45L);

        assertThatThrownBy(() -> authService.login(req, httpResponse))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("45 seconds");
    }

    // ── Refresh token rotation ────────────────────────────────────────────────

    @Test
    void refresh_validToken_rotatesTokenAndReturnsNewAccessToken() {
        String rawToken = "validRawToken";
        String hash = TokenHashUtil.sha256Hex(rawToken);
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(verifiedUser.getId())
                .tokenHash(hash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(verifiedUser.getId())).thenReturn(Optional.of(verifiedUser));
        when(jwtService.generateAccessToken(verifiedUser)).thenReturn("new.access.token");
        when(jwtService.getAccessTokenExpirySeconds()).thenReturn(900L);

        LoginResponse result = authService.refresh(rawToken, httpResponse);

        assertThat(result.accessToken()).isEqualTo("new.access.token");
        // Verify old token was revoked
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(RefreshToken::isRevoked);
    }

    @Test
    void refresh_revokedToken_revokesAllSessionsAndThrows() {
        String rawToken = "revokedRawToken";
        String hash = TokenHashUtil.sha256Hex(rawToken);
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(verifiedUser.getId())
                .tokenHash(hash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)  // already revoked — reuse detected!
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, httpResponse))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Token reuse detected");

        verify(refreshTokenRepository).revokeAllActiveByUserId(verifiedUser.getId());
    }

    @Test
    void refresh_expiredToken_throwsInvalidTokenException() {
        String rawToken = "expiredToken";
        String hash = TokenHashUtil.sha256Hex(rawToken);
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(verifiedUser.getId())
                .tokenHash(hash)
                .expiresAt(Instant.now().minusSeconds(3600)) // already expired
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> authService.refresh(rawToken, httpResponse))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }
}
