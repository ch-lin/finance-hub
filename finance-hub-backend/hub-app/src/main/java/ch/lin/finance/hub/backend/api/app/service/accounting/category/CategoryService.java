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

import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.result.TransactionCategoryResult;

/**
 * Service interface for managing transaction categories.
 */
public interface CategoryService {

    /**
     * Retrieves all transaction categories.
     *
     * @return a list of all categories as result objects
     */
    List<TransactionCategoryResult> getAllCategories();

    /**
     * Retrieves a transaction category by its ID.
     *
     * @param id the ID of the category
     * @return the category result object
     */
    TransactionCategoryResult getCategoryById(Long id);

    /**
     * Creates a new transaction category.
     *
     * @param command the request containing the category details
     * @return the created category result object
     */
    TransactionCategoryResult createCategory(SaveCategoryCommand command);

    /**
     * Updates an existing transaction category.
     *
     * @param id      the ID of the category to update
     * @param command the request containing the updated category details
     * @return the updated category result object
     */
    TransactionCategoryResult updateCategory(Long id, SaveCategoryCommand command);

    /**
     * Deletes a transaction category by its ID.
     *
     * @param id the ID of the category to delete
     */
    void deleteCategory(Long id);
}
