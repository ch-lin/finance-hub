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

import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyOverrideBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for individual items within an order.
 *
 * @param <O> The type of order this item belongs to.
 */
@MappedSuperclass
@Getter
public abstract class BaseOrderItem<O extends BaseOrder<?, ?>> extends PrivacyOverrideBaseEntity {

    /**
     * The name of the item name column.
     */
    public static final String NAME_COLUMN = "name";

    /**
     * The name of the quantity column.
     */
    public static final String QUANTITY_COLUMN = "quantity";

    /**
     * The name of the unit price column.
     */
    public static final String UNIT_PRICE_COLUMN = "unit_price";

    /**
     * The name of the category column.
     */
    public static final String CATEGORY_COLUMN = "category";

    /**
     * The name of the seller column.
     */
    public static final String SELLER_COLUMN = "seller";

    /**
     * The name of the item ID column.
     */
    public static final String ITEM_ID_COLUMN = "item_id";

    /**
     * The name or description of the purchased item.
     */
    @NotNull
    @Column(name = BaseOrderItem.NAME_COLUMN, nullable = false)
    private String name;

    /**
     * The number of units purchased for this item.
     */
    @NotNull
    @ColumnDefault("1")
    @Column(name = BaseOrderItem.QUANTITY_COLUMN, nullable = false)
    @Setter
    private Integer quantity = 1;

    /**
     * The price per single unit of this item.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrderItem.UNIT_PRICE_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * The category classification of this item.
     */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Setter
    private ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory category;

    /**
     * The seller of the item.
     */
    @Column(name = BaseOrderItem.SELLER_COLUMN)
    @Setter
    private String seller;

    /**
     * The platform-specific item ID (e.g., ASIN for Amazon).
     */
    @Column(name = BaseOrderItem.ITEM_ID_COLUMN)
    @Setter
    private String itemId;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected BaseOrderItem() {
    }

    /**
     * Creates a new BaseOrderItem with the specified name.
     *
     * @param name The name of the item.
     */
    protected BaseOrderItem(String name) {
        this.name = name;
    }

    /**
     * @return The order this item belongs to.
     */
    public abstract O getOrder();

    /**
     * Sets the order this item belongs to.
     * 
     * @param order the order
     */
    public abstract void setOrder(O order);

    /**
     * Merges incoming order item fact fields into this item:
     * - Overwrites quantity, unitPrice, seller, and itemId unconditionally.
     * - Strictly preserves user-managed fields (category, privacyLevel).
     *
     * @param incoming The parsed order item to merge facts from.
     */
    public void merge(BaseOrderItem<?> incoming) {
        if (incoming == null) {
            return;
        }
        this.name = incoming.getName();
        this.quantity = incoming.getQuantity();
        this.unitPrice = incoming.getUnitPrice();
        this.seller = incoming.getSeller();
        this.itemId = incoming.getItemId();
    }
}
