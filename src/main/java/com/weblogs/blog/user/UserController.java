package com.weblogs.blog.user;

import com.weblogs.blog.auth.dto.UserProfileResponse;
import com.weblogs.blog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(user)));
    }
}
