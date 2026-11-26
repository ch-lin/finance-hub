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
package ch.lin.finance.hub.backend.api.app.service.accounting.category;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.category.TransactionCategoryRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.result.TransactionCategoryResult;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CategoryService} for managing transaction
 * categories.
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /**
     * Repository for managing {@link TransactionCategory} entities.
     */
    private final TransactionCategoryRepository categoryRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<TransactionCategoryResult> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(TransactionCategoryResult::fromEntity)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public TransactionCategoryResult getCategoryById(Long id) {
        TransactionCategory category = categoryRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ItemNotFoundException("Category not found with id: " + id));
        return TransactionCategoryResult.fromEntity(category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TransactionCategoryResult createCategory(SaveCategoryCommand command) {
        TransactionCategory parent = null;
        if (command.parentId() != null) {
            parent = categoryRepository.findById(Objects.requireNonNull(command.parentId()))
                    .orElseThrow(
                            () -> new ItemNotFoundException(
                                    "Parent category not found with id: " + command.parentId()));
        }

        TransactionCategory category = new TransactionCategory(
                command.name(),
                Objects.requireNonNull(command.isEssential(), "isEssential is required"),
                Objects.requireNonNull(command.isSimulationDuplicate(), "isSimulationDuplicate is required"),
                parent);

        return TransactionCategoryResult.fromEntity(categoryRepository.save(category));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TransactionCategoryResult updateCategory(Long id, SaveCategoryCommand command) {
        TransactionCategory category = categoryRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ItemNotFoundException("Category not found with id: " + id));

        TransactionCategory parent = null;
        if (command.parentId() != null) {
            parent = categoryRepository.findById(Objects.requireNonNull(command.parentId()))
                    .orElseThrow(
                            () -> new ItemNotFoundException(
                                    "Parent category not found with id: " + command.parentId()));
        }

        category.setName(command.name());
        category.setEssential(Objects.requireNonNull(command.isEssential(), "isEssential is required"));
        category.setSimulationDuplicate(
                Objects.requireNonNull(command.isSimulationDuplicate(), "isSimulationDuplicate is required"));
        category.setParent(parent);

        return TransactionCategoryResult.fromEntity(categoryRepository.save(category));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(Objects.requireNonNull(id))) {
            throw new ItemNotFoundException("Category not found");
        }
        categoryRepository.deleteById(Objects.requireNonNull(id));
    }
}
