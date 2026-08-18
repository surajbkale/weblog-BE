package com.weblogs.blog.like;

import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.post.PostRepository;
import com.weblogs.blog.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CacheService   cacheService;

    /**
     * Likes a post. Idempotent — liking an already-liked post is a no-op, not an error.
     * Evicts the per-slug and trending caches so likeCount is never served stale.
     */
    @Transactional
    public void likePost(UUID postId, User user) {
        Post post = requirePublishedPost(postId);
        if (!likeRepository.existsByPostIdAndUserId(postId, user.getId())) {
            Like like = Like.builder().post(post).user(user).build();
            likeRepository.save(like);
            evictPostCaches(post.getSlug());
        }
    }

    /**
     * Unlikes a post. No-op if not currently liked.
     * Evicts the per-slug and trending caches so likeCount is never served stale.
     */
    @Transactional
    public void unlikePost(UUID postId, User user) {
        Post post = requirePublishedPost(postId);
        likeRepository.deleteByPostIdAndUserId(postId, user.getId());
        evictPostCaches(post.getSlug());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Post requirePublishedPost(UUID postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
    }

    /**
     * Evicts the per-slug cache and the trending list so that the next request
     * reflects the updated like count.  The list cache is also evicted because
     * PostListItemResponse carries likeCount and the 'mostLiked' sort depends on it.
     */
    private void evictPostCaches(String slug) {
        cacheService.evict(CacheService.POST_SLUG_PREFIX + slug);
        cacheService.evictAllPostListCaches();
        cacheService.evict(CacheService.TRENDING_POSTS);
    }
}
