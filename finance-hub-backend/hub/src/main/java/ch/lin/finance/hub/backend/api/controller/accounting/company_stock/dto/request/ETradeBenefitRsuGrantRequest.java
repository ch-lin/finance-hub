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

import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueRequest;

/**
 * Request payload for an E-Trade RSU grant.
 *
 * @param grantNumber    The unique grant number or identifier.
 * @param grantDate      The date the RSUs were granted.
 * @param grantedQty     The total quantity of shares granted.
 * @param vestedQty      The total quantity of shares vested so far.
 * @param sellableQty    The total quantity of shares currently available to
 *                       sell.
 * @param estMarketValue The estimated market value of the grant.
 */
public record ETradeBenefitRsuGrantRequest(
        String grantNumber, // Maps to SaveETradeExtensionCommand.RsuGrant.grantNumber
        LocalDate grantDate, // Maps to SaveETradeExtensionCommand.RsuGrant.grantDate
        Double grantedQty, // Maps to SaveETradeExtensionCommand.RsuGrant.grantedQty
        Double vestedQty, // Maps to SaveETradeExtensionCommand.RsuGrant.vestedQty
        Double sellableQty, // Maps to SaveETradeExtensionCommand.RsuGrant.sellableQty
        MonetaryValueRequest estMarketValue) { // Maps to SaveETradeExtensionCommand.RsuGrant.estMarketValue
}
