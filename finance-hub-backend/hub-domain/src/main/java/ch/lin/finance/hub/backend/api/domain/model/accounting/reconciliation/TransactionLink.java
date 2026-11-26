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
package ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a reconciliation link between a credit card transaction and an
 * Amazon order.
 */
@Entity
@Table(name = TransactionLink.TABLE_NAME, indexes = {
        @Index(name = TransactionLink.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN),
        @Index(name = TransactionLink.CREDIT_CARD_TRANSACTION_ID_INDEX, columnList = TransactionLink.CREDIT_CARD_TRANSACTION_ID_COLUMN),
        @Index(name = TransactionLink.AMAZON_ORDER_ID_INDEX, columnList = TransactionLink.AMAZON_ORDER_ID_COLUMN)
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation.
// However, from a Domain-Driven Design (DDD) perspective, an entity should
// never be created
// in an invalid state without its required fields. Therefore, we use
// AccessLevel.PROTECTED
// to satisfy JPA/Hibernate requirements while preventing arbitrary
// instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLink extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "transaction_link";
    public static final String CREDIT_CARD_TRANSACTION_ID_COLUMN = "credit_card_transaction_id";
    public static final String AMAZON_ORDER_ID_COLUMN = "amazon_order_id";

    public static final String ID_INDEX = "transaction_link_id_index";
    public static final String CREDIT_CARD_TRANSACTION_ID_INDEX = "transaction_link_cc_tx_id_index";
    public static final String AMAZON_ORDER_ID_INDEX = "transaction_link_amazon_order_id_index";

    /**
     * The credit card transaction that is being linked.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = CREDIT_CARD_TRANSACTION_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = "fk_link_cc_tx"))
    private CreditCardTransaction creditCardTransaction;

    /**
     * The Amazon order that is being reconciled against the transaction.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = AMAZON_ORDER_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = "fk_link_amazon_order"))
    private AmazonOrder amazonOrder;

    /**
     * Creates a new transaction link between a credit card transaction and an
     * Amazon order.
     *
     * @param creditCardTransaction the credit card transaction to link
     * @param amazonOrder           the Amazon order to link
     */
    public TransactionLink(CreditCardTransaction creditCardTransaction, AmazonOrder amazonOrder) {
        this.creditCardTransaction = creditCardTransaction;
        this.amazonOrder = amazonOrder;
    }
}
