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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto;

/**
 * Data Transfer Object representing a shipment detail for an Amazon order
 * received from the Chrome Extension.
 *
 * @param shippingDate The date the shipment was dispatched.
 * @param carrier The carrier responsible for the shipment (e.g., Yamato, Sagawa).
 * @param trackingNumber The tracking number provided by the carrier.
 */
public record AmazonExtensionShipmentRequest(
    String shippingDate,
    String carrier,
    String trackingNumber
) {}
