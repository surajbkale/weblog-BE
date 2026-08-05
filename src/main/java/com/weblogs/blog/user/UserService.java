package com.weblogs.blog.user;

import com.weblogs.blog.auth.dto.UserProfileResponse;
import com.weblogs.blog.exception.BadRequestException;
import com.weblogs.blog.exception.NotFoundException;
import com.weblogs.blog.user.dto.ChangePasswordRequest;
import com.weblogs.blog.user.dto.PublicProfileResponse;
import com.weblogs.blog.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── GET /me ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(User user) {
        long postCount = userRepository.countPublishedPostsByUserId(user.getId());
        return UserProfileResponse.from(user, postCount);
    }

    // ── PUT /me ──────────────────────────────────────────────────────────────

    @Transactional
    public UserProfileResponse updateProfile(User principal, UpdateProfileRequest req) {
        // The principal injected by JwtAuthFilter is a lightweight stub (no authProvider,
        // no passwordHash, etc.). Re-fetch the full entity from DB before writing.
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (StringUtils.hasText(req.displayName())) {
            user.setDisplayName(req.displayName().trim());
        }
        // bio and avatarUrl allow explicit clearing (empty string → null)
        if (req.bio() != null) {
            user.setBio(req.bio().isBlank() ? null : req.bio().trim());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl().isBlank() ? null : req.avatarUrl().trim());
        }

        User saved = userRepository.save(user);
        long postCount = userRepository.countPublishedPostsByUserId(saved.getId());
        return UserProfileResponse.from(saved, postCount);
    }

    // ── PUT /me/password ─────────────────────────────────────────────────────

    @Transactional
    public void changePassword(User principal, ChangePasswordRequest req) {
        // Same reason: re-fetch full entity so authProvider and passwordHash are available.
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new BadRequestException(
                    "Password change is not available for " + user.getAuthProvider().name() + " accounts");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadRequestException("New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    // ── GET /users/{id} ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        long postCount = userRepository.countPublishedPostsByUserId(userId);
        return PublicProfileResponse.from(user, postCount);
    }
}
