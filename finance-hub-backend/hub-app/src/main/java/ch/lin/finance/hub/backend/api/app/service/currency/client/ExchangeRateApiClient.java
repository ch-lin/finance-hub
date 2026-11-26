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

import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

/**
 * Interface for external foreign exchange rate REST API clients.
 */
public interface ExchangeRateApiClient {

    /**
     * Fetches the exchange rate between base currency and target currency on a specific date.
     *
     * @param base   The base currency (e.g. USD).
     * @param target The target currency (e.g. JPY).
     * @param date   The quotation date (null for latest rate).
     * @return The exchange rate (1 Base = N Target), or {@code null} if fetch fails.
     */
    Double getExchangeRate(CurrencyCode base, CurrencyCode target, LocalDate date);
}
