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

import lombok.Data;

/**
 * DTO representing an individual bank transaction within a statement.
 */
@Data
public class BankStatementTransactionRequest {

    /** The transaction date (e.g. "YYYY-MM-DD"). */
    private String transactionDate; // "YYYY-MM-DD"
    /** The description or merchant name of the transaction. */
    private String description;
    /** The amount deposited into the account. */
    private Integer depositAmount;
    /** The amount withdrawn from the account. */
    private Integer withdrawalAmount;
    /** The account balance after the transaction. */
    private Integer balance;
    /** Additional memo or notes for the transaction. */
    private String memo;
}
