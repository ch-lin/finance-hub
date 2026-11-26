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
package ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage;

/**
 * Defines the repayment method for a mortgage.
 */
public enum RepaymentMethod {

    /**
     * 元利均等返済 (Equal Payment) - The total monthly payment (principal + interest)
     * remains constant.
     * Over time, the portion of the payment applied to the principal increases
     * while the interest portion decreases.
     */
    EQUAL_PAYMENT,

    /**
     * 元金均等返済 (Equal Principal) - The portion of the payment applied to the
     * principal remains constant.
     * The total monthly payment decreases over time as the interest portion
     * decreases.
     */
    EQUAL_PRINCIPAL
}
