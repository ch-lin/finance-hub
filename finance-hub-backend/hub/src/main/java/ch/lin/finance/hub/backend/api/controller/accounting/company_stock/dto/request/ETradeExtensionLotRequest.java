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
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;

/**
 * Request payload for a single E-Trade stock lot.
 *
 * @param recordType       The type of the record (e.g., ESPP, RSU).
 * @param symbol           The stock ticker symbol.
 * @param recordDate       The date of the lot acquisition.
 * @param price            The purchase or acquisition price.
 * @param quantity         The total quantity of shares.
 * @param sellableQuantity The quantity of shares available to sell.
 * @param estMarketValue   The estimated market value of the lot.
 * @param expectedGainLoss The expected gain or loss.
 * @param discountPercent  The discount percentage applied (e.g., for ESPP).
 */
public record ETradeExtensionLotRequest(
        ETradeRecordType recordType, // Maps to SaveETradeExtensionCommand.Lot.recordType
        String symbol, // Maps to SaveETradeExtensionCommand.Lot.symbol
        LocalDate recordDate, // Maps to SaveETradeExtensionCommand.Lot.recordDate
        MonetaryValueRequest price, // Maps to SaveETradeExtensionCommand.Lot.price
        Double quantity, // Maps to SaveETradeExtensionCommand.Lot.quantity
        Double sellableQuantity, // Maps to SaveETradeExtensionCommand.Lot.sellableQuantity
        MonetaryValueRequest estMarketValue, // Maps to SaveETradeExtensionCommand.Lot.estMarketValue
        MonetaryValueRequest expectedGainLoss, // Maps to SaveETradeExtensionCommand.Lot.expectedGainLoss
        Double discountPercent) { // Maps to SaveETradeExtensionCommand.Lot.discountPercent
}
