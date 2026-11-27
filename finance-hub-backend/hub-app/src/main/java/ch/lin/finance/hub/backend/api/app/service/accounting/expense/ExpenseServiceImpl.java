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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.expense.ExpenseItemRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseDetailResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseResult;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.expense.ExpenseItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ExpenseService}.
 */
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    /**
     * Repository for managing {@link ExpenseItem} entities.
     */
    private final ExpenseItemRepository expenseItemRepository;
    private final OwnershipGuard ownershipGuard;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResult> searchExpenseItems(String keyword, List<String> sources, Boolean isEssential,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<ExpenseItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (sources != null && !sources.isEmpty()) {
                predicates.add(root.get("source").in(sources));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), endDate));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return expenseItemRepository.findAll(spec, Objects.requireNonNull(pageable)).map(ExpenseResult::fromEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableSources() {
        return expenseItemRepository.findDistinctSources();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ExpenseDetailResult getExpenseDetail(String source, String stringId) {
        if (source == null || stringId == null) {
            throw new IllegalArgumentException("Source and ID must not be null");
        }

        Long id;
        try {
            id = Long.valueOf(stringId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid expense ID: " + stringId, e);
        }

        switch (source) {
            case "AMAZON" -> {
                AmazonItem item = entityManager.find(AmazonItem.class, id);
                if (item == null || item.getOrder() == null) {
                    throw new ItemNotFoundException("Expense detail not found");
                }
                ownershipGuard.requireReadable(item.getOrder().getOwnerUserId(),
                        item.getOrder().getPrivacyLevel());
                return ExpenseDetailResult.fromAmazonItem(item);
            }
            case "RAKUTEN" -> {
                RakutenItem item = entityManager.find(RakutenItem.class, id);
                if (item == null || item.getOrder() == null) {
                    throw new ItemNotFoundException("Expense detail not found");
                }
                ownershipGuard.requireReadable(item.getOrder().getOwnerUserId(),
                        item.getOrder().getPrivacyLevel());
                return ExpenseDetailResult.fromRakutenItem(item);
            }
            case "SOFMAP" -> {
                SofmapItem item = entityManager.find(SofmapItem.class, id);
                if (item == null || item.getOrder() == null) {
                    throw new ItemNotFoundException("Expense detail not found");
                }
                ownershipGuard.requireReadable(item.getOrder().getOwnerUserId(),
                        item.getOrder().getPrivacyLevel());
                return ExpenseDetailResult.fromSofmapItem(item);
            }
            default -> throw new IllegalArgumentException("Unsupported expense source: " + source);
        }
    }
}
