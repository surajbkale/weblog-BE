package com.weblogs.blog.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderIdAndAuthProvider(String providerId, AuthProvider authProvider);

    boolean existsByEmail(String email);

    @Query("""
            SELECT COUNT(p) FROM Post p
            WHERE p.author.id = :userId
              AND p.status    = com.weblogs.blog.post.PostStatus.PUBLISHED
              AND p.deleted   = false
            """)
    long countPublishedPostsByUserId(@Param("userId") UUID userId);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /**
     * Paginated user search for the admin panel.
     * Passing {@code null} for {@code q} returns all users ordered by creation date.
     * Non-null {@code q} filters on display name and email (case-insensitive).
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email)       LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchUsers(@Param("q") String q, Pageable pageable);
}
