package com.weblogs.blog.common;

import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reusable authorization check for "must be the resource owner or an ADMIN".
 *
 * <p>Usage:
 * <pre>
 *     authorizationHelper.requireOwnerOrAdmin(post.getAuthor().getId(), currentUser);
 * </pre>
 *
 * <p>Throws {@link ForbiddenException} (→ HTTP 403) if neither condition is met.
 * This keeps authorization logic out of individual service methods and in one place.
 */
@Component
public class AuthorizationHelper {

    /**
     * Asserts that {@code currentUser} is either the owner of the resource
     * (identified by {@code resourceOwnerId}) or has the {@code ADMIN} role.
     *
     * @param resourceOwnerId UUID of the user who owns the resource
     * @param currentUser     the currently authenticated user
     * @throws ForbiddenException if the user is neither the owner nor an ADMIN
     */
    public void requireOwnerOrAdmin(UUID resourceOwnerId, User currentUser) {
        boolean isOwner = resourceOwnerId.equals(currentUser.getId());
        boolean isAdmin = Role.ADMIN.equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }
}
