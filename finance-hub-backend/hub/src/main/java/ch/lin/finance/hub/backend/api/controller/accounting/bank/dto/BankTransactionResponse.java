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

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a response containing bank transaction details.
 */
@Data
@Builder
public class BankTransactionResponse {
    /** The unique identifier of the transaction. */
    private Long id;

    /** The name of the bank. */
    private String bankName;

    /** The date of the transaction. */
    private LocalDate transactionDate;

    /** The description of the transaction. */
    private String description;

    /** The amount deposited. */
    private Integer depositAmount;

    /** The amount withdrawn. */
    private Integer withdrawalAmount;

    /** The account balance after the transaction. */
    private Integer balance;

    /** Additional memo for the transaction. */
    private String memo;
}
