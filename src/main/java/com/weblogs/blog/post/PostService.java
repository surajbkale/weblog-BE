package com.weblogs.blog.post;

import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.category.Category;
import com.weblogs.blog.category.CategoryRepository;
import com.weblogs.blog.common.AuthorizationHelper;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.like.LikeRepository;
import com.weblogs.blog.post.dto.*;
import com.weblogs.blog.tag.Tag;
import com.weblogs.blog.tag.TagRepository;
import com.weblogs.blog.tag.TagService;
import com.weblogs.blog.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository       postRepository;
    private final CategoryRepository   categoryRepository;
    private final TagRepository        tagRepository;
    private final TagService           tagService;
    private final LikeRepository       likeRepository;
    private final AuthorizationHelper  authorizationHelper;
    private final CacheService         cacheService;
    private final ViewCountService     viewCountService;
    private final AppProperties        appProperties;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public PostResponse createPost(CreatePostRequest request, User author) {
        String slug = generateUniqueSlug(request.title());

        Set<Category> categories = resolveCategories(request.categoryIds());
        Set<Tag>      tags       = resolveOrCreateTags(request.tagNames());

        Post post = Post.builder()
                .title(request.title())
                .slug(slug)
                .content(request.content())
                .excerpt(request.excerpt())
                .coverImageUrl(request.coverImageUrl())
                .status(PostStatus.DRAFT)
                .author(author)
                .categories(categories)
                .tags(tags)
                .build();

        post = postRepository.save(post);

        // A new draft doesn't appear in the public list, but evict anyway so
        // subsequent publishes get a clean slate.
        cacheService.evictAllPostListCaches();

        return toFullResponse(post, author);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public PostResponse updatePost(UUID postId, UpdatePostRequest request, User currentUser) {
        Post post = requirePost(postId);
        authorizationHelper.requireOwnerOrAdmin(post.getAuthor().getId(), currentUser);

        if (request.title() != null && !request.title().isBlank()) {
            // Re-slug only if title changed
            if (!request.title().equals(post.getTitle())) {
                post.setSlug(generateUniqueSlug(request.title()));
            }
            post.setTitle(request.title());
        }
        if (request.content()       != null) post.setContent(request.content());
        if (request.excerpt()       != null) post.setExcerpt(request.excerpt());
        if (request.coverImageUrl() != null) post.setCoverImageUrl(request.coverImageUrl());
        if (request.categoryIds()   != null) post.setCategories(resolveCategories(request.categoryIds()));
        if (request.tagNames()      != null) post.setTags(resolveOrCreateTags(request.tagNames()));

        post = postRepository.save(post);
        evictPostCaches(post.getSlug());

        return toFullResponse(post, currentUser);
    }

    // ── Publish / Unpublish ───────────────────────────────────────────────────

    @Transactional
    public PostResponse publishPost(UUID postId, User currentUser) {
        Post post = requirePost(postId);
        authorizationHelper.requireOwnerOrAdmin(post.getAuthor().getId(), currentUser);

        post.setStatus(PostStatus.PUBLISHED);
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(Instant.now());
        }
        post = postRepository.save(post);
        evictPostCaches(post.getSlug());

        return toFullResponse(post, currentUser);
    }

    @Transactional
    public PostResponse unpublishPost(UUID postId, User currentUser) {
        Post post = requirePost(postId);
        authorizationHelper.requireOwnerOrAdmin(post.getAuthor().getId(), currentUser);

        post.setStatus(PostStatus.DRAFT);
        post = postRepository.save(post);
        evictPostCaches(post.getSlug());

        return toFullResponse(post, currentUser);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Transactional
    public void deletePost(UUID postId, User currentUser) {
        Post post = requirePost(postId);
        authorizationHelper.requireOwnerOrAdmin(post.getAuthor().getId(), currentUser);

        post.setDeleted(true);
        postRepository.save(post);
        evictPostCaches(post.getSlug());
    }

    // ── Public list ───────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of published, non-deleted posts.
     * Supports filtering by category slug, tag slug, authorId, and full-text query.
     * Sort: {@code mostLiked} → like count DESC, default → publishedAt DESC.
     *
     * <p>Caching: anonymous requests are served from Redis (TTL 5 min).
     * Authenticated requests bypass the cache so {@code likedByCurrentUser}
     * is always accurate.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<PostListItemResponse> getPublicList(
            String categorySlug,
            String tagSlug,
            UUID   authorId,
            String q,
            String sort,
            Pageable pageable,
            User   currentUser   // nullable — null when unauthenticated
    ) {
        // ── 1. Normalize inputs first ─────────────────────────────────────────
        // Pass null for empty q so the SQL `:q IS NULL` branch fires correctly.
        String normalizedQ = (q == null || q.isBlank()) ? null : q.strip();

        // Normalize sort: frontend sends 'popular', SQL expects 'mostLiked'.
        // Auto-switch to 'relevance' when a search query is present.
        String normalizedSort = switch (sort == null ? "newest" : sort.toLowerCase()) {
            case "popular", "mostliked" -> "mostLiked";
            case "relevance"            -> "relevance";
            default                     -> (normalizedQ != null) ? "relevance" : "newest";
        };

        // ── 2. Cache lookup (anonymous only, after normalization) ─────────────
        boolean canUseCache = (currentUser == null && normalizedQ == null); // never cache search results
        String cacheKey = null;

        if (canUseCache) {
            cacheKey = buildListCacheKey(categorySlug, tagSlug, authorId, normalizedQ, normalizedSort, pageable);
            Optional<PaginatedResponse<PostListItemResponse>> cached =
                    cacheService.get(cacheKey);
            if (cached.isPresent()) {
                log.debug("Cache HIT: {}", cacheKey);
                return cached.get();
            }
            log.debug("Cache MISS: {}", cacheKey);
        }

        // ── 3. Query ──────────────────────────────────────────────────────────
        Page<Post> page = postRepository.findPublished(
                categorySlug,
                tagSlug,
                authorId != null ? authorId.toString() : null,
                normalizedQ,
                normalizedSort,
                pageable
        );

        PaginatedResponse<PostListItemResponse> result =
                PaginatedResponse.from(page.map(post -> toListItemResponse(post, currentUser)));

        if (canUseCache && cacheKey != null) {
            cacheService.putListCache(cacheKey, result,
                    Duration.ofSeconds(appProperties.getCache().getPostListTtlSeconds()));
        }

        return result;
    }

    // ── Single post by slug ───────────────────────────────────────────────────

    /**
     * Returns the post if it is published. If it is a draft, only the author may view it;
     * anyone else gets a 404 (no 403 — don't leak draft existence).
     *
     * <p>Caching: published posts are cached by slug (TTL 10 min) with
     * {@code likedByCurrentUser = false}. On cache hit, the liked flag is patched via
     * a single DB lookup if the caller is authenticated.
     */
    @Transactional(readOnly = true)
    public PostResponse getBySlug(String slug, User currentUser) {
        // Try cache first — only for published posts (drafts are never cached)
        String cacheKey = CacheService.POST_SLUG_PREFIX + slug;
        Optional<PostResponse> cached = cacheService.get(cacheKey);

        if (cached.isPresent()) {
            log.debug("Cache HIT: {}", cacheKey);
            PostResponse hit = cached.get();
            // Increment view regardless of cache status
            viewCountService.increment(UUID.fromString(hit.id().toString()));
            // Patch the user-specific liked flag
            boolean liked = currentUser != null
                    && likeRepository.existsByPostIdAndUserId(hit.id(), currentUser.getId());
            if (liked != hit.likedByCurrentUser()) {
                // Return a new record instance with corrected liked state
                return new PostResponse(hit.id(), hit.title(), hit.slug(), hit.content(),
                        hit.excerpt(), hit.coverImageUrl(), hit.status(), hit.author(),
                        hit.categories(), hit.tags(), hit.likeCount(), hit.commentCount(),
                        hit.viewCount(), liked, hit.readingTimeMinutes(),
                        hit.publishedAt(), hit.createdAt());
            }
            return hit;
        }

        log.debug("Cache MISS: {}", cacheKey);
        Post post = postRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        if (post.getStatus() == PostStatus.DRAFT) {
            // Draft: only the author may see it — everyone else gets a 404
            boolean isAuthor = currentUser != null
                    && currentUser.getId().equals(post.getAuthor().getId());
            if (!isAuthor) {
                throw new EntityNotFoundException("Post not found");
            }
            // Don't cache drafts
            return toFullResponse(post, currentUser);
        }

        // Published — increment view and cache with liked=false
        viewCountService.increment(post.getId());
        PostResponse base = toFullResponse(post, null); // liked=false for cache
        cacheService.put(cacheKey, base,
                Duration.ofSeconds(appProperties.getCache().getPostBySlugTtlSeconds()));

        // Patch liked for the current caller if authenticated
        if (currentUser != null) {
            boolean liked = likeRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId());
            if (liked) {
                return new PostResponse(base.id(), base.title(), base.slug(), base.content(),
                        base.excerpt(), base.coverImageUrl(), base.status(), base.author(),
                        base.categories(), base.tags(), base.likeCount(), base.commentCount(),
                        base.viewCount(), true, base.readingTimeMinutes(),
                        base.publishedAt(), base.createdAt());
            }
        }
        return base;
    }

    // ── My posts ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaginatedResponse<PostListItemResponse> getMyPosts(User currentUser, Pageable pageable) {
        Page<Post> page = postRepository.findByAuthorId(currentUser.getId(), pageable);
        return PaginatedResponse.from(page.map(post -> toListItemResponse(post, currentUser)));
    }

    // ── Trending ─────────────────────────────────────────────────────

    /**
     * Returns the top-N posts by view count published within the configured lookback window.
     *
     * <p>Result is cached in Redis for {@code app.cache.trending-ttl-seconds}.
     * The cache is evicted automatically on any post mutation (publish, delete, feature).
     */
    @Transactional(readOnly = true)
    public List<PostListItemResponse> getTrending() {
        Optional<List<PostListItemResponse>> cached = cacheService.get(CacheService.TRENDING_POSTS);
        if (cached.isPresent()) {
            log.debug("Cache HIT: {}", CacheService.TRENDING_POSTS);
            return cached.get();
        }
        log.debug("Cache MISS: {}", CacheService.TRENDING_POSTS);

        int windowDays = appProperties.getCache().getTrendingWindowDays();
        int limit      = appProperties.getCache().getTrendingLimit();
        Instant since  = Instant.now().minus(Duration.ofDays(windowDays));

        List<PostListItemResponse> result = postRepository
                .findTrending(since, PageRequest.of(0, limit))
                .stream()
                .map(post -> toListItemResponse(post, null))
                .toList();

        cacheService.put(CacheService.TRENDING_POSTS, result,
                Duration.ofSeconds(appProperties.getCache().getTrendingTtlSeconds()));
        return result;
    }

    // ── Featured ────────────────────────────────────────────────────

    /**
     * Returns the admin-curated featured post list.
     *
     * <p>Result is cached in Redis for {@code app.cache.featured-ttl-seconds}.
     * The cache is evicted when a post is featured/unfeatured, published, or deleted.
     */
    @Transactional(readOnly = true)
    public List<PostListItemResponse> getFeatured() {
        Optional<List<PostListItemResponse>> cached = cacheService.get(CacheService.FEATURED_POSTS);
        if (cached.isPresent()) {
            log.debug("Cache HIT: {}", CacheService.FEATURED_POSTS);
            return cached.get();
        }
        log.debug("Cache MISS: {}", CacheService.FEATURED_POSTS);

        int limit = appProperties.getCache().getFeaturedLimit();
        List<PostListItemResponse> result = postRepository
                .findFeatured(PageRequest.of(0, limit))
                .stream()
                .map(post -> toListItemResponse(post, null))
                .toList();

        cacheService.put(CacheService.FEATURED_POSTS, result,
                Duration.ofSeconds(appProperties.getCache().getFeaturedTtlSeconds()));
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Post requirePost(UUID postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
    }

    /** Evicts slug cache + entire list cache + trending + featured. Called on every post mutation. */
    private void evictPostCaches(String slug) {
        cacheService.evict(CacheService.POST_SLUG_PREFIX + slug);
        cacheService.evictAllPostListCaches();
        cacheService.evict(CacheService.TRENDING_POSTS);
        cacheService.evict(CacheService.FEATURED_POSTS);
    }

    /**
     * Builds a deterministic Redis key for a post list query.
     * Nulls are replaced with "_" so the key is always well-formed.
     */
    private String buildListCacheKey(String category, String tag, UUID authorId,
                                     String q, String sort, Pageable pageable) {
        return CacheService.POST_LIST_PREFIX
                + nvl(category) + ":"
                + nvl(tag) + ":"
                + nvl(authorId) + ":"
                + nvl(q) + ":"
                + nvl(sort) + ":"
                + pageable.getPageNumber() + ":"
                + pageable.getPageSize();
    }

    private static String nvl(Object value) {
        return value == null ? "_" : value.toString().replace(":", "-");
    }

    /**
     * Generates a URL-safe slug from the title.
     * On collision appends {@code -2}, {@code -3}, ... until unique.
     */
    String generateUniqueSlug(String title) {
        String base = title.strip()
                           .toLowerCase()
                           .replaceAll("[^a-z0-9\\s-]", "")
                           .replaceAll("[\\s]+", "-");
        // Trim trailing hyphens
        base = base.replaceAll("-+$", "");

        if (!postRepository.existsBySlug(base)) {
            return base;
        }
        // Strip any existing numeric suffix before appending
        Pattern suffixPattern = Pattern.compile("^(.+)-\\d+$");
        var matcher = suffixPattern.matcher(base);
        String root = matcher.matches() ? matcher.group(1) : base;

        int counter = 2;
        String candidate;
        do {
            candidate = root + "-" + counter++;
        } while (postRepository.existsBySlug(candidate));
        return candidate;
    }

    private Set<Category> resolveCategories(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return new LinkedHashSet<>();
        Set<Category> found = new LinkedHashSet<>(categoryRepository.findAllById(categoryIds));
        if (found.size() != categoryIds.size()) {
            throw new EntityNotFoundException("One or more categories not found");
        }
        return found;
    }

    /**
     * Resolves tag names to Tag entities. If a tag doesn't exist yet, it is created
     * automatically. This is intentional: tags are free-form (unlike categories).
     * When a new tag is persisted, the {@code tags:all} cache is evicted so that
     * the next list request reflects the newly created tag.
     */
    private Set<Tag> resolveOrCreateTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return new LinkedHashSet<>();
        Set<Tag> result = new LinkedHashSet<>();
        boolean newTagCreated = false;
        for (String rawName : tagNames) {
            String name = rawName.strip();
            boolean existed = tagRepository.findByName(name).isPresent();
            Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                String slug = name.toLowerCase()
                        .replaceAll("[^a-z0-9\\s-]", "")
                        .replaceAll("[\\s]+", "-");
                return tagRepository.save(Tag.builder().name(name).slug(slug).build());
            });
            if (!existed) {
                newTagCreated = true;
            }
            result.add(tag);
        }
        if (newTagCreated) {
            tagService.evictTagCache();
        }
        return result;
    }

    private PostResponse toFullResponse(Post post, User currentUser) {
        long likeCount    = postRepository.countLikesByPostId(post.getId());
        long commentCount = postRepository.countCommentsByPostId(post.getId());
        boolean liked     = currentUser != null
                && likeRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId());
        return PostResponse.from(post, likeCount, commentCount, liked);
    }

    private PostListItemResponse toListItemResponse(Post post, User currentUser) {
        long likeCount    = postRepository.countLikesByPostId(post.getId());
        long commentCount = postRepository.countCommentsByPostId(post.getId());
        boolean liked     = currentUser != null
                && likeRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId());
        return PostListItemResponse.from(post, likeCount, commentCount, liked);
    }
}

