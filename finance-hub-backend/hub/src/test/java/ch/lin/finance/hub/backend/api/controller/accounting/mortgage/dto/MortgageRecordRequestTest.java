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
package ch.lin.finance.hub.backend.api.controller.accounting.mortgage.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.controller.accounting.mortgage.dto.MortgageRecordRequest.MortgageEarlyRepaymentRequest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class MortgageRecordRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private MortgageRecordRequest createBaseRequest(LocalDate startDate, List<MortgageEarlyRepaymentRequest> earlyRepayments) {
        return new MortgageRecordRequest(
                "ソニー銀行",
                "1",
                "Home Loan",
                30_000_000L,
                25_000_000L,
                LocalDate.now(),
                startDate,
                startDate.plusYears(35),
                420,
                0.5,
                InterestRateType.VARIABLE,
                RepaymentMethod.EQUAL_PAYMENT,
                80_000L,
                List.of(),
                null,
                null,
                null,
                false,
                null,
                null,
                true,
                earlyRepayments
        );
    }

    @Test
    @DisplayName("Should pass validation when earlyRepayments is null or empty")
    void testNullOrEmptyEarlyRepayments_Passes() {
        MortgageRecordRequest request1 = createBaseRequest(LocalDate.now().minusYears(1), null);
        Set<ConstraintViolation<MortgageRecordRequest>> violations1 = validator.validate(request1);
        assertThat(violations1).isEmpty();

        MortgageRecordRequest request2 = createBaseRequest(LocalDate.now().minusYears(1), List.of());
        Set<ConstraintViolation<MortgageRecordRequest>> violations2 = validator.validate(request2);
        assertThat(violations2).isEmpty();
    }

    @Test
    @DisplayName("Existing mortgage (startDate in the past): repaymentDate in the future should pass")
    void testExistingMortgage_FutureRepayment_Passes() {
        LocalDate startDate = LocalDate.now().minusYears(2);
        LocalDate futureRepayment = LocalDate.now().plusMonths(6);
        MortgageEarlyRepaymentRequest er = new MortgageEarlyRepaymentRequest(
                null, futureRepayment, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        MortgageRecordRequest request = createBaseRequest(startDate, List.of(er));

        Set<ConstraintViolation<MortgageRecordRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Existing mortgage: repaymentDate in the past should fail validation")
    void testExistingMortgage_PastRepayment_Fails() {
        LocalDate startDate = LocalDate.now().minusYears(2);
        LocalDate pastRepayment = LocalDate.now().minusDays(1);
        MortgageEarlyRepaymentRequest er = new MortgageEarlyRepaymentRequest(
                null, pastRepayment, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        MortgageRecordRequest request = createBaseRequest(startDate, List.of(er));

        Set<ConstraintViolation<MortgageRecordRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Early repayment date cannot be earlier than mortgage start date or today");
    }

    @Test
    @DisplayName("Future mortgage (startDate in the future): repaymentDate after startDate should pass")
    void testFutureMortgage_RepaymentAfterStartDate_Passes() {
        LocalDate startDate = LocalDate.now().plusMonths(6);
        LocalDate repaymentDate = startDate.plusMonths(1);
        MortgageEarlyRepaymentRequest er = new MortgageEarlyRepaymentRequest(
                null, repaymentDate, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        MortgageRecordRequest request = createBaseRequest(startDate, List.of(er));

        Set<ConstraintViolation<MortgageRecordRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Future mortgage: repaymentDate between today and startDate should fail validation")
    void testFutureMortgage_RepaymentBeforeStartDate_Fails() {
        LocalDate startDate = LocalDate.now().plusMonths(6);
        LocalDate repaymentDate = LocalDate.now().plusMonths(1); // Future relative to today, but before mortgage starts
        MortgageEarlyRepaymentRequest er = new MortgageEarlyRepaymentRequest(
                null, repaymentDate, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        MortgageRecordRequest request = createBaseRequest(startDate, List.of(er));

        Set<ConstraintViolation<MortgageRecordRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Early repayment date cannot be earlier than mortgage start date or today");
    }
}
