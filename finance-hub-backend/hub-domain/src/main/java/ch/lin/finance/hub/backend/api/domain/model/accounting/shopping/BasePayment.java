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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping;

import java.math.BigDecimal;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all order payments in the finance hub.
 *
 * @param <O> The type of order associated with this payment.
 */
@MappedSuperclass
@Getter
public abstract class BasePayment<O extends BaseOrder<?, ?>> extends BaseEntity {

    /**
     * The name of the payment method column (e.g., "Visa", "Points",
     * "Voucher").
     */
    public static final String METHOD_COLUMN = "method";

    /**
     * The name of the payment amount column.
     */
    public static final String AMOUNT_COLUMN = "amount";

    /**
     * The payment method name.
     */
    @NotNull
    @Column(name = BasePayment.METHOD_COLUMN, nullable = false)
    private String method;

    /**
     * The amount paid using this method.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BasePayment.AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal amount = BigDecimal.ZERO;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected BasePayment() {
    }

    /**
     * Creates a new BasePayment with the specified method.
     *
     * @param method The payment method name.
     */
    protected BasePayment(String method) {
        this.method = method;
    }

    /**
     * @return The order this payment belongs to.
     */
    public abstract O getOrder();

    /**
     * Sets the order this payment belongs to.
     * 
     * @param order the order
     */
    public abstract void setOrder(O order);
}
