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
package ch.lin.finance.hub.backend.api.app.service.currency.client;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

import static org.assertj.core.api.Assertions.assertThat;

class FrankfurterApiClientTest {

    private FrankfurterApiClient client;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        client = new FrankfurterApiClient();
    }

    @Test
    void shouldReturnOneWhenSameCurrency() {
        assertThat(client.getExchangeRate(CurrencyCode.USD, CurrencyCode.USD, null)).isEqualTo(1.0);
        assertThat(client.getExchangeRate(CurrencyCode.JPY, CurrencyCode.JPY, LocalDate.of(2025, 1, 6))).isEqualTo(1.0);
    }

    @Test
    void shouldReturnNullWhenCurrencyInvalidOrUnknown() {
        assertThat(client.getExchangeRate(null, CurrencyCode.JPY, null)).isNull();
        assertThat(client.getExchangeRate(CurrencyCode.USD, null, null)).isNull();
        assertThat(client.getExchangeRate(CurrencyCode.UNKNOWN, CurrencyCode.JPY, null)).isNull();
        assertThat(client.getExchangeRate(CurrencyCode.USD, CurrencyCode.UNKNOWN, null)).isNull();
    }

    @Test
    void shouldCacheSubsequentCalls() {
        // First call might succeed if online or fail if offline, but shouldn't throw exception
        Double firstResult = client.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, LocalDate.of(2024, 5, 15));
        Double secondResult = client.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, LocalDate.of(2024, 5, 15));

        assertThat(firstResult).isEqualTo(secondResult);
    }
}
