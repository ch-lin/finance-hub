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
package ch.lin.finance.hub.backend.api.app.service.currency;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;

public interface CurrencyConversionService {

    /**
     * Resolves the target currency based on the user profile and system configuration.
     * Prioritizes the user's custom preferred currency if present; otherwise, falls back
     * to the system configuration's default currency.
     *
     * @param profile user's financial profile (nullable)
     * @param config  active system configuration (non-null)
     * @return the resolved target CurrencyCode
     */
    CurrencyCode resolveTargetCurrency(UserProfile profile, FinanceHubConfig config);

    /**
     * Converts a MonetaryAmount into the target currency using the given USD/JPY exchange rate.
     *
     * @param source               the source monetary amount
     * @param targetCurrency       the target currency code
     * @param usdToJpyExchangeRate the exchange rate expressed as "1 USD = N JPY"
     * @return the converted amount rounded to the nearest long
     */
    long convertToTargetCurrency(MonetaryAmount source, CurrencyCode targetCurrency, Double usdToJpyExchangeRate);
}
