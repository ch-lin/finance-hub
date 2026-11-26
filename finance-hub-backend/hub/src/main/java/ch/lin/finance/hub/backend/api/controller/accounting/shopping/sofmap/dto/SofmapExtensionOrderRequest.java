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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object representing a complete Sofmap order
 * payload received from the Chrome Extension.
 *
 * @param orderId The unique identifier for the Sofmap order.
 * @param orderDate The date the order was placed.
 * @param totalAmount The total amount of the order.
 * @param billedAmount The final amount billed to the customer.
 * @param discounts Total discounts applied to the order.
 * @param pointsUsed The amount paid using points.
 * @param shippingFee The shipping fee charged for the order.
 * @param refundAmount Any refund amount associated with the order.
 * @param items The list of items included in this order.
 * @param payments The list of payment methods used for this order.
 * @param shipments The list of shipments associated with this order.
 */
public record SofmapExtensionOrderRequest(
    @NotBlank(message = "Order ID is mandatory") String orderId,
    @NotBlank(message = "Order Date is mandatory") String orderDate,
    @NotNull(message = "Total Amount is mandatory") BigDecimal totalAmount,
    @NotNull(message = "Billed Amount is mandatory") BigDecimal billedAmount,
    BigDecimal discounts,
    BigDecimal pointsUsed,
    BigDecimal shippingFee,
    BigDecimal refundAmount,
    List<SofmapExtensionOrderItemRequest> items,
    List<SofmapExtensionPaymentRequest> payments,
    List<SofmapExtensionShipmentRequest> shipments
) {
    public SofmapExtensionOrderRequest {
        if (items == null) items = List.of();
        if (payments == null) payments = List.of();
        if (shipments == null) shipments = List.of();
    }
}
