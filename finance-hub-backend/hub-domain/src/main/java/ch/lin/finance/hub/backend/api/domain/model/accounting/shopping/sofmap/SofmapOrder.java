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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.BaseOrder;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Sofmap shopping order, including specific point usages,
 * and earned points. This extends the base order with Sofmap-specific
 * features.
 */
@Entity
@Table(name = SofmapOrder.TABLE_NAME, indexes = {
        @Index(name = SofmapOrder.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = SofmapOrder.ORDER_DATE_INDEX, columnList = BaseOrder.ORDER_DATE_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = BaseOrder.ORDER_ID_COLUMN)
})
@Getter
public class SofmapOrder extends BaseOrder<SofmapItem, SofmapPayment> {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "sofmap_order";

    public static final String ID_INDEX = "sofmap_order_id_idx";
    public static final String ORDER_DATE_INDEX = "sofmap_order_date_idx";

    /**
     * The name of the earned points column.
     */
    public static final String EARNED_POINTS_COLUMN = "earned_points";

    /**
     * The amount of points earned from this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = SofmapOrder.EARNED_POINTS_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal earnedPoints = BigDecimal.ZERO;

    /**
     * The list of purchased items associated with this Sofmap order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SofmapItem> items;

    /**
     * The list of payment methods used to fulfill this Sofmap order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SofmapPayment> payments;

    /**
     * The list of physical shipments related to this Sofmap order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SofmapShipment> shipments;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective instantiation.
     * However, from a Domain-Driven Design (DDD) perspective, an entity should never be created
     * in an invalid state without its required fields. Therefore, we use protected access
     * to satisfy JPA/Hibernate requirements while preventing arbitrary instantiation in business logic.
     */
    protected SofmapOrder() {
    }

    /**
     * Creates a new SofmapOrder with the specified order ID and date.
     *
     * @param orderId   The unique Sofmap order ID.
     * @param orderDate The date the order was placed.
     */
    public SofmapOrder(String orderId, LocalDate orderDate) {
        super(orderId, "SOFMAP", orderDate);
        items = new ArrayList<>();
        payments = new ArrayList<>();
        shipments = new ArrayList<>();
    }

    /**
     * Adds an item to this order and sets the order reference in the item.
     *
     * @param item The SofmapItem to add.
     */
    @Override
    public void addItem(SofmapItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Adds a payment method to this order and sets the order reference in the
     * payment.
     *
     * @param payment The SofmapPayment to add.
     */
    @Override
    public void addPayment(SofmapPayment payment) {
        payments.add(payment);
        payment.setOrder(this);
    }

    /**
     * Adds a shipment to this order and sets the order reference in the shipment.
     *
     * @param shipment The SofmapShipment to add.
     */
    public void addShipment(SofmapShipment shipment) {
        shipments.add(shipment);
        shipment.setOrder(this);
    }

    /**
     * Calculates the total value of all non-cash deductions (points, discounts).
     *
     * @return The sum of all deductions, treating nulls as zero.
     */
    public BigDecimal getTotalDeductions() {
        return getPointsUsed().add(getDiscounts());
    }

    /**
     * Merges the current order with data from a newly parsed order:
     * - Overwrites order-level facts (including shippingDate) unconditionally.
     * - Preserves accounting state (reconciledAmount).
     * - Merges items loosely by composite key, strictly preserving category and privacyLevel.
     * - Re-populates payments and shipments from incoming facts.
     *
     * @param incoming The newly parsed order containing updated information.
     */
    public void merge(SofmapOrder incoming) {
        if (incoming == null) {
            return;
        }
        super.merge(incoming);

        // Update SofmapOrder-specific fields directly using =
        this.earnedPoints = incoming.getEarnedPoints();

        this.payments.clear();
        if (incoming.getPayments() != null) {
            incoming.getPayments().forEach(this::addPayment);
        }

        this.shipments.clear();
        if (incoming.getShipments() != null) {
            incoming.getShipments().forEach(this::addShipment);
        }
    }
}
