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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import lombok.Getter;

@Getter
public class RakutenOrderResult extends BaseResult {
    private final String orderId;
    private final LocalDate orderDate;
    private final BigDecimal totalAmount;
    private final BigDecimal billedAmount;
    private final BigDecimal discounts;
    private final BigDecimal pointsUsed;
    private final BigDecimal taxAmount;
    private final String shopId;
    private final BigDecimal shippingFee;
    private final BigDecimal refundAmount;
    private final List<RakutenItemResult> items;
    private final List<RakutenPaymentResult> payments;
    private final List<RakutenShipmentResult> shipments;

    public RakutenOrderResult(
            String orderId,
            LocalDate orderDate,
            BigDecimal totalAmount,
            BigDecimal billedAmount,
            BigDecimal discounts,
            BigDecimal pointsUsed,
            BigDecimal taxAmount,
            String shopId,
            BigDecimal shippingFee,
            BigDecimal refundAmount,
            List<RakutenItemResult> items,
            List<RakutenPaymentResult> payments,
            List<RakutenShipmentResult> shipments) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.billedAmount = billedAmount;
        this.discounts = discounts;
        this.pointsUsed = pointsUsed;
        this.taxAmount = taxAmount;
        this.shopId = shopId;
        this.shippingFee = shippingFee;
        this.refundAmount = refundAmount;
        this.items = items;
        this.payments = payments;
        this.shipments = shipments;
    }

    public static RakutenOrderResult fromEntity(RakutenOrder entity) {
        if (entity == null)
            return null;

        List<RakutenItemResult> itemResults = entity.getItems() == null ? null
                : entity.getItems().stream().map(RakutenItemResult::fromEntity).collect(Collectors.toList());

        List<RakutenPaymentResult> paymentResults = entity.getPayments() == null ? null
                : entity.getPayments().stream().map(RakutenPaymentResult::fromEntity).collect(Collectors.toList());

        List<RakutenShipmentResult> shipmentResults = entity.getShipments() == null ? null
                : entity.getShipments().stream().map(RakutenShipmentResult::fromEntity).collect(Collectors.toList());

        RakutenOrderResult result = new RakutenOrderResult(
                entity.getOrderId(),
                entity.getOrderDate(),
                entity.getTotalAmount(),
                entity.getBilledAmount(),
                entity.getDiscounts(),
                entity.getPointsUsed(),
                entity.getTaxAmount(),
                entity.getShopId(),
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
