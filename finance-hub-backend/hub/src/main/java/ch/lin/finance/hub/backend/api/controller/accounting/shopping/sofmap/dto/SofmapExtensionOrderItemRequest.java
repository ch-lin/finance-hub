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

/**
 * Data Transfer Object representing an item in a Sofmap order request.
 *
 * @param name The name of the item.
 * @param itemId The unique identifier of the item.
 * @param seller The seller of the item.
 * @param quantity The quantity purchased.
 * @param unitPrice The price per unit of the item.
 */
public record SofmapExtensionOrderItemRequest(
    String name,
    String itemId,
    String seller,
    Integer quantity,
    BigDecimal unitPrice
) {}
