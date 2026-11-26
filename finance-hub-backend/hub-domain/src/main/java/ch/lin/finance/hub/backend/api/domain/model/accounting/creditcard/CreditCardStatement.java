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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a monthly credit card statement (parsed from a single CSV file).
 */
@Entity
@Table(name = CreditCardStatement.TABLE_NAME, indexes = {
    @Index(name = CreditCardStatement.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
        CreditCardStatement.CARD_NAME_COLUMN, CreditCardStatement.BILLING_MONTH_COLUMN})
})
@Getter
@EqualsAndHashCode(of = {"cardName", "billingMonth"}, callSuper = false)
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation.
// However, from a Domain-Driven Design (DDD) perspective, an entity should
// never be created
// in an invalid state without its required fields. Therefore, we use
// AccessLevel.PROTECTED
// to satisfy JPA/Hibernate requirements while preventing arbitrary
// instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditCardStatement extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "credit_card_statement";
    public static final String ID_INDEX = "credit_card_statement_id_index";

    /**
     * The name of the card name column.
     */
    public static final String CARD_NAME_COLUMN = "card_name";

    /**
     * The name of the billing month column.
     */
    public static final String BILLING_MONTH_COLUMN = "billing_month";

    /**
     * The name of the payment date column.
     */
    public static final String PAYMENT_DATE_COLUMN = "payment_date";

    /**
     * The name of the total amount column.
     */
    public static final String TOTAL_AMOUNT_COLUMN = "total_amount";

    /**
     * The name of the bank transaction ID column.
     */
    public static final String BANK_TRANSACTION_ID_COLUMN = "bank_transaction_id";

    /**
     * The name of the credit card.
     */
    @NotNull
    @Column(name = CARD_NAME_COLUMN, nullable = false)
    private String cardName;

    /**
     * The billing year and month (e.g., "2024-03"). Can be parsed from the CSV
     * header or the CSV file name.
     */
    @NotNull
    @Column(name = BILLING_MONTH_COLUMN, length = 7, nullable = false)
    private String billingMonth;

    /**
     * The exact date the bank withdrawal is scheduled to happen. Parsed from
     * the CSV (e.g., "お支払日", "今回のお振替日").
     */
    @Column(name = PAYMENT_DATE_COLUMN)
    @Setter
    private LocalDate paymentDate;

    /**
     * The total amount billed for this statement.
     */
    @NotNull
    @Column(name = TOTAL_AMOUNT_COLUMN, nullable = false)
    @Setter
    private Integer totalAmount;

    /**
     * The ID of the bank transaction once this statement is matched with a bank
     * withdrawal.
     */
    @Column(name = BANK_TRANSACTION_ID_COLUMN, length = 36)
    @Setter
    private String bankTransactionId;

    /**
     * The list of individual credit card transactions for this statement.
     */
    @OneToMany(mappedBy = "statement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CreditCardTransaction> transactions = new ArrayList<>();

    /**
     * Creates a new CreditCardStatement.
     *
     * @param cardName The name of the credit card.
     * @param billingMonth The billing year and month.
     * @param paymentDate The scheduled payment date.
     * @param totalAmount The total billed amount.
     */
    public CreditCardStatement(String cardName, String billingMonth, LocalDate paymentDate, Integer totalAmount) {
        this();
        this.cardName = cardName;
        this.billingMonth = billingMonth;
        this.paymentDate = paymentDate;
        this.totalAmount = totalAmount;
    }

    /**
     * Helper method to add a transaction and keep the bidirectional
     * relationship in sync.
     *
     * @param transaction The transaction to add.
     */
    public void addTransaction(CreditCardTransaction transaction) {
        transaction.setStatement(this);
        this.transactions.add(transaction);
    }

    /**
     * Merges incoming statement data into this statement:
     * - Overwrites all statement-level facts (cardName, billingMonth, totalAmount, paymentDate).
     * - Leaves system relational fields (bankTransactionId) untouched.
     * - Merges transaction details loosely, preserving user-managed fields (category) and zero deletions.
     *
     * @param incoming The parsed statement to merge from.
     */
    public void merge(CreditCardStatement incoming) {
        if (incoming == null) {
            return;
        }
        this.cardName = incoming.getCardName();
        this.billingMonth = incoming.getBillingMonth();
        this.totalAmount = incoming.getTotalAmount();
        this.paymentDate = incoming.getPaymentDate();
        this.mergeTransactions(incoming.getTransactions());
    }

    /**
     * Merges incoming transactions into the current statement:
     * - Matches existing transactions loosely by transactionDate + amount + storeName.
     * - Overwrites facts on matched transactions without touching user-managed fields like category.
     * - Adds new transactions that don't match any existing records.
     * - Never deletes existing transactions (zero deletion rule).
     *
     * @param incomingTransactions The list of newly parsed transactions.
     */
    public void mergeTransactions(List<CreditCardTransaction> incomingTransactions) {
        if (incomingTransactions == null) {
            return;
        }
        for (CreditCardTransaction incoming : incomingTransactions) {
            CreditCardTransaction existing = this.transactions.stream()
                    .filter(tx -> matches(tx, incoming))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.merge(incoming);
            } else {
                this.addTransaction(incoming);
            }
        }
    }

    private boolean matches(CreditCardTransaction a, CreditCardTransaction b) {
        return Objects.equals(a.getTransactionDate(), b.getTransactionDate())
                && Objects.equals(a.getAmount(), b.getAmount())
                && Objects.equals(a.getStoreName(), b.getStoreName());
    }
}
