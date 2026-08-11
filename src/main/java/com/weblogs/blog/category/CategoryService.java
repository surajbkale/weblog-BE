package com.weblogs.blog.category;

import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.category.dto.CategoryRequest;
import com.weblogs.blog.category.dto.CategoryResponse;
import com.weblogs.blog.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CacheService        cacheService;
    private final AppProperties       appProperties;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        List<CategoryResponse> cached =
                cacheService.<List<CategoryResponse>>get(CacheService.CATEGORIES_ALL)
                        .orElse(null);
        if (cached != null) {
            log.debug("Cache HIT: categories:all");
            return cached;
        }

        log.debug("Cache MISS: categories:all — loading from DB");
        List<CategoryResponse> result = categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();

        cacheService.put(CacheService.CATEGORIES_ALL, result,
                Duration.ofSeconds(appProperties.getCache().getCategoriesTtlSeconds()));
        return result;
    }

    /**
     * Creates a new category. Restricted to ADMIN role via {@code @PreAuthorize}.
     * Slug is auto-derived from the name (lowercase, spaces → hyphens).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = toSlug(request.name());
        Category category = Category.builder()
                .name(request.name().strip())
                .slug(slug)
                .build();
        CategoryResponse response = CategoryResponse.from(categoryRepository.save(category));
        cacheService.evict(CacheService.CATEGORIES_ALL);
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String toSlug(String name) {
        return name.strip()
                   .toLowerCase()
                   .replaceAll("[^a-z0-9\\s-]", "")
                   .replaceAll("[\\s]+", "-");
    }
}

