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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;

/**
 * Spring Data JPA repository for {@link RakutenOrder} entities.
 */
@Repository
public interface RakutenOrderRepository
        extends JpaRepository<RakutenOrder, Long>, JpaSpecificationExecutor<RakutenOrder> {

    /**
     * Deletes all records from the 'rakuten_order' table.
     * <p>
     * This custom bulk delete operation bypasses the persistence context,
     * performing a direct SQL {@code DELETE} statement.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM RakutenOrder ro")
    void cleanTable();

    /**
     * Resets the sequence for the 'rakuten_order' table's primary key.
     * <p>
     * <b>Note:</b> Alters the MySQL {@code AUTO_INCREMENT} state to 1.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE rakuten_order AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Finds a {@link RakutenOrder} by its unique Rakuten-provided order ID.
     *
     * @param orderId The unique Rakuten order identifier.
     * @return An {@link Optional} containing the found order, or an empty
     *         Optional if no order matches the ID.
     */
    Optional<RakutenOrder> findByOrderId(String orderId);

    /**
     * Retrieves a Rakuten order by its ID, eagerly fetching its associated items,
     * payments, and shipments using an EntityGraph to avoid N+1 queries.
     *
     * @param orderId The unique Rakuten order ID.
     * @return An Optional containing the fully initialized RakutenOrder if found.
     */
    @EntityGraph(attributePaths = { "items", "payments", "shipments" })
    Optional<RakutenOrder> findWithDetailsByOrderId(String orderId);

    /**
     * Finds all {@link RakutenOrder} entities whose Rakuten order IDs are present
     * in the
     * given list.
     *
     * @param orderIds A list of Rakuten order identifiers to search for.
     * @return A list of matching {@link RakutenOrder} entities.
     */
    List<RakutenOrder> findAllByOrderIdIn(List<String> orderIds);

    /**
     * Finds all Rakuten orders placed within a specific date range.
     *
     * @param startDate The start date (inclusive) for filtering the orders.
     * @param endDate   The end date (inclusive) for filtering the orders.
     * @return A list of {@link RakutenOrder} entities that fall within the
     *         specified date range.
     */
    List<RakutenOrder> findByOrderDateBetween(LocalDate startDate, LocalDate endDate);

    Optional<RakutenOrder> findByOwnerUserIdAndOrderId(String ownerUserId, String orderId);

    @EntityGraph(attributePaths = { "items" })
    Optional<RakutenOrder> findWithDetailsByOwnerUserIdAndOrderId(String ownerUserId, String orderId);
}
