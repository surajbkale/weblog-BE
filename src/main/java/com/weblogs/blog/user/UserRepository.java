package com.weblogs.blog.user;

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
}
