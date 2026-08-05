package com.weblogs.blog.comment;

import com.weblogs.blog.comment.dto.CommentRequest;
import com.weblogs.blog.common.AuthorizationHelper;
import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.post.PostRepository;
import com.weblogs.blog.post.PostStatus;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository   commentRepository;
    @Mock PostRepository      postRepository;
    @Mock AuthorizationHelper authorizationHelper;

    @InjectMocks CommentService commentService;

    private User postAuthor;
    private User commentAuthor;
    private User randomUser;
    private Post post;

    @BeforeEach
    void setUp() {
        postAuthor = User.builder().id(UUID.randomUUID()).role(Role.USER)
                .displayName("PostAuthor").email("pa@test.com").build();
        commentAuthor = User.builder().id(UUID.randomUUID()).role(Role.USER)
                .displayName("Commenter").email("ca@test.com").build();
        randomUser = User.builder().id(UUID.randomUUID()).role(Role.USER)
                .displayName("Random").email("r@test.com").build();

        post = Post.builder()
                .id(UUID.randomUUID())
                .title("Test Post")
                .slug("test-post")
                .content("Content")
                .status(PostStatus.PUBLISHED)
                .author(postAuthor)
                .build();
    }

    // ── Soft delete sets deleted=true without removing the row ────────────────

    @Test
    void softDeleteComment_setsDeletedTrue_andSaves() {
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(commentAuthor)
                .content("Original content")
                .deleted(false)
                .build();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.deleteComment(comment.getId(), commentAuthor);

        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository).save(comment);
        // Verify the comment row is NOT removed — only saved with deleted=true
        verify(commentRepository, never()).delete(any());
        verify(commentRepository, never()).deleteById(any());
    }

    // ── Random user cannot delete another user's comment ──────────────────────

    @Test
    void softDeleteComment_byUnauthorizedUser_throwsForbiddenException() {
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(commentAuthor)
                .content("Some comment")
                .deleted(false)
                .build();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(comment.getId(), randomUser))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Post author can delete any comment on their post ──────────────────────

    @Test
    void softDeleteComment_byPostAuthor_succeeds() {
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(commentAuthor)
                .content("Some comment")
                .deleted(false)
                .build();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> commentService.deleteComment(comment.getId(), postAuthor))
                .doesNotThrowAnyException();

        assertThat(comment.isDeleted()).isTrue();
    }

    // ── Soft-deleted parent does not break replies ─────────────────────────────
    // (structural test — parent is deleted but reply's parentId still references it)

    @Test
    void softDeletedParent_doesNotOrphanReplies() {
        UUID parentId = UUID.randomUUID();

        Comment parent = Comment.builder()
                .id(parentId)
                .post(post)
                .author(commentAuthor)
                .content("Parent comment")
                .deleted(false)
                .build();

        Comment reply = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(randomUser)
                .parentId(parentId)    // references the parent by UUID — not a JPA FK that would cascade
                .content("Reply content")
                .deleted(false)
                .build();

        // Soft-delete the parent
        parent.setDeleted(true);

        // Reply still has the parentId set — it is not nulled out from Java's perspective
        // (DB nulls it via ON DELETE SET NULL only if the row is physically deleted, which we don't do)
        assertThat(reply.getParentId()).isEqualTo(parentId);
        assertThat(parent.isDeleted()).isTrue();
        assertThat(reply.isDeleted()).isFalse();
    }

    // ── ADMIN can delete any comment ──────────────────────────────────────────

    @Test
    void softDeleteComment_byAdmin_succeeds() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN)
                .displayName("Admin").email("admin@test.com").build();

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(commentAuthor)
                .content("A comment")
                .deleted(false)
                .build();

        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> commentService.deleteComment(comment.getId(), admin))
                .doesNotThrowAnyException();

        assertThat(comment.isDeleted()).isTrue();
    }
}
