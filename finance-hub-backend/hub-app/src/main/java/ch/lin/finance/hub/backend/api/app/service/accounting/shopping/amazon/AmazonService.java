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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon;

import java.util.Optional;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonOrderResult;

/**
 * A high-level service that orchestrates Amazon-related operations, such as
 * parsing order history files and persisting order data.
 */
public interface AmazonService {

    /**
     * Performs cleanup of application data, such as removing stale job
     * information or unreferenced entities.
     */
    void cleanup();

    /**
     * Saves or updates an Amazon order parsed from the Chrome Extension.
     *
     * @param command The parsed order to save
     * @return The saved order
     */
    AmazonOrderResult saveOrder(SaveAmazonOrderCommand command);

    /**
     * Retrieves an existing Amazon order by its unique order ID.
     *
     * @param orderId The Amazon order ID
     * @return An Optional containing the Amazon order if found, otherwise empty
     */
    Optional<AmazonOrderResult> getOrder(String orderId);

    /**
     * Retrieves an existing Amazon order by its unique order ID and eagerly fetches
     * its lazy collections (items, payments, shipments) to prevent
     * LazyInitializationException.
     *
     * @param orderId The Amazon order ID
     * @return An Optional containing the fully initialized Amazon order if found,
     *         otherwise empty
     */
    Optional<AmazonOrderResult> getOrderWithDetails(String orderId);
}
