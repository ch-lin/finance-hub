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
package ch.lin.finance.hub.backend.api.app.service.accounting.expense.result;

import java.math.BigDecimal;
import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.domain.model.accounting.expense.ExpenseItem;
import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Result representing an individual expense item.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpenseResult extends BaseResult {

    private String id;
    private String source;
    private String name;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String categoryName;
    private Boolean isEssential;
    private LocalDate orderDate;

    /**
     * Converts an {@link ExpenseItem} entity to an {@link ExpenseResult}.
     *
     * @param entity the entity to convert
     * @return the corresponding ExpenseResult
     */
    public static ExpenseResult fromEntity(ExpenseItem entity) {
        if (entity == null) {
            return null;
        }

        ExpenseResult result = new ExpenseResult();
        result.setId(entity.getId());
        result.setSource(entity.getSource());
        result.setName(entity.getName());
        result.setUnitPrice(entity.getUnitPrice());
        result.setQuantity(entity.getQuantity());
        result.setCategoryName(entity.getCategoryName());
        result.setIsEssential(entity.getIsEssential());
        result.setOrderDate(entity.getOrderDate());

        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        return result;
    }
}
