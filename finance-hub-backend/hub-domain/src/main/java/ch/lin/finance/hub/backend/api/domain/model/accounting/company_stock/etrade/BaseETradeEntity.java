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
package ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade;

import java.util.Objects;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@Getter
@EqualsAndHashCode(of = { "symbol" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseETradeEntity extends MultiUserBaseEntity {

    public static final String SYMBOL_COLUMN = "symbol";

    @NotNull
    @Column(name = SYMBOL_COLUMN, nullable = false, length = 20)
    protected String symbol;

    public BaseETradeEntity(String symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
    }
}
