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
package ch.lin.finance.hub.backend.api.app.repository.currency;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateType;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

public interface ExchangeRateRecordRepository extends JpaRepository<ExchangeRateRecord, String> {

    Optional<ExchangeRateRecord> findByRecordDateAndBaseCurrencyAndTargetCurrencyAndRateType(
            LocalDate recordDate, CurrencyCode baseCurrency, CurrencyCode targetCurrency, ExchangeRateType rateType);

    Optional<ExchangeRateRecord> findFirstByBaseCurrencyAndTargetCurrencyAndRecordDateLessThanEqualOrderByRecordDateDesc(
            CurrencyCode baseCurrency, CurrencyCode targetCurrency, LocalDate recordDate);

    List<ExchangeRateRecord> findByRecordDateBetweenOrderByRecordDateAsc(
            LocalDate startDate, LocalDate endDate);

    void deleteByRecordDateAndBaseCurrencyAndTargetCurrencyAndRateType(
            LocalDate recordDate, CurrencyCode baseCurrency, CurrencyCode targetCurrency, ExchangeRateType rateType);
}
