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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap;

import java.util.Optional;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

/**
 * A high-level service that orchestrates Sofmap-related operations.
 */
public interface SofmapService {

    /**
     * Saves or updates a Sofmap order parsed from the Chrome Extension.
     *
     * @param order The parsed order to save
     * @return The saved order
     */
    SofmapOrderResult saveOrder(SofmapOrder order);

    /**
     * Retrieves an existing Sofmap order by its unique order ID.
     *
     * @param orderId The Sofmap order ID
     * @return An Optional containing the Sofmap order if found, otherwise empty
     */
    Optional<SofmapOrderResult> getOrder(String orderId);

    /**
     * Retrieves an existing Sofmap order by its unique order ID and eagerly fetches
     * its lazy collections (items, payments, shipments) to prevent
     * LazyInitializationException.
     *
     * @param orderId The Sofmap order ID
     * @return An Optional containing the fully initialized Sofmap order if found,
     *         otherwise empty
     */
    Optional<SofmapOrderResult> getOrderWithDetails(String orderId);
}
