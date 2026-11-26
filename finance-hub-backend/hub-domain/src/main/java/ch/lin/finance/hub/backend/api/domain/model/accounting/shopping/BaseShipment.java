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

import java.time.LocalDate;

import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all order shipments in the finance hub.
 */
@MappedSuperclass
@Getter
public abstract class BaseShipment extends BaseEntity {

    public static final String SHIPPING_DATE_COLUMN = "shipping_date";
    public static final String CARRIER_COLUMN = "carrier";
    public static final String TRACKING_NUMBER_COLUMN = "tracking_number";

    /**
     * The date when this shipment was dispatched.
     */
    @Column(name = SHIPPING_DATE_COLUMN)
    @Setter
    private LocalDate shippingDate;

    /**
     * The carrier handling this shipment.
     */
    @Column(name = CARRIER_COLUMN)
    @Setter
    private String carrier;

    /**
     * The tracking number for this shipment.
     */
    @Column(name = TRACKING_NUMBER_COLUMN)
    @Setter
    private String trackingNumber;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected BaseShipment() {
    }

    /**
     * Creates a new BaseShipment with the specified details.
     *
     * @param shippingDate   The date the shipment was dispatched.
     * @param carrier        The carrier handling the shipment.
     * @param trackingNumber The tracking number for the shipment.
     */
    protected BaseShipment(LocalDate shippingDate, String carrier, String trackingNumber) {
        this.shippingDate = shippingDate;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }
}
