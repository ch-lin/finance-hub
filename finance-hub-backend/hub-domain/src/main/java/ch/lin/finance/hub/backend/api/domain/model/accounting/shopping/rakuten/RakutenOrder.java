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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.BaseOrder;
import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Rakuten shopping order, including specific point usages,
 * and discounts. This extends the base order with Rakuten-specific features.
 */
@Entity
@Table(name = RakutenOrder.TABLE_NAME, indexes = {
        @Index(name = RakutenOrder.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = RakutenOrder.ORDER_DATE_INDEX, columnList = BaseOrder.ORDER_DATE_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN, BaseOrder.ORDER_ID_COLUMN })
})
@Getter
public class RakutenOrder extends BaseOrder<RakutenItem, RakutenPayment> {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "rakuten_order";

    public static final String ID_INDEX = "rakuten_order_id_idx";
    public static final String ORDER_DATE_INDEX = "rakuten_order_date_idx";

    /**
     * Additional shop references if necessary.
     */
    public static final String SHOP_ID_COLUMN = "shop_id";

    /**
     * The shop ID where the order was placed.
     */
    @Setter
    private String shopId;

    /**
     * The tax amount applied to this Rakuten order.
     */
    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Setter
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /**
     * The list of purchased items associated with this Rakuten order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RakutenItem> items;

    /**
     * The list of payment methods used to fulfill this Rakuten order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RakutenPayment> payments;

    /**
     * The list of physical shipments for this order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RakutenShipment> shipments;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected RakutenOrder() {
    }

    /**
     * Creates a new RakutenOrder with the specified order ID and date.
     *
     * @param orderId   The unique Rakuten order ID.
     * @param orderDate The date the order was placed.
     */
    public RakutenOrder(String orderId, LocalDate orderDate) {
        super(orderId, "RAKUTEN", orderDate);
        items = new ArrayList<>();
        payments = new ArrayList<>();
        shipments = new ArrayList<>();
    }

    /**
     * Adds an item to this order and sets the order reference in the item.
     *
     * @param item The RakutenItem to add.
     */
    @Override
    public void addItem(RakutenItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Adds a payment method to this order and sets the order reference in the
     * payment.
     *
     * @param payment The RakutenPayment to add.
     */
    @Override
    public void addPayment(RakutenPayment payment) {
        payments.add(payment);
        payment.setOrder(this);
    }

    /**
     * Adds a shipment to this order and sets the order reference in the shipment.
     *
     * @param shipment The RakutenShipment to add.
     */
    public void addShipment(RakutenShipment shipment) {
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
     * - Merges items loosely by composite key, strictly preserving category and
     * privacyLevel.
     * - Re-populates payments and shipments from incoming facts.
     *
     * @param incoming The newly parsed order containing updated information.
     */
    public void merge(RakutenOrder incoming) {
        if (incoming == null) {
            return;
        }
        super.merge(incoming);

        // Update RakutenOrder-specific fields directly using =
        this.shopId = incoming.getShopId();
        this.taxAmount = incoming.getTaxAmount();

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
