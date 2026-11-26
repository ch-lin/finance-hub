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

import java.io.File;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;

/**
 * Defines the contract for parsing Rakuten order history pages (HTML) into
 * structured domain objects.
 */
public interface RakutenOrderParser {

    /**
     * Parses a Rakuten order HTML file into a {@link RakutenOrder} entity.
     *
     * @param htmlFile The HTML file containing the order details.
     * @return The parsed {@link RakutenOrder} with items and payments.
     * @throws Exception If parsing fails or mandatory fields are missing.
     */
    RakutenOrder parse(File htmlFile) throws Exception;
}
