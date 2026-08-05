package com.weblogs.blog.category;

import com.weblogs.blog.category.dto.CategoryRequest;
import com.weblogs.blog.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
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
        return CategoryResponse.from(categoryRepository.save(category));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String toSlug(String name) {
        return name.strip()
                   .toLowerCase()
                   .replaceAll("[^a-z0-9\\s-]", "")
                   .replaceAll("[\\s]+", "-");
    }
}
