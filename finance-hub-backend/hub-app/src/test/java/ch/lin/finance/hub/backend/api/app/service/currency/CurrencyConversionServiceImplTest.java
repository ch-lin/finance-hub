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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.lin.finance.hub.backend.api.app.repository.currency.ExchangeRateRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.currency.client.ExchangeRateApiClient;
import ch.lin.finance.hub.backend.api.common.exception.CurrencyConversionException;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateType;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;

class CurrencyConversionServiceImplTest {

    private CurrencyConversionServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new CurrencyConversionServiceImpl();
    }

    @Nested
    @DisplayName("resolveTargetCurrency Tests")
    @SuppressWarnings("unused")
    class ResolveTargetCurrencyTests {

        @Test
        @DisplayName("Should return user custom currency when profile has defaultCurrency")
        void shouldReturnUserCurrencyWhenProfileHasDefaultCurrency() {
            UserProfile profile = new UserProfile("user1", LocalDate.of(1990, 1, 1), 200_000L, 65);
            profile.setDefaultCurrency(CurrencyCode.USD);

            FinanceHubConfig config = new FinanceHubConfig("default");
            config.setDefaultCurrency(CurrencyCode.JPY);

            CurrencyCode result = service.resolveTargetCurrency(profile, config);
            assertThat(result).isEqualTo(CurrencyCode.USD);
        }

        @Test
        @DisplayName("Should fallback to system config currency when profile currency is null")
        void shouldFallbackToConfigWhenProfileCurrencyIsNull() {
            UserProfile profile = new UserProfile("user1", LocalDate.of(1990, 1, 1), 200_000L, 65);
            profile.setDefaultCurrency(null);

            FinanceHubConfig config = new FinanceHubConfig("default");
            config.setDefaultCurrency(CurrencyCode.TWD);

            CurrencyCode result = service.resolveTargetCurrency(profile, config);
            assertThat(result).isEqualTo(CurrencyCode.TWD);
        }

        @Test
        @DisplayName("Should fallback to system config currency when profile is null")
        void shouldFallbackToConfigWhenProfileIsNull() {
            FinanceHubConfig config = new FinanceHubConfig("default");
            config.setDefaultCurrency(CurrencyCode.EUR);

            CurrencyCode result = service.resolveTargetCurrency(null, config);
            assertThat(result).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        @DisplayName("Should fallback to JPY when both profile and config currency are null")
        void shouldFallbackToJpyWhenBothNull() {
            FinanceHubConfig config = new FinanceHubConfig("default");
            config.setDefaultCurrency(null);

            CurrencyCode result = service.resolveTargetCurrency(null, config);
            assertThat(result).isEqualTo(CurrencyCode.JPY);
        }
    }

    @Nested
    @DisplayName("convertToTargetCurrency Tests")
    @SuppressWarnings("unused")
    class ConvertToTargetCurrencyTests {

        @Test
        @DisplayName("Should return 0 when source is null or amount is null")
        void shouldReturnZeroWhenSourceOrAmountIsNull() {
            assertThat(service.convertToTargetCurrency(null, CurrencyCode.JPY, 150.0)).isEqualTo(0L);
            assertThat(service.convertToTargetCurrency(MonetaryAmount.of((BigDecimal) null, CurrencyCode.USD),
                    CurrencyCode.JPY, 150.0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should return raw amount when source and target currencies match")
        void shouldReturnRawAmountWhenSameCurrency() {
            MonetaryAmount usdAmount = MonetaryAmount.of(1000L, CurrencyCode.USD);
            assertThat(service.convertToTargetCurrency(usdAmount, CurrencyCode.USD, 150.0)).isEqualTo(1000L);

            MonetaryAmount jpyAmount = MonetaryAmount.of(150_000L, CurrencyCode.JPY);
            assertThat(service.convertToTargetCurrency(jpyAmount, CurrencyCode.JPY, 150.0)).isEqualTo(150_000L);
        }

        @Test
        @DisplayName("Should convert USD to JPY using exchange rate")
        void shouldConvertUsdToJpy() {
            MonetaryAmount usdAmount = MonetaryAmount.of(1000L, CurrencyCode.USD);
            double rate = 154.5;
            long result = service.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, rate);
            assertThat(result).isEqualTo(154_500L);
        }

        @Test
        @DisplayName("Should convert JPY to USD using exchange rate")
        void shouldConvertJpyToUsd() {
            MonetaryAmount jpyAmount = MonetaryAmount.of(150_000L, CurrencyCode.JPY);
            double rate = 150.0;
            long result = service.convertToTargetCurrency(jpyAmount, CurrencyCode.USD, rate);
            assertThat(result).isEqualTo(1000L);
        }

        @Test
        @DisplayName("Should throw exception when exchangeRate is invalid and no repository available")
        void shouldThrowExceptionWhenExchangeRateInvalidAndNoRepo() {
            MonetaryAmount usdAmount = MonetaryAmount.of(1000L, CurrencyCode.USD);

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, null))
                    .isInstanceOf(CurrencyConversionException.class);
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, 0.0))
                    .isInstanceOf(CurrencyConversionException.class);
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, -1.0))
                    .isInstanceOf(CurrencyConversionException.class);
        }

        @Test
        @DisplayName("Should fallback to DB exchange rate when snapshot rate is null")
        void shouldFallbackToDbExchangeRateWhenSnapshotRateIsNull() {
            ExchangeRateRecordRepository mockRepo = mock(ExchangeRateRecordRepository.class);
            CurrencyConversionService repoService = new CurrencyConversionServiceImpl(mockRepo);

            ExchangeRateRecord dbRecord = new ExchangeRateRecord(
                    LocalDate.now(),
                    CurrencyCode.USD,
                    CurrencyCode.JPY,
                    BigDecimal.valueOf(155.0),
                    ExchangeRateType.TTM,
                    "MURC");

            when(mockRepo.findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                    eq(CurrencyCode.USD),
                    eq(CurrencyCode.JPY),
                    any()))
                    .thenReturn(Optional.of(dbRecord));

            MonetaryAmount usdAmount = MonetaryAmount.of(1000L, CurrencyCode.USD);
            long result = repoService.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, null);
            assertThat(result).isEqualTo(155_000L);
        }

        @Test
        @DisplayName("Should fallback to API client when snapshot and DB rates are null")
        void shouldFallbackToApiClientWhenSnapshotAndDbRatesAreNull() {
            ExchangeRateRecordRepository mockRepo = mock(ExchangeRateRecordRepository.class);
            ExchangeRateApiClient mockApiClient = mock(ExchangeRateApiClient.class);
            CurrencyConversionService apiService = new CurrencyConversionServiceImpl(mockRepo, mockApiClient);

            when(mockRepo.findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                    any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(mockApiClient.getExchangeRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY), any()))
                    .thenReturn(156.50);

            MonetaryAmount usdAmount = MonetaryAmount.of(1000L, CurrencyCode.USD);
            long result = apiService.convertToTargetCurrency(usdAmount, CurrencyCode.JPY, null);
            assertThat(result).isEqualTo(156_500L);
        }

        @Test
        @DisplayName("Should fallback to DB USD/JPY rate and apply division for JPY to USD conversion")
        void shouldFallbackToDbRateWithDivisionForJpyToUsd() {
            ExchangeRateRecordRepository mockRepo = mock(ExchangeRateRecordRepository.class);
            CurrencyConversionService repoService = new CurrencyConversionServiceImpl(mockRepo);

            // DB stores USD->JPY rate (e.g. 150.0). When converting JPY->USD,
            // the service looks up the inverse (USD->JPY from DB) and divides by it.
            ExchangeRateRecord dbRecord = new ExchangeRateRecord(
                    LocalDate.now(),
                    CurrencyCode.USD,
                    CurrencyCode.JPY,
                    java.math.BigDecimal.valueOf(150.0),
                    ExchangeRateType.TTM,
                    "MURC");

            // No direct JPY->USD record in DB
            when(mockRepo.findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                    eq(CurrencyCode.JPY),
                    eq(CurrencyCode.USD),
                    any()))
                    .thenReturn(Optional.empty());
            // Inverse USD->JPY record exists
            when(mockRepo.findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
                    eq(CurrencyCode.USD),
                    eq(CurrencyCode.JPY),
                    any()))
                    .thenReturn(Optional.of(dbRecord));

            MonetaryAmount jpyAmount = MonetaryAmount.of(150_000L, CurrencyCode.JPY);
            long result = repoService.convertToTargetCurrency(jpyAmount, CurrencyCode.USD, null);
            // 150,000 JPY / 150.0 (USD/JPY rate) = 1,000 USD
            assertThat(result).isEqualTo(1_000L);
        }
    }
}
