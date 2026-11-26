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

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = ETradeCashTransaction.TABLE_NAME, indexes = {
        @Index(name = ETradeCashTransaction.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeCashTransaction.TRANSACTION_DATE_COLUMN,
                ETradeCashTransaction.DESCRIPTION_COLUMN,
                ETradeCashTransaction.AMOUNT_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "transactionDate", "description", "amount" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeCashTransaction extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_cash_transaction";
    public static final String ID_INDEX = "etrade_cash_transaction_id_index";
    public static final String TRANSACTION_DATE_COLUMN = "transaction_date";
    public static final String DESCRIPTION_COLUMN = "description";
    public static final String AMOUNT_COLUMN = "amount";

    @NotNull
    @Column(name = TRANSACTION_DATE_COLUMN, nullable = false)
    private LocalDate transactionDate;

    @NotNull
    @Column(name = DESCRIPTION_COLUMN, nullable = false, length = 255)
    private String description;

    @NotNull
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = AMOUNT_COLUMN, nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = AMOUNT_COLUMN
                    + "_currency", length = 10, nullable = false))
    })
    private MonetaryAmount amount;

    public ETradeCashTransaction(String symbol, LocalDate transactionDate, String description, MonetaryAmount amount) {
        super(symbol);
        this.transactionDate = transactionDate;
        this.description = description;
        this.amount = amount;
    }

    public void merge(ETradeCashTransaction incoming) {
        if (incoming == null) {
            return;
        }
        this.transactionDate = incoming.transactionDate;
        this.description = incoming.description;
        this.amount = incoming.amount;
    }
}
