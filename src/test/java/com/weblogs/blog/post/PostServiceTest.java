package com.weblogs.blog.post;

import com.weblogs.blog.category.CategoryRepository;
import com.weblogs.blog.common.AuthorizationHelper;
import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.like.LikeRepository;
import com.weblogs.blog.post.dto.CreatePostRequest;
import com.weblogs.blog.post.dto.UpdatePostRequest;
import com.weblogs.blog.tag.TagRepository;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository      postRepository;
    @Mock CategoryRepository  categoryRepository;
    @Mock TagRepository       tagRepository;
    @Mock LikeRepository      likeRepository;
    @Mock AuthorizationHelper authorizationHelper; // real implementation tested via integration test

    @InjectMocks PostService postService;

    private User author;
    private User otherUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        author = User.builder().id(UUID.randomUUID()).role(Role.USER)
                .displayName("Author").email("author@test.com").build();
        otherUser = User.builder().id(UUID.randomUUID()).role(Role.USER)
                .displayName("Other").email("other@test.com").build();
        adminUser = User.builder().id(UUID.randomUUID()).role(Role.ADMIN)
                .displayName("Admin").email("admin@test.com").build();
    }

    // ── createPost: always creates as DRAFT ───────────────────────────────────

    @Test
    void createPost_setsStatusToDraft() {
        CreatePostRequest req = new CreatePostRequest(
                "My First Post", "Content here", null, null, null, null);

        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(postRepository.countLikesByPostId(any())).thenReturn(0L);
        when(postRepository.countCommentsByPostId(any())).thenReturn(0L);
        when(likeRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false);

        var response = postService.createPost(req, author);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.publishedAt()).isNull();
    }

    // ── publishPost: sets publishedAt ─────────────────────────────────────────

    @Test
    void publishPost_setsPublishedAtAndStatus() {
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .title("Draft Post")
                .slug("draft-post")
                .content("Content")
                .status(PostStatus.DRAFT)
                .author(author)
                .build();

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.countLikesByPostId(any())).thenReturn(0L);
        when(postRepository.countCommentsByPostId(any())).thenReturn(0L);
        when(likeRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false);

        var response = postService.publishPost(post.getId(), author);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.publishedAt()).isBefore(Instant.now().plusSeconds(1));
    }

    // ── Non-author update is rejected ─────────────────────────────────────────

    @Test
    void updatePost_byNonAuthor_throwsForbiddenException() {
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .title("Post")
                .slug("post")
                .content("Content")
                .status(PostStatus.DRAFT)
                .author(author)
                .build();

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        // Use real AuthorizationHelper for this scenario
        doThrow(new ForbiddenException("You do not have permission to perform this action"))
                .when(authorizationHelper).requireOwnerOrAdmin(author.getId(), otherUser);

        UpdatePostRequest req = new UpdatePostRequest("New Title", null, null, null, null, null);

        assertThatThrownBy(() -> postService.updatePost(post.getId(), req, otherUser))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Non-author delete is rejected ─────────────────────────────────────────

    @Test
    void deletePost_byNonAuthor_throwsForbiddenException() {
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .title("Post")
                .slug("post")
                .content("Content")
                .status(PostStatus.DRAFT)
                .author(author)
                .build();

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        doThrow(new ForbiddenException("You do not have permission to perform this action"))
                .when(authorizationHelper).requireOwnerOrAdmin(author.getId(), otherUser);

        assertThatThrownBy(() -> postService.deletePost(post.getId(), otherUser))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── ADMIN can delete another user's post ──────────────────────────────────

    @Test
    void deletePost_byAdmin_succeeds() {
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .title("Post")
                .slug("post")
                .content("Content")
                .status(PostStatus.PUBLISHED)
                .author(author)
                .build();

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        // authorizationHelper does NOT throw for ADMIN — no stubbing needed (default = do nothing)
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> postService.deletePost(post.getId(), adminUser))
                .doesNotThrowAnyException();

        verify(postRepository).save(argThat(Post::isDeleted));
    }

    // ── Slug collision gets numeric suffix ────────────────────────────────────

    @Test
    void generateUniqueSlug_onCollision_appendsSuffix() {
        // "my-post" taken, "my-post-2" taken, "my-post-3" free
        when(postRepository.existsBySlug("my-post")).thenReturn(true);
        when(postRepository.existsBySlug("my-post-2")).thenReturn(true);
        when(postRepository.existsBySlug("my-post-3")).thenReturn(false);

        String slug = postService.generateUniqueSlug("My Post");

        assertThat(slug).isEqualTo("my-post-3");
    }

    @Test
    void generateUniqueSlug_noCollision_returnsBase() {
        when(postRepository.existsBySlug("hello-world")).thenReturn(false);

        assertThat(postService.generateUniqueSlug("Hello World")).isEqualTo("hello-world");
    }
}
