package com.weblogs.blog.comment;

import com.weblogs.blog.post.Post;
import com.weblogs.blog.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /**
     * Nullable UUID referencing the parent comment.
     * Self-referencing without a JPA association so that soft-deleting the parent
     * does not cascade to or break child comments (DB uses ON DELETE SET NULL).
     */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Soft-delete flag. Deleted comments are kept so replies don't become orphans;
     * the frontend renders them as "[deleted]".
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
