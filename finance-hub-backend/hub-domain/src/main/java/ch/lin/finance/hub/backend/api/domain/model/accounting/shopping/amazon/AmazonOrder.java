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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an Amazon shopping order, including specific point usages,
 * gift cards, and discounts. This extends the base order with Amazon-specific
 * features.
 */
@Entity
@Table(name = AmazonOrder.TABLE_NAME, indexes = {
        @Index(name = AmazonOrder.ID_INDEX, columnList = BaseEntity.ID_COLUMN),
        @Index(name = AmazonOrder.ORDER_DATE_INDEX, columnList = BaseOrder.ORDER_DATE_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN, BaseOrder.ORDER_ID_COLUMN })
})
@Getter
public class AmazonOrder extends BaseOrder<AmazonItem, AmazonPayment> {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "amazon_order";

    public static final String ID_INDEX = "amazon_order_id_idx";
    public static final String ORDER_DATE_INDEX = "amazon_order_date_idx";

    /**
     * The name of the gift card amount column.
     */
    public static final String GIFT_CARD_AMOUNT_COLUMN = "gift_card_amount";

    /**
     * The name of the D points used column.
     */
    public static final String D_POINTS_USED_COLUMN = "d_points_used";

    /**
     * The amount of Amazon gift card balance applied to this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = AmazonOrder.GIFT_CARD_AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal giftCardAmount = BigDecimal.ZERO;

    /**
     * The amount of partner D points (docomo) used for this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = AmazonOrder.D_POINTS_USED_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal dPointsUsed = BigDecimal.ZERO;

    /**
     * The list of purchased items associated with this Amazon order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AmazonItem> items;

    /**
     * The list of payment methods used to fulfill this Amazon order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AmazonPayment> payments;

    /**
     * The list of physical shipments related to this Amazon order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AmazonShipment> shipments;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected AmazonOrder() {
    }

    /**
     * Creates a new AmazonOrder with the specified order ID and date.
     *
     * @param orderId   The unique Amazon order ID.
     * @param orderDate The date the order was placed.
     */
    public AmazonOrder(String orderId, LocalDate orderDate) {
        super(orderId, "AMAZON_JP", orderDate);
        items = new ArrayList<>();
        payments = new ArrayList<>();
        shipments = new ArrayList<>();
    }

    /**
     * Adds an item to this order and sets the order reference in the item.
     *
     * @param item The AmazonItem to add.
     */
    @Override
    public void addItem(AmazonItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Adds a payment method to this order and sets the order reference in the
     * payment.
     *
     * @param payment The AmazonPayment to add.
     */
    @Override
    public void addPayment(AmazonPayment payment) {
        payments.add(payment);
        payment.setOrder(this);
    }

    /**
     * Adds a shipment to this order and sets the order reference in the shipment.
     *
     * @param shipment The AmazonShipment to add.
     */
    public void addShipment(AmazonShipment shipment) {
        shipments.add(shipment);
        shipment.setOrder(this);
    }

    /**
     * Calculates the total value of all non-cash deductions (points, gift
     * cards, discounts).
     *
     * @return The sum of all deductions, treating nulls as zero.
     */
    public BigDecimal getTotalDeductions() {
        return getPointsUsed().add(giftCardAmount).add(dPointsUsed).add(getDiscounts());
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
    public void merge(AmazonOrder incoming) {
        if (incoming == null) {
            return;
        }
        super.merge(incoming);

        // Update AmazonOrder-specific fields directly using =
        this.giftCardAmount = incoming.getGiftCardAmount();
        this.dPointsUsed = incoming.getDPointsUsed();

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
