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

import java.util.List;

/**
 * Data Transfer Object representing a response for a bank statement.
 *
 * @param id             The unique identifier of the statement.
 * @param bankName       The name of the bank.
 * @param statementMonth The month of the statement (e.g., "2025-01").
 * @param transactions   The list of transactions associated with this
 *                       statement.
 */
public record BankStatementResponse(
        Long id,
        String bankName,
        String statementMonth,
        List<BankStatementTransactionResponse> transactions) {
}
