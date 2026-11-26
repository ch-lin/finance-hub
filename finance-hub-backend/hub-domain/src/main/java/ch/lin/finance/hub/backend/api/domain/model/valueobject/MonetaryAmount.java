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
package ch.lin.finance.hub.backend.api.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonetaryAmount {

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10)
    private CurrencyCode currency;

    private MonetaryAmount(BigDecimal amount, CurrencyCode currency) {
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.currency = currency;
    }

    public static MonetaryAmount of(BigDecimal amount, CurrencyCode currency) {
        return new MonetaryAmount(amount, currency);
    }

    public static MonetaryAmount of(Long amount, CurrencyCode currency) {
        return new MonetaryAmount(amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO, currency);
    }

    public static MonetaryAmount of(Double amount, CurrencyCode currency) {
        return new MonetaryAmount(amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO, currency);
    }

    public static MonetaryAmount zero(CurrencyCode currency) {
        return new MonetaryAmount(BigDecimal.ZERO, currency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MonetaryAmount money = (MonetaryAmount) o;
        return amount.compareTo(money.amount) == 0 && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.doubleValue(), currency);
    }
}
