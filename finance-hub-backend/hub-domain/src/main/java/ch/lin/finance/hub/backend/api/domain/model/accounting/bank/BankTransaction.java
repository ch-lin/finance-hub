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
package ch.lin.finance.hub.backend.api.domain.model.accounting.bank;

import java.time.LocalDate;

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
 * Represents a single transaction from a bank passbook.
 */
@Entity
@Table(name = BankTransaction.TABLE_NAME, indexes = {
        @Index(name = BankTransaction.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN),
        @Index(name = BankTransaction.STATEMENT_ID_INDEX, columnList = BankTransaction.STATEMENT_COLUMN)
})
@Getter
@EqualsAndHashCode(of = { "bankName", "transactionDate", "description", "depositAmount", "withdrawalAmount",
        "balance" }, callSuper = false)
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation.
// However, from a Domain-Driven Design (DDD) perspective, an entity should
// never be created
// in an invalid state without its required fields. Therefore, we use
// AccessLevel.PROTECTED
// to satisfy JPA/Hibernate requirements while preventing arbitrary
// instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankTransaction extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "bank_transaction";
    public static final String ID_INDEX = "bank_transaction_id_index";
    public static final String STATEMENT_ID_INDEX = "bank_transaction_statement_id_index";
    public static final String BANK_NAME_COLUMN = "bank_name";
    public static final String TRANSACTION_DATE_COLUMN = "transaction_date";
    public static final String DESCRIPTION_COLUMN = "description";
    public static final String DEPOSIT_AMOUNT_COLUMN = "deposit_amount";
    public static final String WITHDRAWAL_AMOUNT_COLUMN = "withdrawal_amount";
    public static final String BALANCE_COLUMN = "balance";
    public static final String MEMO_COLUMN = "memo";
    public static final String STATEMENT_COLUMN = "statement_id";

    @NotNull
    @Column(name = BankTransaction.BANK_NAME_COLUMN, nullable = false)
    private String bankName;

    @NotNull
    @Column(name = BankTransaction.TRANSACTION_DATE_COLUMN, nullable = false)
    private LocalDate transactionDate;

    @NotNull
    @Column(name = BankTransaction.DESCRIPTION_COLUMN, nullable = false)
    private String description;

    @Column(name = BankTransaction.DEPOSIT_AMOUNT_COLUMN)
    private Integer depositAmount;

    @Column(name = BankTransaction.WITHDRAWAL_AMOUNT_COLUMN)
    private Integer withdrawalAmount;

    @Column(name = BankTransaction.BALANCE_COLUMN)
    private Integer balance;

    @Column(name = BankTransaction.MEMO_COLUMN, length = 500)
    @Setter
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = BankTransaction.STATEMENT_COLUMN)
    @Setter(AccessLevel.PACKAGE)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private BankStatement statement;

    /**
     * The category classification of this transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_bank_tx_category"))
    @Setter
    private TransactionCategory category;

    /**
     * Creates a new BankTransaction with all parsed fact fields including memo.
     *
     * @param bankName         The bank name.
     * @param transactionDate  The transaction date.
     * @param description      The transaction description / counterparty.
     * @param depositAmount    The deposit amount (nullable).
     * @param withdrawalAmount The withdrawal amount (nullable).
     * @param balance          The account balance after transaction (nullable).
     * @param memo             The memo / note parsed from bank portal (nullable).
     */
    public BankTransaction(String bankName, LocalDate transactionDate, String description, Integer depositAmount,
            Integer withdrawalAmount, Integer balance, String memo) {
        this.bankName = bankName;
        this.transactionDate = transactionDate;
        this.description = description;
        this.depositAmount = depositAmount;
        this.withdrawalAmount = withdrawalAmount;
        this.balance = balance;
        this.memo = memo;
    }

    /**
     * Overload constructor for cases without memo.
     */
    public BankTransaction(String bankName, LocalDate transactionDate, String description, Integer depositAmount,
            Integer withdrawalAmount, Integer balance) {
        this(bankName, transactionDate, description, depositAmount, withdrawalAmount, balance, null);
    }

    /**
     * Merges incoming transaction data: overwrites all fact fields with incoming data
     * from re-imported statement, leaving user-managed fields (such as category) untouched.
     *
     * @param incoming The parsed transaction with updated facts.
     */
    public void merge(BankTransaction incoming) {
        if (incoming == null) {
            return;
        }
        this.bankName = incoming.getBankName();
        this.transactionDate = incoming.getTransactionDate();
        this.description = incoming.getDescription();
        this.depositAmount = incoming.getDepositAmount();
        this.withdrawalAmount = incoming.getWithdrawalAmount();
        this.balance = incoming.getBalance();
        this.memo = incoming.getMemo();
        // User-defined fields (category) are not touched
    }
}
