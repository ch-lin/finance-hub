/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.controller.accounting.category;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.category.CategoryService;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.category.dto.CategoryMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.category.dto.CategoryRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for managing transaction categories.
 */
@RestController
@RequestMapping("/transaction-categories")
@RequiredArgsConstructor
public class CategoryController {

    /** Service for handling category logic. */
    private final CategoryService categoryService;

    /** Mapper for converting between category DTOs and domain models. */
    private final CategoryMapper categoryMapper;

    /**
     * Retrieves all available transaction categories.
     *
     * @return A ResponseEntity containing a list of categories.
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories().stream()
                .map(result -> categoryMapper.toResponse(result, SecurityUtil.getCurrentUserId())).toList());
    }

    /**
     * Retrieves a specific transaction category by its ID.
     *
     * @param id The ID of the category.
     * @return A ResponseEntity containing the category details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity
                .ok(categoryMapper.toResponse(categoryService.getCategoryById(id), SecurityUtil.getCurrentUserId()));
    }

    /**
     * Creates a new transaction category.
     *
     * @param request The data for the new category.
     * @return A ResponseEntity containing the created category.
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        SaveCategoryCommand command = categoryMapper.toCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                categoryMapper.toResponse(categoryService.createCategory(command), SecurityUtil.getCurrentUserId()));
    }

    /**
     * Updates an existing transaction category.
     *
     * @param id      The ID of the category to update.
     * @param request The updated data for the category.
     * @return A ResponseEntity containing the updated category.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        SaveCategoryCommand command = categoryMapper.toCommand(request);
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.updateCategory(id, command),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Deletes a transaction category by its ID.
     *
     * @param id The ID of the category to delete.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
