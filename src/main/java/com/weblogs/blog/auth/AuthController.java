package com.weblogs.blog.auth;

import com.weblogs.blog.auth.dto.*;
import com.weblogs.blog.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String COOKIE_NAME = "refresh_token";

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        String message = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully. You can now log in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.ok("If an unverified account with that email exists, a new verification email has been sent."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.ok(loginResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.refresh(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok(loginResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.ok("If an account with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. Please log in with your new password."));
    }
}
