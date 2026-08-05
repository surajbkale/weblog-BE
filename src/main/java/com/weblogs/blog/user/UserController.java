package com.weblogs.blog.user;

import com.weblogs.blog.auth.dto.UserProfileResponse;
import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.user.dto.ChangePasswordRequest;
import com.weblogs.blog.user.dto.PublicProfileResponse;
import com.weblogs.blog.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/v1/users/me — authenticated user's full profile */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(user)));
    }

    /** PUT /api/v1/users/me — update display name, bio, avatar URL */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(user, req)));
    }

    /** PUT /api/v1/users/me/password — change password (LOCAL accounts only) */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(user, req);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** GET /api/v1/users/{id} — public profile (authenticated) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicProfileResponse>> getPublicProfile(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getPublicProfile(id)));
    }
}
