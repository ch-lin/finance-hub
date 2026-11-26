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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto;

/**
 * Data Transfer Object representing a shipment in a Sofmap order response.
 *
 * @param shippingDate The date the order was shipped.
 * @param carrier The shipping carrier used.
 * @param trackingNumber The tracking number for the shipment.
 */
public record SofmapExtensionShipmentResponse(
    String shippingDate,
    String carrier,
    String trackingNumber
) {}
