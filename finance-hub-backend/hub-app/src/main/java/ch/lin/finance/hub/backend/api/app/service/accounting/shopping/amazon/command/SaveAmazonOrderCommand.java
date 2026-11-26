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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaveAmazonOrderCommand(
    String orderId,
    LocalDate orderDate,
    BigDecimal totalAmount,
    BigDecimal billedAmount,
    BigDecimal discounts,
    BigDecimal amazonPointsUsed,
    BigDecimal dPointsUsed,
    BigDecimal giftCardAmount,
    BigDecimal shippingFee,
    BigDecimal refundAmount,
    List<SaveAmazonItemCommand> items,
    List<SaveAmazonPaymentCommand> payments,
    List<SaveAmazonShipmentCommand> shipments
) {
    public SaveAmazonOrderCommand {
        if (items == null) items = List.of();
        if (payments == null) payments = List.of();
        if (shipments == null) shipments = List.of();
    }
}
