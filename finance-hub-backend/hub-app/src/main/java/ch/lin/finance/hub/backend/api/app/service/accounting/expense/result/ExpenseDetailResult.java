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

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Result representing the details of a transaction for an expense item.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpenseDetailResult extends BaseResult {

    private String originalOrderId;
    private String seller;
    private BigDecimal orderTotal;
    private BigDecimal shippingFee;
    private BigDecimal pointsUsed;
    private BigDecimal discounts;

    public static ExpenseDetailResult fromAmazonItem(AmazonItem item) {
        if (item == null || item.getOrder() == null) {
            return null;
        }
        ExpenseDetailResult result = new ExpenseDetailResult();
        result.setOriginalOrderId(item.getOrder().getOrderId());
        result.setSeller(item.getSeller());
        result.setOrderTotal(item.getOrder().getTotalAmount());
        result.setShippingFee(item.getOrder().getShippingFee());
        result.setPointsUsed(item.getOrder().getPointsUsed());
        result.setDiscounts(item.getOrder().getDiscounts());
        result.setOwnerUserId(item.getOrder().getOwnerUserId());
        result.setPrivacyLevel(
                item.getPrivacyLevel() != null ? item.getPrivacyLevel() : item.getOrder().getPrivacyLevel());
        return result;
    }

    public static ExpenseDetailResult fromRakutenItem(RakutenItem item) {
        if (item == null || item.getOrder() == null) {
            return null;
        }
        ExpenseDetailResult result = new ExpenseDetailResult();
        result.setOriginalOrderId(item.getOrder().getOrderId());
        result.setSeller(item.getSeller());
        result.setOrderTotal(item.getOrder().getTotalAmount());
        result.setShippingFee(item.getOrder().getShippingFee());
        result.setPointsUsed(item.getOrder().getPointsUsed());
        result.setDiscounts(item.getOrder().getDiscounts());
        result.setOwnerUserId(item.getOrder().getOwnerUserId());
        result.setPrivacyLevel(
                item.getPrivacyLevel() != null ? item.getPrivacyLevel() : item.getOrder().getPrivacyLevel());
        return result;
    }

    public static ExpenseDetailResult fromSofmapItem(SofmapItem item) {
        if (item == null || item.getOrder() == null) {
            return null;
        }
        ExpenseDetailResult result = new ExpenseDetailResult();
        result.setOriginalOrderId(item.getOrder().getOrderId());
        result.setSeller("Sofmap");
        result.setOrderTotal(item.getOrder().getTotalAmount());
        result.setShippingFee(item.getOrder().getShippingFee());
        result.setPointsUsed(item.getOrder().getPointsUsed());
        result.setDiscounts(item.getOrder().getDiscounts());
        result.setOwnerUserId(item.getOrder().getOwnerUserId());
        result.setPrivacyLevel(
                item.getPrivacyLevel() != null ? item.getPrivacyLevel() : item.getOrder().getPrivacyLevel());
        return result;
    }
}
