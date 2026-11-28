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
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all order types in the finance hub. Provides common
 * properties such as dates, total amounts, and reconciliation status.
 *
 * @param <I> The type of order item associated with this order.
 * @param <P> The type of payment associated with this order.
 */
@MappedSuperclass
@Getter
public abstract class BaseOrder<I extends BaseOrderItem<?>, P extends BasePayment<?>> extends MultiUserBaseEntity {

    /**
     * The name of the order ID column in the database.
     */
    public static final String ORDER_ID_COLUMN = "order_id";

    /**
     * The name of the order source column in the database.
     */
    public static final String ORDER_SOURCE_COLUMN = "order_source";
    public static final String ORDER_DATE_COLUMN = "order_date";

    /**
     * The name of the shipping date column in the database.
     */
    public static final String SHIPPING_DATE_COLUMN = "shipping_date";

    /**
     * The name of the total amount column in the database.
     */
    public static final String TOTAL_AMOUNT_COLUMN = "total_amount";

    /**
     * The name of the billed amount column in the database.
     */
    public static final String BILLED_AMOUNT_COLUMN = "billed_amount";

    /**
     * The name of the shipping fee column in the database.
     */
    public static final String SHIPPING_FEE_COLUMN = "shipping_fee";

    /**
     * The name of the refund amount column in the database.
     */
    public static final String REFUND_AMOUNT_COLUMN = "refund_amount";

    /**
     * The name of the discounts column in the database.
     */
    public static final String DISCOUNTS_COLUMN = "discounts";

    /**
     * The name of the points used column in the database.
     */
    public static final String POINTS_USED_COLUMN = "points_used";

    /**
     * The name of the needs review column.
     */
    public static final String NEEDS_REVIEW_COLUMN = "needs_review";

    /**
     * The name of the review reason column.
     */
    public static final String REVIEW_REASON_COLUMN = "review_reason";

    /**
     * The name of the reconciled amount column.
     */
    public static final String RECONCILED_AMOUNT_COLUMN = "reconciled_amount";

    /**
     * The unique identifier for this order (e.g., the order number provided by
     * the source platform).
     */
    @NotNull
    @Column(name = BaseOrder.ORDER_ID_COLUMN, nullable = false)
    private String orderId;

    /**
     * The source platform from which this order originated (e.g., AMAZON_JP).
     */
    @NotNull
    @Column(name = BaseOrder.ORDER_SOURCE_COLUMN, nullable = false)
    private String orderSource;

    /**
     * The date this order was placed.
     */
    @NotNull
    @Column(name = BaseOrder.ORDER_DATE_COLUMN, nullable = false)
    private LocalDate orderDate;

    /**
     * The date this order was shipped.
     */
    @Column(name = BaseOrder.SHIPPING_DATE_COLUMN)
    @Setter
    private LocalDate shippingDate;

    /**
     * The gross total amount before any deductions.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.TOTAL_AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * The final amount charged to the credit card.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.BILLED_AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal billedAmount = BigDecimal.ZERO;

    /**
     * The shipping fee associated with the order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.SHIPPING_FEE_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal shippingFee = BigDecimal.ZERO;

    /**
     * The total amount refunded for this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.REFUND_AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /**
     * The total amount of promotional discounts applied to this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.DISCOUNTS_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal discounts = BigDecimal.ZERO;

    /**
     * The amount of platform points used for this order.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.POINTS_USED_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal pointsUsed = BigDecimal.ZERO;

    /**
     * Flag indicating if this order requires manual human review (e.g., due to
     * math mismatch).
     */
    @NotNull
    @ColumnDefault("false")
    @Column(name = BaseOrder.NEEDS_REVIEW_COLUMN, nullable = false)
    @Setter
    private Boolean needsReview = false;

    /**
     * A system-generated message describing why this order needs review.
     */
    @Column(name = BaseOrder.REVIEW_REASON_COLUMN, length = 1000)
    @Setter
    private String reviewReason;

    /**
     * The amount that has already been successfully reconciled against credit
     * card transactions.
     */
    @NotNull
    @ColumnDefault("0.00")
    @Column(name = BaseOrder.RECONCILED_AMOUNT_COLUMN, precision = 10, scale = 2, nullable = false)
    @Setter
    private BigDecimal reconciledAmount = BigDecimal.ZERO;

    /**
     * Hibernate requires a no-argument constructor for proxying and reflective
     * instantiation. However, from a Domain-Driven Design (DDD) perspective, an
     * entity should never be created in an invalid state without its required
     * fields. Therefore, we use protected access to satisfy JPA/Hibernate
     * requirements while preventing arbitrary instantiation in business logic.
     */
    protected BaseOrder() {
    }

    /**
     * Creates a new BaseOrder with the specified ID, source, and date.
     *
     * @param orderId     The unique order identifier.
     * @param orderSource The source platform of the order.
     * @param orderDate   The date the order was placed.
     */
    protected BaseOrder(String orderId, String orderSource, LocalDate orderDate) {
        this.orderId = orderId;
        this.orderSource = orderSource;
        this.orderDate = orderDate;
    }

    /**
     * Calculates the remaining balance of the order that has not yet been
     * matched to a credit card transaction.
     *
     * @return The unreconciled balance.
     */
    public BigDecimal getUnreconciledBalance() {
        return billedAmount.subtract(reconciledAmount);
    }

    /**
     * Flags this order for manual review with a required reason.
     * 
     * @param reason The reason this order needs review.
     */
    public void flagForReview(String reason) {
        this.needsReview = true;
        this.reviewReason = reason;
    }

    /**
     * @return The list of items in this order.
     */
    public abstract List<I> getItems();

    /**
     * @return The list of payments in this order.
     */
    public abstract List<P> getPayments();

    /**
     * Adds an item to the order.
     * 
     * @param item the item to add
     */
    public abstract void addItem(I item);

    /**
     * Adds a payment method to the order.
     * 
     * @param payment the payment to add
     */
    public abstract void addPayment(P payment);

    /**
     * Merges incoming order items into this order:
     * - Matches existing items by composite key: name and unitPrice.
     * - If matched, updates facts (quantity, seller, itemId) while preserving user
     * fields (category, privacyLevel).
     * - If unmatched, adds as a new item to this order.
     * - Leaves unmatched existing items intact (zero deletion).
     *
     * @param incomingItems The parsed items to merge into this order.
     */
    public void mergeItems(List<I> incomingItems) {
        if (incomingItems == null || incomingItems.isEmpty()) {
            return;
        }
        List<I> existingItems = getItems();
        for (I incoming : incomingItems) {
            I matched = existingItems.stream()
                    .filter(item -> item.getName() != null && item.getName().equals(incoming.getName())
                            && item.getUnitPrice() != null && incoming.getUnitPrice() != null
                            && item.getUnitPrice().compareTo(incoming.getUnitPrice()) == 0)
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                matched.merge(incoming);
            } else {
                this.addItem(incoming);
            }
        }
    }

    /**
     * Merges incoming order fact fields into this order:
     * - Overwrites all order-level facts unconditionally using direct assignment.
     * - Strictly preserves accounting state (reconciledAmount).
     * - Merges items loosely by composite key, preserving category and
     * privacyLevel.
     *
     * @param incoming The parsed order to merge facts from.
     */
    public void merge(BaseOrder<I, ?> incoming) {
        if (incoming == null) {
            return;
        }
        this.orderDate = incoming.getOrderDate();
        this.shippingDate = incoming.getShippingDate();
        this.totalAmount = incoming.getTotalAmount();
        this.billedAmount = incoming.getBilledAmount();
        this.shippingFee = incoming.getShippingFee();
        this.refundAmount = incoming.getRefundAmount();
        this.discounts = incoming.getDiscounts();
        this.pointsUsed = incoming.getPointsUsed();
        this.needsReview = incoming.getNeedsReview();
        this.reviewReason = incoming.getReviewReason();
        this.mergeItems(incoming.getItems());
    }
}
