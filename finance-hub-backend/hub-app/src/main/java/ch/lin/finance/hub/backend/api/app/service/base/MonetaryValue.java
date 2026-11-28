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
package ch.lin.finance.hub.backend.api.app.service.base;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonetaryValue {
    private BigDecimal amount;
    private CurrencyCode currency;

    public static MonetaryValue fromEntity(MonetaryAmount money) {
        if (money == null) {
            return null;
        }
        return new MonetaryValue(money.getAmount(), money.getCurrency());
    }

    public MonetaryAmount toDomain() {
        return MonetaryAmount.of(amount, currency);
    }
}
