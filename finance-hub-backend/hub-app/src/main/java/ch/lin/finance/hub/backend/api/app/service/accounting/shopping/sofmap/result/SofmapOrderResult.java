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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import lombok.Getter;

@Getter
public class SofmapOrderResult extends BaseResult {
    private final String orderId;
    private final LocalDate orderDate;
    private final BigDecimal totalAmount;
    private final BigDecimal billedAmount;
    private final BigDecimal discounts;
    private final BigDecimal pointsUsed;
    private final BigDecimal earnedPoints;
    private final BigDecimal shippingFee;
    private final BigDecimal refundAmount;

    private final List<SofmapItemResult> items;
    private final List<SofmapPaymentResult> payments;
    private final List<SofmapShipmentResult> shipments;

    public SofmapOrderResult(
            String orderId,
            LocalDate orderDate,
            BigDecimal totalAmount,
            BigDecimal billedAmount,
            BigDecimal discounts,
            BigDecimal pointsUsed,
            BigDecimal earnedPoints,
            BigDecimal shippingFee,
            BigDecimal refundAmount,
            List<SofmapItemResult> items,
            List<SofmapPaymentResult> payments,
            List<SofmapShipmentResult> shipments) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.billedAmount = billedAmount;
        this.discounts = discounts;
        this.pointsUsed = pointsUsed;
        this.earnedPoints = earnedPoints;
        this.shippingFee = shippingFee;
        this.refundAmount = refundAmount;
        this.items = items;
        this.payments = payments;
        this.shipments = shipments;
    }

    public static SofmapOrderResult fromEntity(SofmapOrder entity) {
        if (entity == null)
            return null;

        List<SofmapItemResult> itemResults = entity.getItems() == null ? null
                : entity.getItems().stream().map(SofmapItemResult::fromEntity).collect(Collectors.toList());

        List<SofmapPaymentResult> paymentResults = entity.getPayments() == null ? null
                : entity.getPayments().stream().map(SofmapPaymentResult::fromEntity).collect(Collectors.toList());

        List<SofmapShipmentResult> shipmentResults = entity.getShipments() == null ? null
                : entity.getShipments().stream().map(SofmapShipmentResult::fromEntity).collect(Collectors.toList());

        SofmapOrderResult result = new SofmapOrderResult(
                entity.getOrderId(),
                entity.getOrderDate(),
                entity.getTotalAmount(),
                entity.getBilledAmount(),
                entity.getDiscounts(),
                entity.getPointsUsed(),
                entity.getEarnedPoints(),
                entity.getShippingFee(),
                entity.getRefundAmount(),
                itemResults,
                paymentResults,
                shipmentResults);

        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        return result;
    }
}
