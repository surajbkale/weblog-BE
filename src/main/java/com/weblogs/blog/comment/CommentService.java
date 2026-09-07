package com.weblogs.blog.comment;

import com.weblogs.blog.comment.dto.CommentRequest;
import com.weblogs.blog.comment.dto.CommentResponse;
import com.weblogs.blog.common.AuthorizationHelper;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.post.PostRepository;
import com.weblogs.blog.post.PostStatus;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository   commentRepository;
    private final PostRepository      postRepository;
    private final AuthorizationHelper authorizationHelper;

    // ── Add comment ───────────────────────────────────────────────────────────

    @Transactional
    public CommentResponse addComment(UUID postId, CommentRequest request, User author) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
                .orElseThrow(() -> new EntityNotFoundException("Post not found or not published"));

        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .parentId(request.parentId())
                .content(request.content())
                .build();

        return CommentResponse.from(commentRepository.save(comment));
    }

    // ── Edit comment ──────────────────────────────────────────────────────────

    @Transactional
    public CommentResponse editComment(UUID commentId, CommentRequest request, User currentUser) {
        Comment comment = requireComment(commentId);
        // Only the comment author can edit their own comment
        authorizationHelper.requireOwnerOrAdmin(comment.getAuthor().getId(), currentUser);

        comment.setContent(request.content());
        return CommentResponse.from(commentRepository.save(comment));
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    /**
     * A comment can be soft-deleted by:
     * <ul>
     *   <li>The comment author</li>
     *   <li>The post author (moderating their own post)</li>
     *   <li>An ADMIN</li>
     * </ul>
     * The row is kept with {@code deleted=true} so that replies don't become orphans.
     */
    @Transactional
    public void deleteComment(UUID commentId, User currentUser) {
        Comment comment = requireComment(commentId);

        boolean isCommentAuthor = comment.getAuthor().getId().equals(currentUser.getId());
        boolean isPostAuthor    = comment.getPost().getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin         = Role.ADMIN.equals(currentUser.getRole());

        if (!isCommentAuthor && !isPostAuthor && !isAdmin) {
            throw new ForbiddenException("You do not have permission to delete this comment");
        }

        comment.setDeleted(true);
        commentRepository.save(comment);
    }

    // ── List comments for post ────────────────────────────────────────────────

    /**
     * Returns a flat paginated list of all comments (including soft-deleted ones so the
     * frontend can render "[deleted]" placeholders and keep reply threads intact).
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentResponse> listByPost(UUID postId, Pageable pageable) {
        postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        Page<Comment> page = commentRepository.findByPostId(postId, pageable);
        return PaginatedResponse.from(page.map(CommentResponse::from));
    }

    // ── List replies to a comment ─────────────────────────────────────────────

    /**
     * Returns a paginated list of direct replies to the given parent comment.
     * Soft-deleted replies are included so thread integrity is preserved on the frontend.
     * This is the companion to {@link #listByPost} for clients that want lazy-loaded threads.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<CommentResponse> listReplies(UUID parentId, Pageable pageable) {
        commentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        Page<Comment> page = commentRepository.findByParentId(parentId, pageable);
        return PaginatedResponse.from(page.map(CommentResponse::from));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Comment requireComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
    }
}
