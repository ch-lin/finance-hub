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
 * Data Transfer Object representing a payment method used for an Amazon order
 * received from the Chrome Extension.
 *
 * @param method The name of the payment method (e.g., Credit Card, Gift Card).
 * @param amount The amount charged to this payment method.
 */
public record AmazonExtensionPaymentRequest(
    String method,
    BigDecimal amount
) {}
