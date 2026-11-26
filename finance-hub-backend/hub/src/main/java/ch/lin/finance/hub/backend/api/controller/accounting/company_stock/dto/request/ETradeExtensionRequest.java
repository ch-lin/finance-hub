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
import java.util.List;

import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueRequest;
import jakarta.validation.Valid;

/**
 * Request payload from the E-Trade Chrome Extension containing snapshot data.
 *
 * @param recordDate            The date the snapshot was recorded.
 * @param totalAccountValue     The total account value.
 * @param currentAccountValue   The current account value.
 * @param potentialBenefitValue The potential benefit value from unvested RSUs
 *                              or ESPP.
 * @param currency              The fallback currency for the payload.
 * @param esppPurchasedQty      The total purchased ESPP quantity.
 * @param rsGrantedQty          The total restricted stock granted quantity.
 * @param exchangeRate          The exchange rate applied, if any.
 * @param lots                  List of stock lots.
 * @param cashTransactions      List of cash transactions.
 * @param rsuGrants             List of RSU grants.
 * @param rsuEvents             List of RSU events (e.g., vesting).
 * @param rsuVestSchedules      List of RSU vest schedules.
 * @param esppPurchases         List of ESPP purchases.
 */
public record ETradeExtensionRequest(
        LocalDate recordDate, // Maps to SaveETradeExtensionCommand.recordDate
        MonetaryValueRequest totalAccountValue, // Maps to SaveETradeExtensionCommand.totalAccountValue
        MonetaryValueRequest currentAccountValue, // Maps to SaveETradeExtensionCommand.currentAccountValue
        MonetaryValueRequest potentialBenefitValue, // Maps to SaveETradeExtensionCommand.potentialBenefitValue
        String currency, // Optional fallback currency for payload
        Double esppPurchasedQty, // Maps to SaveETradeExtensionCommand.esppPurchasedQty
        Double rsGrantedQty, // Maps to SaveETradeExtensionCommand.rsGrantedQty
        Double exchangeRate, // Maps to SaveETradeExtensionCommand.exchangeRate
        @Valid List<ETradeExtensionLotRequest> lots, // Maps to SaveETradeExtensionCommand.lots
        @Valid List<ETradeExtensionCashTransactionRequest> cashTransactions, // Maps to
                                                                             // SaveETradeExtensionCommand.cashTransactions
        @Valid List<ETradeBenefitRsuGrantRequest> rsuGrants, // Maps to SaveETradeExtensionCommand.rsuGrants
        @Valid List<ETradeBenefitRsuEventRequest> rsuEvents, // Maps to SaveETradeExtensionCommand.rsuEvents
        @Valid List<ETradeBenefitRsuVestScheduleRequest> rsuVestSchedules, // Maps to
                                                                           // SaveETradeExtensionCommand.rsuVestSchedules
        @Valid List<ETradeBenefitEsppPurchaseRequest> esppPurchases) { // Maps to
                                                                       // SaveETradeExtensionCommand.esppPurchases
}
