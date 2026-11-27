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
package ch.lin.finance.hub.backend.api.controller.accounting.expense;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Sort.Direction;

import ch.lin.finance.hub.backend.api.app.service.accounting.expense.ExpenseService;
import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseDetailResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.expense.dto.ExpenseDetailResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.expense.dto.ExpenseMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.expense.dto.ExpenseResponse;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for retrieving aggregated expense records across all sources.
 */
@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    /** Service to fetch and transform expense data. */
    private final ExpenseService expenseService;
    private final ExpenseMapper expenseMapper;

    /**
     * Retrieves a paginated list of all aggregated expenses.
     *
     * @param sources     Optional list of sources to filter by.
     * @param keyword     Optional keyword to filter by name or category.
     * @param isEssential Optional boolean to filter by essential status.
     * @param startDate   Optional start date for filtering.
     * @param endDate     Optional end date for filtering.
     * @param pageable    The pagination and sorting information.
     * @return A paginated list of {@link ExpenseResponse} representing aggregated
     *         expenses.
     */
    @GetMapping
    public Page<ExpenseResponse> getAllExpenses(
            @RequestParam(required = false) List<String> sources,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isEssential,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "orderDate", direction = Direction.DESC) Pageable pageable) {
        return expenseService.searchExpenseItems(keyword, sources, isEssential, startDate, endDate, pageable)
                .map(result -> expenseMapper.toResponse(result, SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all available distinct sources.
     *
     * @return A list of source strings.
     */
    @GetMapping("/available-sources")
    public List<String> getAvailableSources() {
        return expenseService.getAvailableSources();
    }

    /**
     * Retrieves detailed order information for a specific expense item.
     * 
     * @param id The unified expense ID (e.g., AMAZON_123).
     * @return A {@link ExpenseDetailResponse} containing order details.
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<ExpenseDetailResponse> getExpenseDetails(@PathVariable String id) {

        if (id == null || !id.contains("_")) {
            return ResponseEntity.badRequest().build();
        }

        String[] parts = id.split("_", 2);
        String source = parts[0];
        String stringId = parts[1];

        ExpenseDetailResult details = expenseService.getExpenseDetail(source, stringId);
        return ResponseEntity.ok(expenseMapper.toDetailResponse(details, SecurityUtil.getCurrentUserId()));
    }
}
