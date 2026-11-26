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

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.repository.currency.ExchangeRateRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.currency.client.ExchangeRateApiClient;
import ch.lin.finance.hub.backend.api.common.exception.CurrencyConversionException;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;

@Service
public class CurrencyConversionServiceImpl implements CurrencyConversionService {

    private final ExchangeRateRecordRepository repository;
    private final ExchangeRateApiClient apiClient;

    @Autowired
    public CurrencyConversionServiceImpl(
            ExchangeRateRecordRepository repository,
            ExchangeRateApiClient apiClient) {
        this.repository = repository;
        this.apiClient = apiClient;
    }

    public CurrencyConversionServiceImpl(ExchangeRateRecordRepository repository) {
        this(repository, null);
    }

    public CurrencyConversionServiceImpl() {
        this(null, null);
    }

    @Override
    public CurrencyCode resolveTargetCurrency(UserProfile profile, FinanceHubConfig config) {
        if (profile != null && profile.getDefaultCurrency() != null) {
            return profile.getDefaultCurrency();
        }
        if (config != null && config.getDefaultCurrency() != null) {
            return config.getDefaultCurrency();
        }
        return CurrencyCode.JPY;
    }

    @Override
    public long convertToTargetCurrency(MonetaryAmount source, CurrencyCode targetCurrency,
            Double usdToJpyExchangeRate) {
        if (source == null || source.getAmount() == null) {
            return 0L;
        }
        double rawAmount = source.getAmount().doubleValue();
        CurrencyCode sourceCurrency = source.getCurrency();

        if (sourceCurrency == null || targetCurrency == null || sourceCurrency == targetCurrency) {
            return Math.round(rawAmount);
        }

        Double effectiveRate = usdToJpyExchangeRate;
        if (effectiveRate == null || effectiveRate <= 0) {
            effectiveRate = findLatestExchangeRateFromDb(sourceCurrency, targetCurrency);
        }
        if (effectiveRate == null || effectiveRate <= 0) {
            effectiveRate = findLatestExchangeRateFromApi(sourceCurrency, targetCurrency);
        }

        if (effectiveRate != null && effectiveRate > 0) {
            if (sourceCurrency == CurrencyCode.USD && targetCurrency == CurrencyCode.JPY) {
                return Math.round(rawAmount * effectiveRate);
            } else if (sourceCurrency == CurrencyCode.JPY && targetCurrency == CurrencyCode.USD) {
                return Math.round(rawAmount / effectiveRate);
            }
        }

        throw new CurrencyConversionException(
                "No valid exchange rate found between " + sourceCurrency + " and " + targetCurrency);
    }

    private Double findLatestExchangeRateFromDb(CurrencyCode source, CurrencyCode target) {
        if (repository == null) {
            return null;
        }
        Optional<ExchangeRateRecord> directRate = repository
                .findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                        source, target, LocalDate.now());
        if (directRate.isPresent()) {
            return directRate.get().getRate().doubleValue();
        }

        // If inverse rate exists (e.g. searching USD->JPY, check if JPY->USD or
        // vice-versa)
        if (source == CurrencyCode.JPY && target == CurrencyCode.USD) {
            Optional<ExchangeRateRecord> usdJpy = repository
                    .findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                            CurrencyCode.USD, CurrencyCode.JPY, LocalDate.now());
            if (usdJpy.isPresent()) {
                return usdJpy.get().getRate().doubleValue();
            }
        }

        return null;
    }

    private Double findLatestExchangeRateFromApi(CurrencyCode source, CurrencyCode target) {
        if (apiClient == null) {
            return null;
        }
        Double rate = apiClient.getExchangeRate(source, target, null);
        if (rate != null && rate > 0) {
            return rate;
        }

        // Check inverse quotation
        if (source == CurrencyCode.JPY && target == CurrencyCode.USD) {
            Double usdJpy = apiClient.getExchangeRate(CurrencyCode.USD, CurrencyCode.JPY, null);
            if (usdJpy != null && usdJpy > 0) {
                return usdJpy;
            }
        }

        return null;
    }
}
