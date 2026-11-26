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

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenPayment;

public record RakutenPaymentResult(
        String method,
        BigDecimal amount) {
    public static RakutenPaymentResult fromEntity(RakutenPayment entity) {
        if (entity == null)
            return null;
        return new RakutenPaymentResult(
                entity.getMethod(),
                entity.getAmount());
    }
}
