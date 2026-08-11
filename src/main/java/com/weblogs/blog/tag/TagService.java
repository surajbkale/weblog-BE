package com.weblogs.blog.tag;

import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Service layer for tags.
 * Wraps {@link TagRepository} with a Redis cache so the full tag list
 * is only read from Postgres once per TTL window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final CacheService  cacheService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<TagResponse> listAll() {
        // Cache hit?
        List<TagResponse> cached = cacheService.<List<TagResponse>>get(CacheService.TAGS_ALL)
                .orElse(null);
        if (cached != null) {
            log.debug("Cache HIT: tags:all");
            return cached;
        }

        log.debug("Cache MISS: tags:all — loading from DB");
        List<TagResponse> result = tagRepository.findAll().stream()
                .map(TagResponse::from)
                .toList();

        cacheService.put(CacheService.TAGS_ALL, result,
                Duration.ofSeconds(appProperties.getCache().getTagsTtlSeconds()));
        return result;
    }

    /** Evicts the tag cache — call this whenever tags are created or deleted. */
    public void evictTagCache() {
        cacheService.evict(CacheService.TAGS_ALL);
    }
}
