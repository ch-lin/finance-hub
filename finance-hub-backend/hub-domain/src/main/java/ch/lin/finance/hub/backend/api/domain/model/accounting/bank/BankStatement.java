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

import java.util.ArrayList;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents a bank statement containing multiple transactions.
 */
@Entity
@Table(name = BankStatement.TABLE_NAME, indexes = {
    @Index(name = BankStatement.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN, BankStatement.BANK_NAME_COLUMN,
        BankStatement.STATEMENT_MONTH_COLUMN})
})
@Getter
@EqualsAndHashCode(of = {"bankName", "statementMonth"}, callSuper = false)
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BankStatement extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "bank_statement";
    public static final String ID_INDEX = "bank_statement_id_index";

    /**
     * The name of the bank name column.
     */
    public static final String BANK_NAME_COLUMN = "bank_name";

    /**
     * The name of the statement month column.
     */
    public static final String STATEMENT_MONTH_COLUMN = "statement_month";

    /**
     * The name of the bank (e.g., SMBC, MUFG).
     */
    @NotNull
    @Column(name = BankStatement.BANK_NAME_COLUMN, nullable = false)
    private String bankName;

    /**
     * The month this statement applies to, usually formatted as YYYY-MM.
     */
    @NotNull
    @Column(name = BankStatement.STATEMENT_MONTH_COLUMN, nullable = false)
    private String statementMonth;

    /**
     * The list of transactions included in this statement.
     */
    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<BankTransaction> transactions = new ArrayList<>();

    /**
     * Creates a new BankStatement with the essential immutable fields.
     *
     * @param bankName The name of the bank.
     * @param statementMonth The month of the statement (YYYY-MM).
     */
    public BankStatement(String bankName, String statementMonth) {
        this.bankName = bankName;
        this.statementMonth = statementMonth;
    }

    /**
     * Adds a transaction to this statement and maintains the bidirectional
     * relationship.
     *
     * @param transaction The transaction to add.
     */
    public void addTransaction(BankTransaction transaction) {
        transactions.add(transaction);
        transaction.setStatement(this);
    }

    /**
     * Removes a transaction from this statement and maintains the bidirectional
     * relationship.
     *
     * @param transaction The transaction to remove.
     */
    public void removeTransaction(BankTransaction transaction) {
        transactions.remove(transaction);
        transaction.setStatement(null);
    }

    /**
     * Merges incoming transactions into this statement.
     * <p>
     * For each incoming transaction:
     * <ul>
     *   <li>If a matching transaction already exists (based on composite equals),
     *       updates its fact fields via {@link BankTransaction#merge(BankTransaction)}
     *       while strictly preserving user-defined fields (e.g. category).</li>
     *   <li>If no matching transaction is found, adds it as a new transaction to this statement.</li>
     * </ul>
     * This method never performs any deletion.
     *
     * @param incomingTransactions The list of parsed transactions to merge.
     * @return The number of newly added transactions.
     */
    public int mergeTransactions(List<BankTransaction> incomingTransactions) {
        if (incomingTransactions == null || incomingTransactions.isEmpty()) {
            return 0;
        }

        int newCount = 0;
        for (BankTransaction incoming : incomingTransactions) {
            java.util.Optional<BankTransaction> existingOpt = transactions.stream()
                    .filter(tx -> tx.equals(incoming))
                    .findFirst();

            if (existingOpt.isPresent()) {
                existingOpt.get().merge(incoming);
            } else {
                addTransaction(incoming);
                newCount++;
            }
        }
        return newCount;
    }
}
