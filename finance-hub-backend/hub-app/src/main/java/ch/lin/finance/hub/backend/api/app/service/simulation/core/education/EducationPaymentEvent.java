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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.education;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a discrete educational payment event in an absolute calendar month.
 * Pure domain value object with no external dependencies or ORM metadata.
 */
@Getter
@Builder
@EqualsAndHashCode
@ToString
public class EducationPaymentEvent implements Comparable<EducationPaymentEvent> {

    private final YearMonth paymentMonth;
    private final BigDecimal amount;
    private final PaymentType type;
    private final EducationStage stage;

    public EducationPaymentEvent(YearMonth paymentMonth, BigDecimal amount, PaymentType type, EducationStage stage) {
        this.paymentMonth = Objects.requireNonNull(paymentMonth, "paymentMonth must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
    }

    @Override
    public int compareTo(EducationPaymentEvent other) {
        int cmp = this.paymentMonth.compareTo(other.paymentMonth);
        if (cmp != 0) {
            return cmp;
        }
        return this.type.compareTo(other.type);
    }
}
