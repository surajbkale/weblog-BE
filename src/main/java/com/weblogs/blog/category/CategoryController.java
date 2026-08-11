package com.weblogs.blog.category;

import com.weblogs.blog.category.dto.CategoryRequest;
import com.weblogs.blog.category.dto.CategoryResponse;
import com.weblogs.blog.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** Public — no auth required. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listAll()));
    }

    /** ADMIN only — enforced in CategoryService via @PreAuthorize. */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(categoryService.create(request)));
    }

    /** ADMIN only — renames an existing category. Slug is re-derived automatically. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(id, request)));
    }

    /** ADMIN only — permanently deletes a category (not reversible). */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
