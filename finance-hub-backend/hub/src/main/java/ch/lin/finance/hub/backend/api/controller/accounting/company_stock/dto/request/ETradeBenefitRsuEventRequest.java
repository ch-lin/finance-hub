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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request;

import java.time.LocalDate;

/**
 * Request payload for an E-Trade RSU event (e.g., Vest, Release).
 *
 * @param grantNumber The associated grant number.
 * @param eventDate   The date of the event.
 * @param eventType   The type of event (e.g., "Vest", "Release").
 * @param quantity    The quantity of shares involved in the event.
 */
public record ETradeBenefitRsuEventRequest(
        String grantNumber, // Maps to SaveETradeExtensionCommand.RsuEvent.grantNumber
        LocalDate eventDate, // Maps to SaveETradeExtensionCommand.RsuEvent.eventDate
        String eventType, // Maps to SaveETradeExtensionCommand.RsuEvent.eventType
        Double quantity // Maps to SaveETradeExtensionCommand.RsuEvent.quantity
) {
}
