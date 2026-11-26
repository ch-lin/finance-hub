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

import java.io.File;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

/**
 * Defines the contract for parsing Sofmap order history pages (HTML) into
 * structured domain objects.
 */
public interface SofmapOrderParser {

    /**
     * Parses a Sofmap order HTML file into a {@link SofmapOrder} entity.
     *
     * @param htmlFile The HTML file containing the order details.
     * @return The parsed {@link SofmapOrder} with items, payments, and shipments.
     * @throws Exception If parsing fails or mandatory fields are missing.
     */
    SofmapOrder parse(File htmlFile) throws Exception;
}
