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
package ch.lin.finance.hub.backend.api.controller.accounting.bank.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a request to create a bank transaction manually.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionRequest {
    /** The name of the bank. */
    @NotNull(message = "Bank name is required")
    private String bankName;

    /** The date of the transaction. */
    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    /** The description of the transaction. */
    @NotNull(message = "Description is required")
    private String description;

    /** The amount deposited. */
    private Integer depositAmount;

    /** The amount withdrawn. */
    private Integer withdrawalAmount;

    /** Additional memo for the transaction. */
    private String memo;
}
