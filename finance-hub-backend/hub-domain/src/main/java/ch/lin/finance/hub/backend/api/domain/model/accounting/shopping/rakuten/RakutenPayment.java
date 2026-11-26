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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.BasePayment;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a payment method used in a Rakuten order.
 */
@Entity
@Table(name = RakutenPayment.TABLE_NAME, indexes = {
        @Index(name = RakutenPayment.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = RakutenPayment.ORDER_ID_INDEX, columnList = RakutenPayment.ORDER_ID_COLUMN)
})
@Getter
public class RakutenPayment extends BasePayment<RakutenOrder> {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "rakuten_payment";

    public static final String ID_INDEX = "rakuten_payment_id_idx";
    public static final String ORDER_ID_INDEX = "rakuten_payment_order_id_idx";

    /**
     * The name of the order ID column used for joining.
     */
    public static final String ORDER_ID_COLUMN = "order_id";

    /**
     * The name of the foreign key constraint for the order.
     */
    public static final String FK_PAYMENT_ORDER = "fk_rakuten_payment_order";

    /**
     * The {@link RakutenOrder} to which this payment belongs. This establishes a
     * non-nullable, many-to-one relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = RakutenPayment.ORDER_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = RakutenPayment.FK_PAYMENT_ORDER))
    @Setter
    private RakutenOrder order;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected RakutenPayment() {
    }

    /**
     * Creates a new RakutenPayment with the specified method.
     *
     * @param method The payment method name.
     */
    public RakutenPayment(String method) {
        super(method);
    }
}
