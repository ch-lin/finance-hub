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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object representing a complete Amazon order
 * payload received from the Chrome Extension.
 *
 * @param orderId The unique identifier for the Amazon order.
 * @param orderDate The date the order was placed.
 * @param totalAmount The total amount of the order before any discounts or points are applied.
 * @param billedAmount The final amount billed to the customer after applying discounts and points.
 * @param discounts Total discounts applied to the order.
 * @param amazonPointsUsed The amount paid using Amazon Points.
 * @param dPointsUsed The amount paid using dPoints.
 * @param giftCardAmount The amount paid using an Amazon Gift Card.
 * @param shippingFee The shipping fee charged for the order.
 * @param refundAmount Any refund amount associated with the order.
 * @param items The list of items included in this order.
 * @param payments The list of payment methods used for this order.
 * @param shipments The list of shipments associated with this order.
 */
public record AmazonExtensionOrderResponse(
    @NotBlank String orderId,
    @NotBlank String orderDate,
    @NotNull BigDecimal totalAmount,
    @NotNull BigDecimal billedAmount,
    BigDecimal discounts,
    BigDecimal amazonPointsUsed,
    BigDecimal dPointsUsed,
    BigDecimal giftCardAmount,
    BigDecimal shippingFee,
    BigDecimal refundAmount,
    List<AmazonExtensionOrderItemResponse> items,
    List<AmazonExtensionPaymentResponse> payments,
    List<AmazonExtensionShipmentResponse> shipments
) {
    public AmazonExtensionOrderResponse {
        if (items == null) items = List.of();
        if (payments == null) payments = List.of();
        if (shipments == null) shipments = List.of();
    }
}
