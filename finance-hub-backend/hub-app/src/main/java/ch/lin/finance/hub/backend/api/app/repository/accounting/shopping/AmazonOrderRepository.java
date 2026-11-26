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
package ch.lin.finance.hub.backend.api.app.repository.accounting.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

/**
 * Spring Data JPA repository for {@link AmazonOrder} entities.
 * <p>
 * This interface provides the mechanism for CRUD operations and advanced
 * querying
 * on Amazon orders, leveraging standard methods provided by
 * {@link JpaRepository}
 * and {@link JpaSpecificationExecutor}.
 */
@Repository
public interface AmazonOrderRepository extends JpaRepository<AmazonOrder, Long>, JpaSpecificationExecutor<AmazonOrder> {

    /**
     * Deletes all records from the 'amazon_order' table.
     * <p>
     * This custom bulk delete operation is intended for cleanup or reset
     * purposes. It is more efficient than standard JPA deletion because it
     * bypasses the persistence context and issues a direct SQL `DELETE`
     * statement.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM AmazonOrder ao")
    void cleanTable();

    /**
     * Resets the sequence for the 'amazon_order' table's primary key.
     * <p>
     * <b>Note:</b> This is a native SQL query for resetting the
     * {@code AUTO_INCREMENT} value on the primary key column, commonly used in
     * databases like MySQL. It should be used with caution, typically after
     * cleaning the table, to reset ID generation.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE amazon_order AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Finds an {@link AmazonOrder} by its unique Amazon-provided order ID.
     * <p>
     * This query is utilized primarily to check for existing orders and prevent
     * duplications when processing imports from new CSV files.
     *
     * @param orderId The unique Amazon order identifier (e.g.,
     *                "114-1234567-1234567").
     * @return An {@link Optional} containing the found order, or an empty
     *         Optional if no order matches the ID.
     */
    Optional<AmazonOrder> findByOrderId(String orderId);

    /**
     * Retrieves an Amazon order by its ID, eagerly fetching its associated items,
     * payments, and shipments using an EntityGraph to avoid N+1 queries.
     *
     * @param orderId The unique Amazon order ID.
     * @return An Optional containing the fully initialized AmazonOrder if found.
     */
    @EntityGraph(attributePaths = { "items", "payments", "shipments" })
    Optional<AmazonOrder> findWithDetailsByOrderId(String orderId);

    /**
     * Finds all {@link AmazonOrder} entities whose Amazon order IDs are present in
     * the
     * given list.
     * <p>
     * This is useful for batch operations, such as identifying which orders from a
     * newly
     * uploaded CSV already exist in the database.
     *
     * @param orderIds A list of Amazon order identifiers to search for.
     * @return A list of matching {@link AmazonOrder} entities.
     */
    List<AmazonOrder> findAllByOrderIdIn(List<String> orderIds);

    /**
     * Finds all Amazon orders placed within a specific date range.
     * <p>
     * This is primarily used for report generation and filtering transactions
     * when creating statements or summaries over a given period.
     *
     * @param startDate The start date (inclusive) for filtering the orders.
     * @param endDate   The end date (inclusive) for filtering the orders.
     * @return A list of {@link AmazonOrder} entities that fall within the specified
     *         date range.
     */
    @EntityGraph(attributePaths = {"items", "payments"})
    List<AmazonOrder> findByOrderDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Searches for unmatched Amazon orders based on optional criteria.
     *
     * @param orderId   Optional partial Amazon order ID.
     * @param minAmount Optional minimum unreconciled balance.
     * @param maxAmount Optional maximum unreconciled balance.
     * @return A list of matching Amazon orders.
     */
    @Query("SELECT o FROM AmazonOrder o " +
            "WHERE (o.billedAmount - o.reconciledAmount) > 0 " +
            "AND NOT EXISTS (SELECT 1 FROM TransactionLink t WHERE t.amazonOrder.id = o.id) " +
            "AND (:orderId IS NULL OR o.orderId LIKE concat('%', :orderId, '%')) " +
            "AND (:minAmount IS NULL OR (o.billedAmount - o.reconciledAmount) >= :minAmount) " +
            "AND (:maxAmount IS NULL OR (o.billedAmount - o.reconciledAmount) <= :maxAmount) " +
            "ORDER BY o.orderDate DESC")
    List<AmazonOrder> searchUnmatchedOrders(
            @Param("orderId") String orderId,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);
}
