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

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

/**
 * Repository interface for managing {@link SofmapOrder} entities.
 */
@Repository
public interface SofmapOrderRepository extends JpaRepository<SofmapOrder, Long> {

    /**
     * Retrieves an order by its unique business order ID.
     *
     * @param orderId The unique order ID.
     * @return An Optional containing the SofmapOrder if found.
     */
    Optional<SofmapOrder> findByOrderId(String orderId);

    /**
     * Retrieves an order by its ID, eagerly fetching its associated items,
     * payments, and shipments using an EntityGraph to avoid N+1 queries.
     *
     * @param orderId The unique order ID.
     * @return An Optional containing the fully initialized SofmapOrder if found.
     */
    @EntityGraph(attributePaths = { "items", "payments", "shipments" })
    Optional<SofmapOrder> findWithDetailsByOrderId(String orderId);

    Optional<SofmapOrder> findByOwnerUserIdAndOrderId(String ownerUserId, String orderId);

    @EntityGraph(attributePaths = { "items" })
    Optional<SofmapOrder> findWithDetailsByOwnerUserIdAndOrderId(String ownerUserId, String orderId);
}
