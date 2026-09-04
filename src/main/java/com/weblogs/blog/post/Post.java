package com.weblogs.blog.post;

import com.weblogs.blog.category.Category;
import com.weblogs.blog.tag.Tag;
import com.weblogs.blog.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
/*
 * Index documentation — all indexes below are created by Flyway migrations.
 * This @Table(indexes) block is for developer reference only; Hibernate DDL
 * is disabled (spring.jpa.hibernate.ddl-auto=none) so these annotations do
 * NOT modify the schema. Indexes that cannot be expressed in JPA @Index
 * (GIN, partial) are noted in comments below.
 *
 *   idx_posts_search_vector  — GIN index on search_vector (V2) — Flyway only
 *   idx_posts_featured       — partial index WHERE featured = TRUE (V5) — Flyway only
 */
@Table(name = "posts", indexes = {
        // V2 single-column indexes
        @Index(name = "idx_posts_author_id",    columnList = "author_id"),
        @Index(name = "idx_posts_status",        columnList = "status"),
        @Index(name = "idx_posts_deleted",       columnList = "deleted"),
        @Index(name = "idx_posts_published_at",  columnList = "published_at DESC"),
        // V9 compound index — covers the dominant WHERE status=? AND deleted=? pattern
        @Index(name = "idx_posts_status_deleted", columnList = "status, deleted"),
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, unique = true, length = 600)
    private String slug;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 1000)
    private String excerpt;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_categories",
            joinColumns        = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_tags",
            joinColumns        = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Managed by the {@code trg_posts_search_vector} DB trigger — not writable from JPA.
     * {@code insertable=false, updatable=false} ensures Hibernate never touches this column.
     */
    @Column(name = "search_vector", insertable = false, updatable = false,
            columnDefinition = "tsvector")
    private String searchVector;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
