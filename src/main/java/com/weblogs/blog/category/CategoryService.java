package com.weblogs.blog.category;

import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.category.dto.CategoryRequest;
import com.weblogs.blog.category.dto.CategoryResponse;
import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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

    /**
     * Renames an existing category. Slug is re-derived from the new name.
     * Restricted to ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        category.setName(request.name().strip());
        category.setSlug(toSlug(request.name()));
        CategoryResponse response = CategoryResponse.from(categoryRepository.save(category));
        cacheService.evict(CacheService.CATEGORIES_ALL);
        return response;
    }

    /**
     * Deletes a category by ID. Posts that reference this category will have it removed
     * from their category set automatically (JOIN TABLE row deleted by DB CASCADE).
     * Restricted to ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new NotFoundException("Category not found");
        }
        categoryRepository.deleteById(id);
        cacheService.evict(CacheService.CATEGORIES_ALL);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    static String toSlug(String name) {
        return name.strip()
                   .toLowerCase()
                   .replaceAll("[^a-z0-9\\s-]", "")
                   .replaceAll("[\\s]+", "-");
    }
}
