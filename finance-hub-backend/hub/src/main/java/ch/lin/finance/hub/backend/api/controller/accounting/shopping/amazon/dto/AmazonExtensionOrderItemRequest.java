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

/**
 * Data Transfer Object representing an individual item within an Amazon order
 * received from the Chrome Extension.
 *
 * @param itemName The name or title of the purchased item.
 * @param asin The Amazon Standard Identification Number (ASIN) of the item.
 * @param seller The name of the seller or merchant.
 * @param unitPrice The price of a single unit of the item.
 * @param quantity The quantity of the item purchased.
 */
public record AmazonExtensionOrderItemRequest(
    String itemName,
    String asin,
    String seller,
    BigDecimal unitPrice,
    Integer quantity
) {}
