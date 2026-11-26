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
package ch.lin.finance.hub.backend.api.domain.model.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a historical or current foreign exchange rate quotation between
 * two currencies on a specific date (e.g. TTM rates from banks or central banks).
 */
@Entity
@Table(name = ExchangeRateRecord.TABLE_NAME, indexes = {
        @Index(name = "idx_fx_record_query", columnList = "record_date, base_currency, target_currency"),
        @Index(name = ExchangeRateRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_fx_record_unique", columnNames = { "record_date", "base_currency",
                "target_currency", "rate_type" })
})
@Getter
@Setter
@EqualsAndHashCode(of = { "recordDate", "baseCurrency", "targetCurrency", "rateType" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateRecord extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "finance_exchange_rate_record";
    public static final String ID_INDEX = "exchange_rate_record_id_index";

    @NotNull
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", length = 10, nullable = false)
    private CurrencyCode baseCurrency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_currency", length = 10, nullable = false)
    private CurrencyCode targetCurrency;

    @NotNull
    @Column(name = "rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal rate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", length = 20, nullable = false)
    private ExchangeRateType rateType = ExchangeRateType.TTM;

    @Column(name = "source", length = 50)
    private String source;

    public ExchangeRateRecord(LocalDate recordDate, CurrencyCode baseCurrency, CurrencyCode targetCurrency,
            BigDecimal rate, ExchangeRateType rateType, String source) {
        this.recordDate = recordDate;
        this.baseCurrency = baseCurrency;
        this.targetCurrency = targetCurrency;
        this.rate = rate;
        this.rateType = rateType != null ? rateType : ExchangeRateType.TTM;
        this.source = source;
    }
}
