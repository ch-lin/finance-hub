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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object representing an item in a Rakuten order
 * payload received from the Chrome Extension.
 *
 * @param itemName The name of the item.
 * @param asin The ASIN/Item ID of the product.
 * @param seller The seller of the item.
 * @param unitPrice The price per unit of the item.
 * @param quantity The quantity of the item purchased.
 */
public record RakutenExtensionOrderItemRequest(
    @NotBlank String itemName,
    String asin,
    String seller,
    @NotNull BigDecimal unitPrice,
    @NotNull Integer quantity
) {}
