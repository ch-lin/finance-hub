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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.BaseOrderItem;
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
 * Represents a single item within an Amazon order.
 */
@Entity
@Table(name = AmazonItem.TABLE_NAME, indexes = {
        @Index(name = AmazonItem.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = AmazonItem.ORDER_ID_INDEX, columnList = AmazonItem.ORDER_ID_COLUMN)
})
@Getter
public class AmazonItem extends BaseOrderItem<AmazonOrder> {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "amazon_item";

    public static final String ID_INDEX = "amazon_item_id_idx";
    public static final String ORDER_ID_INDEX = "amazon_item_order_id_idx";

    /**
     * The name of the order ID column used for joining.
     */
    public static final String ORDER_ID_COLUMN = "order_id";

    /**
     * The name of the foreign key constraint for the order.
     */
    public static final String FK_ITEM_ORDER = "fk_item_order";

    /**
     * The {@link AmazonOrder} to which this item belongs. This establishes a
     * non-nullable, many-to-one relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = AmazonItem.ORDER_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = AmazonItem.FK_ITEM_ORDER))
    @Setter
    private AmazonOrder order;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected AmazonItem() {
    }

    /**
     * Creates a new AmazonItem with the specified name.
     *
     * @param name The name of the item.
     */
    public AmazonItem(String name) {
        super(name);
    }
}
