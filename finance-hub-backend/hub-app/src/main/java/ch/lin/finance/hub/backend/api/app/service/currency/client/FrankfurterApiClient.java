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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.Scheme;

/**
 * Free foreign exchange rate client powered by the European Central Bank (ECB)
 * via the Frankfurter open API (api.frankfurter.app).
 * <p>
 * Uses in-memory caching to avoid redundant network calls and guarantees zero
 * database writes.
 */
@Component
public class FrankfurterApiClient implements ExchangeRateApiClient {

    private static final Logger logger = LoggerFactory.getLogger(FrankfurterApiClient.class);
    private static final String HOST = "api.frankfurter.app";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Double> memoryCache = new ConcurrentHashMap<>();

    @Override
    public Double getExchangeRate(CurrencyCode base, CurrencyCode target, LocalDate date) {
        if (base == null || target == null || base == CurrencyCode.UNKNOWN || target == CurrencyCode.UNKNOWN) {
            return null;
        }

        if (base == target) {
            return 1.0;
        }

        String path = date != null ? "/" + date : "/latest";
        String cacheKey = base.name() + "_" + target.name() + "_" + path;

        return memoryCache.computeIfAbsent(cacheKey, key -> fetchRateFromApi(base, target, path));
    }

    private Double fetchRateFromApi(CurrencyCode base, CurrencyCode target, String path) {
        try (HttpClient client = new HttpClient(Scheme.HTTPS, HOST, 443)) {
            Map<String, String> params = Map.of(
                    "from", base.name(),
                    "to", target.name());

            HttpClient.Response response = client.get(path, params, null);
            if (response != null && response.statusCode() == 200) {
                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                JsonNode rateNode = root.path("rates").path(target.name());
                if (!rateNode.isMissingNode() && rateNode.isNumber()) {
                    double rate = rateNode.asDouble();
                    logger.info("Fetched exchange rate from Frankfurter API: 1 {} = {} {} ({})",
                            base, rate, target, path);
                    return rate;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch exchange rate from Frankfurter API (path={}, base={}, target={}): {}",
                    path, base, target, e.getMessage());
        }
        return null;
    }
}
