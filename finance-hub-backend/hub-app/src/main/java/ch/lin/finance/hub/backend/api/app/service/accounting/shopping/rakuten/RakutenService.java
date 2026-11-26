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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten;

import java.util.Optional;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;

/**
 * A high-level service that orchestrates Rakuten-related operations, such as
 * persisting order data from the Chrome Extension.
 */
public interface RakutenService {

    /**
     * Performs cleanup of application data, such as removing stale job
     * information or unreferenced entities.
     */
    void cleanup();

    /**
     * Saves or updates a Rakuten order parsed from the Chrome Extension.
     *
     * @param order The parsed order to save
     * @return The saved order
     */
    RakutenOrderResult saveOrder(RakutenOrder order);

    /**
     * Retrieves an existing Rakuten order by its unique order ID.
     *
     * @param orderId The Rakuten order ID
     * @return An Optional containing the Rakuten order if found, otherwise empty
     */
    Optional<RakutenOrderResult> getOrder(String orderId);

    /**
     * Retrieves an existing Rakuten order by its unique order ID and eagerly
     * fetches
     * its lazy collections (items) to prevent LazyInitializationException.
     *
     * @param orderId The Rakuten order ID
     * @return An Optional containing the fully initialized Rakuten order if found,
     *         otherwise empty
     */
    Optional<RakutenOrderResult> getOrderWithDetails(String orderId);
}
