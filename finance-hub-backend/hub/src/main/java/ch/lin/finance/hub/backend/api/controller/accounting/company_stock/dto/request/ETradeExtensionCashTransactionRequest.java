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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for a single E-Trade cash transaction (e.g., dividend,
 * transfer).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ETradeExtensionCashTransactionRequest {

    /** The date the transaction occurred. */
    @NotNull
    private LocalDate transactionDate; // Maps to SaveETradeExtensionCommand.CashTransaction.transactionDate

    /** The description or type of the transaction. */
    @NotNull
    private String description; // Maps to SaveETradeExtensionCommand.CashTransaction.description

    /** The monetary amount of the transaction. */
    @NotNull
    private MonetaryValueRequest amount; // Maps to SaveETradeExtensionCommand.CashTransaction.amount
}
