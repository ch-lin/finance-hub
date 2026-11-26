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
package ch.lin.finance.hub.backend.api.app.service.accounting.expense;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseDetailResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseResult;

/**
 * Service interface for managing expenses.
 */
public interface ExpenseService {

    /**
     * Gets a paginated list of all expenses across all sources.
     *
     * @param keyword     the search keyword
     * @param sources     list of sources to filter by
     * @param isEssential whether to filter by essential status
     * @param startDate   start date to filter by (inclusive)
     * @param endDate     end date to filter by (inclusive)
     * @param pageable    the pagination information
     * @return a page of ExpenseResult
     */
    Page<ExpenseResult> searchExpenseItems(String keyword, List<String> sources, Boolean isEssential,
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Retrieves all distinct sources available.
     *
     * @return a list of source strings
     */
    List<String> getAvailableSources();

    /**
     * Retrieves detailed order information for a specific expense item.
     * 
     * @param source the source name
     * @param id     the unique identifier
     * @return the detail result
     */
    ExpenseDetailResult getExpenseDetail(String source, String id);
}
