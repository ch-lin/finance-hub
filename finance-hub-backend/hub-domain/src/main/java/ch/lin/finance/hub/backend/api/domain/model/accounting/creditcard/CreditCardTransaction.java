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
package ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;
import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single transaction from a credit card statement.
 */
@Entity
@Table(name = CreditCardTransaction.TABLE_NAME, indexes = {
        @Index(name = CreditCardTransaction.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN),
        @Index(name = CreditCardTransaction.STATEMENT_ID_INDEX, columnList = CreditCardTransaction.STATEMENT_COLUMN)
})
@Getter
@EqualsAndHashCode(of = { "cardName", "transactionDate", "storeName", "amount" }, callSuper = false)
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation.
// However, from a Domain-Driven Design (DDD) perspective, an entity should
// never be created
// in an invalid state without its required fields. Therefore, we use
// AccessLevel.PROTECTED
// to satisfy JPA/Hibernate requirements while preventing arbitrary
// instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditCardTransaction extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "credit_card_transaction";
    public static final String ID_INDEX = "credit_card_transaction_id_index";
    public static final String STATEMENT_ID_INDEX = "credit_card_transaction_statement_id_index";

    /**
     * The name of the card name column.
     */
    public static final String CARD_NAME_COLUMN = "card_name";

    /**
     * The name of the transaction date column.
     */
    public static final String TRANSACTION_DATE_COLUMN = "transaction_date";

    /**
     * The name of the store name column.
     */
    public static final String STORE_NAME_COLUMN = "store_name";

    /**
     * The name of the amount column.
     */
    public static final String AMOUNT_COLUMN = "amount";

    /**
     * The name of the memo column.
     */
    public static final String MEMO_COLUMN = "memo";

    /**
     * The name of the statement foreign key column.
     */
    public static final String STATEMENT_COLUMN = "statement_id";

    /**
     * The name of the credit card (e.g., PayPay, AEON, SAISON, MUFG).
     */
    @NotNull
    @Column(name = CreditCardTransaction.CARD_NAME_COLUMN, nullable = false)
    private String cardName;

    /**
     * The transaction date, normalized to YYYY-MM-DD.
     */
    @NotNull
    @Column(name = CreditCardTransaction.TRANSACTION_DATE_COLUMN, nullable = false)
    private LocalDate transactionDate;

    /**
     * The name of the store or merchant.
     */
    @NotNull
    @Column(name = CreditCardTransaction.STORE_NAME_COLUMN, nullable = false)
    private String storeName;

    /**
     * The transaction amount. Japanese Yen typically has no decimal points, so
     * Integer is used. Can be negative for refunds.
     */
    @NotNull
    @Column(name = CreditCardTransaction.AMOUNT_COLUMN, nullable = false)
    private Integer amount;

    /**
     * Additional raw information such as point deductions, installment details,
     * or foreign exchange rates.
     */
    @Column(name = CreditCardTransaction.MEMO_COLUMN, length = 500)
    @Setter
    private String memo;

    /**
     * The CreditCardStatement this transaction belongs to.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = CreditCardTransaction.STATEMENT_COLUMN)
    @Setter(AccessLevel.PACKAGE)
    private CreditCardStatement statement;

    /**
     * The category classification of this transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_cc_tx_category"))
    @Setter
    private TransactionCategory category;

    /**
     * Creates a new CreditCardTransaction with the essential immutable fields.
     *
     * @param cardName        The name of the credit card.
     * @param transactionDate The date of the transaction.
     * @param storeName       The name of the store.
     * @param amount          The transaction amount.
     */
    public CreditCardTransaction(String cardName, LocalDate transactionDate, String storeName, Integer amount) {
        this();
        this.cardName = cardName;
        this.transactionDate = transactionDate;
        this.storeName = storeName;
        this.amount = amount;
    }

    /**
     * Creates a new CreditCardTransaction with the essential fields and memo.
     *
     * @param cardName        The name of the credit card.
     * @param transactionDate The date of the transaction.
     * @param storeName       The name of the store.
     * @param amount          The transaction amount.
     * @param memo            Additional transaction details/memo.
     */
    public CreditCardTransaction(String cardName, LocalDate transactionDate, String storeName, Integer amount, String memo) {
        this(cardName, transactionDate, storeName, amount);
        this.memo = memo;
    }

    /**
     * Merges incoming transaction data: overwrites all fact fields with incoming data
     * from re-imported statement, leaving user-managed fields (such as category) untouched.
     *
     * @param incoming The parsed transaction with updated facts.
     */
    public void merge(CreditCardTransaction incoming) {
        if (incoming == null) {
            return;
        }
        this.cardName = incoming.getCardName();
        this.transactionDate = incoming.getTransactionDate();
        this.storeName = incoming.getStoreName();
        this.amount = incoming.getAmount();
        this.memo = incoming.getMemo();
    }
}
