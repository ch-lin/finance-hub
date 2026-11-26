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
package ch.lin.finance.hub.backend.api.domain.model.config.scheduler;

/**
 * Defines the supported transaction sources for the Finance Hub.
 */
public enum TransactionSource {
    /**
     * Amazon shopping order and item data.
     */
    AMAZON,

    /**
     * Rakuten shopping order and item data.
     */
    RAKUTEN,

    /**
     * Credit card statements and transactions.
     */
    CREDIT_CARD,

    /**
     * Bank statements and transactions.
     */
    BANK,

    /**
     * Salary statements from payroll.
     */
    SALARY,

    /**
     * NISA (Nippon Individual Savings Account) investments.
     */
    NISA,

    /**
     * Public Pension (Nenkin).
     */
    PENSION,

    /**
     * Defined Contribution (DC) / iDeCo pensions.
     */
    DEFINED_CONTRIBUTION,

    /**
     * Company Stock (RSU, ESPP) from brokers like E-Trade.
     */
    COMPANY_STOCK,

    /**
     * Insurance policies (e.g., Life, Nursing, Earthquake).
     */
    INSURANCE,

    /**
     * Mortgage records and histories.
     */
    MORTGAGE,

    /**
     * Foreign exchange rate history statements (e.g. MURC TTM).
     */
    EXCHANGE_RATE
}
