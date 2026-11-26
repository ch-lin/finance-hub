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
package ch.lin.finance.hub.backend.api.controller.accounting.reconciliation;

/**
 * A DTO representing a request to link a credit card transaction to a shopping
 * order.
 *
 * @param creditCardTransactionId The ID of the credit card transaction.
 * @param amazonOrderId           The ID of the corresponding shopping (e.g.,
 *                                Amazon) order.
 */
public record LinkRequest(
        Long creditCardTransactionId,
        Long amazonOrderId) {

}
