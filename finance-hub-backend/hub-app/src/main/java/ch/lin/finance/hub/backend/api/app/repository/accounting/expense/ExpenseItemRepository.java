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
package ch.lin.finance.hub.backend.api.app.repository.accounting.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.expense.ExpenseItem;

/**
 * Repository for querying the unified ExpenseItem view.
 * <p>
 * Note: ExpenseItem is read-only (@Subselect). Do not use save() or delete().
 */
@Repository
public interface ExpenseItemRepository
        extends JpaRepository<ExpenseItem, String>, JpaSpecificationExecutor<ExpenseItem> {

    /**
     * Retrieves all distinct sources available in the unified view.
     *
     * @return a list of distinct source strings
     */
    @Query("SELECT DISTINCT e.source FROM ExpenseItem e")
    java.util.List<String> findDistinctSources();
}
