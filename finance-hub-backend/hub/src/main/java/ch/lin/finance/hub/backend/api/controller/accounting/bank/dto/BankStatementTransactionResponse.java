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

/**
 * Data Transfer Object representing a response for a bank transaction.
 *
 * @param id               The unique identifier of the transaction.
 * @param bankName         The name of the bank.
 * @param transactionDate  The date of the transaction.
 * @param description      The description or merchant name.
 * @param depositAmount    The amount deposited.
 * @param withdrawalAmount The amount withdrawn.
 * @param balance          The account balance after the transaction.
 * @param memo             Additional memo or notes.
 * @param categoryId       The identifier of the associated category, if any.
 */
public record BankStatementTransactionResponse(
        Long id,
        String bankName,
        LocalDate transactionDate,
        String description,
        Integer depositAmount,
        Integer withdrawalAmount,
        Integer balance,
        String memo,
        Long categoryId) {
}
