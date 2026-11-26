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
package ch.lin.finance.hub.backend.api.controller.accounting.expense.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseDetailResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.expense.result.ExpenseResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    ExpenseResponse toResponse(ExpenseResult result, @Context String currentUserId);

    ExpenseDetailResponse toDetailResponse(ExpenseDetailResult result, @Context String currentUserId);

    @AfterMapping
    default void applyExpenseMasking(ExpenseResult result, @MappingTarget ExpenseResponse response,
            @Context String currentUserId) {
        if (result != null
                && PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setUnitPrice(null);
            response.setQuantity(null);

            String source = result.getSource();
            PrivacyMaskingUtil.MaskingType type = null;
            if (source != null) {
                try {
                    type = PrivacyMaskingUtil.MaskingType.valueOf(source.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Ignore, keep type null to use generic Masked Data
                }
            }
            response.setName(PrivacyMaskingUtil.getMaskedName(type));
        }
    }

    @AfterMapping
    default void applyExpenseDetailMasking(ExpenseDetailResult result, @MappingTarget ExpenseDetailResponse response,
            @Context String currentUserId) {
        if (result != null
                && PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setOriginalOrderId(null);
            response.setSeller(" ");
            response.setOrderTotal(null);
            response.setShippingFee(null);
            response.setPointsUsed(null);
            response.setDiscounts(null);
        }
    }
}
