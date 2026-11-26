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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class AmazonOrderResult extends BaseResult {
    private final String orderId;
    private final LocalDate orderDate;
    private final BigDecimal totalAmount;
    private final BigDecimal billedAmount;
    private final BigDecimal discounts;
    private final BigDecimal pointsUsed;
    private final BigDecimal dPointsUsed;
    private final BigDecimal giftCardAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal refundAmount;
    private final List<AmazonItemResult> items;
    private final List<AmazonPaymentResult> payments;
    private final List<AmazonShipmentResult> shipments;

    public AmazonOrderResult(
            String orderId,
            LocalDate orderDate,
            BigDecimal totalAmount,
            BigDecimal billedAmount,
            BigDecimal discounts,
            BigDecimal pointsUsed,
            BigDecimal dPointsUsed,
            BigDecimal giftCardAmount,
            BigDecimal shippingFee,
            BigDecimal refundAmount,
            List<AmazonItemResult> items,
            List<AmazonPaymentResult> payments,
            List<AmazonShipmentResult> shipments) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.billedAmount = billedAmount;
        this.discounts = discounts;
        this.pointsUsed = pointsUsed;
        this.dPointsUsed = dPointsUsed;
        this.giftCardAmount = giftCardAmount;
        this.shippingFee = shippingFee;
        this.refundAmount = refundAmount;
        this.items = items;
        this.payments = payments;
        this.shipments = shipments;
    }

    public static AmazonOrderResult fromEntity(AmazonOrder entity) {
        if (entity == null) return null;

        List<AmazonItemResult> itemResults = entity.getItems() == null ? List.of() :
            entity.getItems().stream().map(AmazonItemResult::fromEntity).collect(Collectors.toList());

        List<AmazonPaymentResult> paymentResults = entity.getPayments() == null ? List.of() :
            entity.getPayments().stream().map(AmazonPaymentResult::fromEntity).collect(Collectors.toList());

        List<AmazonShipmentResult> shipmentResults = entity.getShipments() == null ? List.of() :
            entity.getShipments().stream().map(AmazonShipmentResult::fromEntity).collect(Collectors.toList());

        AmazonOrderResult result = new AmazonOrderResult(
                entity.getOrderId(),
                entity.getOrderDate(),
                entity.getTotalAmount(),
                entity.getBilledAmount(),
                entity.getDiscounts(),
                entity.getPointsUsed(),
                entity.getDPointsUsed(),
                entity.getGiftCardAmount(),
                entity.getShippingFee(),
                entity.getRefundAmount(),
                itemResults,
                paymentResults,
                shipmentResults
        );

        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        return result;
    }
}
