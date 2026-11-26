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
package ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result;

import java.math.BigDecimal;
import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReconciliationAmazonOrderResult extends BaseResult {
    private Long id;
    private String orderId;
    private LocalDate orderDate;
    private BigDecimal totalAmount;
    private BigDecimal billedAmount;
    private BigDecimal shippingFee;
    private BigDecimal refundAmount;
    private BigDecimal discounts;
    private BigDecimal pointsUsed;
    private Boolean needsReview;
    private String reviewReason;
    private BigDecimal reconciledAmount;
    private BigDecimal giftCardAmount;
    private BigDecimal dPointsUsed;

    public static ReconciliationAmazonOrderResult fromEntity(AmazonOrder entity) {
        if (entity == null) {
            return null;
        }

        ReconciliationAmazonOrderResult result = new ReconciliationAmazonOrderResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        result.setId(entity.getId());
        result.setOrderId(entity.getOrderId());
        result.setOrderDate(entity.getOrderDate());
        result.setTotalAmount(entity.getTotalAmount());
        result.setBilledAmount(entity.getBilledAmount());
        result.setShippingFee(entity.getShippingFee());
        result.setRefundAmount(entity.getRefundAmount());
        result.setDiscounts(entity.getDiscounts());
        result.setPointsUsed(entity.getPointsUsed());
        result.setNeedsReview(entity.getNeedsReview());
        result.setReviewReason(entity.getReviewReason());
        result.setReconciledAmount(entity.getReconciledAmount());
        result.setGiftCardAmount(entity.getGiftCardAmount());
        result.setDPointsUsed(entity.getDPointsUsed());

        return result;
    }
}
