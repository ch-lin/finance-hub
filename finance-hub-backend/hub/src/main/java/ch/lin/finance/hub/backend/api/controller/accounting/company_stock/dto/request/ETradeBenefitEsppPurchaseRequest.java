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
 * Request payload for an E-Trade ESPP purchase.
 *
 * @param purchaseDate     The date the shares were purchased.
 * @param purchasePrice    The price paid per share.
 * @param purchasedQty     The total quantity of shares purchased.
 * @param sellableQty      The quantity of shares available to sell.
 * @param estMarketValue   The estimated market value of the purchased shares.
 * @param grantDate        The grant/offering date.
 * @param discountPercent  The discount percentage applied to the purchase.
 * @param grantDateFmv     The fair market value on the grant date.
 * @param purchaseDateFmv  The fair market value on the purchase date.
 * @param qualifiedPlan    Indicates if this is a qualified plan (e.g.,
 *                         tax-advantaged).
 * @param transferableDate The date the shares become transferable or sellable.
 */
public record ETradeBenefitEsppPurchaseRequest(
        LocalDate purchaseDate, // Maps to SaveETradeExtensionCommand.EsppPurchase.purchaseDate
        MonetaryValueRequest purchasePrice, // Maps to SaveETradeExtensionCommand.EsppPurchase.purchasePrice
        Double purchasedQty, // Maps to SaveETradeExtensionCommand.EsppPurchase.purchasedQty
        Double sellableQty, // Maps to SaveETradeExtensionCommand.EsppPurchase.sellableQty
        MonetaryValueRequest estMarketValue, // Maps to SaveETradeExtensionCommand.EsppPurchase.estMarketValue
        LocalDate grantDate, // Maps to SaveETradeExtensionCommand.EsppPurchase.grantDate
        Double discountPercent, // Maps to SaveETradeExtensionCommand.EsppPurchase.discountPercent
        MonetaryValueRequest grantDateFmv, // Maps to SaveETradeExtensionCommand.EsppPurchase.grantDateFmv
        MonetaryValueRequest purchaseDateFmv, // Maps to SaveETradeExtensionCommand.EsppPurchase.purchaseDateFmv
        Boolean qualifiedPlan, // Maps to SaveETradeExtensionCommand.EsppPurchase.qualifiedPlan
        LocalDate transferableDate) { // Maps to SaveETradeExtensionCommand.EsppPurchase.transferableDate
}
