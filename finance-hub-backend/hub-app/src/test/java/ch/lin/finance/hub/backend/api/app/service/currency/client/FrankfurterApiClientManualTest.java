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
 * 手動整合測試類別：直接發送真實 HTTP 請求至 Frankfurter API 驗證匯率抓取與快取。
 */
public class FrankfurterApiClientManualTest {

    public static void main(String[] args) {
        FrankfurterApiClient client = new FrankfurterApiClient();

        System.out.println("=================================================");
        System.out.println("1. 測試最新匯率 (Latest USD -> JPY)");
        Double latestUsdJpy = client.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, null);
        System.out.println("--> 最新匯率: 1 USD = " + latestUsdJpy + " JPY");

        System.out.println("\n2. 測試歷史匯率 (2024-05-15 USD -> JPY)");
        Double historyUsdJpy = client.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, LocalDate.of(2024, 5, 15));
        System.out.println("--> 歷史匯率: 1 USD = " + historyUsdJpy + " JPY");

        System.out.println("\n3. 測試記憶體快取 (再次請求 2024-05-15)");
        Double cachedRate = client.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, LocalDate.of(2024, 5, 15));
        System.out.println("--> 快取命中匯率: " + cachedRate);

        System.out.println("\n4. 測試其他幣別 (2025-01-06 EUR -> JPY)");
        Double eurJpy = client.getExchangeRate(CurrencyCode.EUR, CurrencyCode.JPY, LocalDate.of(2025, 1, 6));
        System.out.println("--> 歐元歷史匯率: 1 EUR = " + eurJpy + " JPY");
        System.out.println("=================================================");
    }
}
