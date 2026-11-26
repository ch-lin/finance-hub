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

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.BaseShipment;
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
 * Represents a physical shipment associated with an Amazon order.
 */
@Entity
@Table(name = AmazonShipment.TABLE_NAME, indexes = {
        @Index(name = AmazonShipment.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = AmazonShipment.ORDER_ID_INDEX, columnList = AmazonShipment.ORDER_ID_COLUMN)
})
@Getter
public class AmazonShipment extends BaseShipment {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "amazon_shipment";

    public static final String ID_INDEX = "amazon_shipment_id_idx";
    public static final String ORDER_ID_INDEX = "amazon_shipment_order_id_idx";
    /**
     * The name of the order ID column used for joining.
     */
    public static final String ORDER_ID_COLUMN = "order_id";
    /**
     * The name of the foreign key constraint for the order.
     */
    public static final String FK_SHIPMENT_ORDER = "fk_shipment_order";

    /**
     * The {@link AmazonOrder} to which this shipment belongs. This establishes a
     * non-nullable, many-to-one relationship.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = ORDER_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = FK_SHIPMENT_ORDER))
    @Setter(lombok.AccessLevel.PACKAGE)
    private AmazonOrder order;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected AmazonShipment() {
    }

    /**
     * Creates a new AmazonShipment with the specified details.
     *
     * @param shippingDate   The date the shipment was dispatched.
     * @param carrier        The carrier handling the shipment.
     * @param trackingNumber The tracking number for the shipment.
     */
    public AmazonShipment(LocalDate shippingDate, String carrier, String trackingNumber) {
        super(shippingDate, carrier, trackingNumber);
    }
}
