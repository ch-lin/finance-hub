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

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MortgageRecordRequest(
        String bankName,
        String contractNumber,
        @NotBlank String loanName,
        @NotNull @Positive Long totalLoanAmount,
        @NotNull @Positive Long currentBalance,
        LocalDate balanceRecordDate,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @Positive Integer loanTermMonths,
        @NotNull Double interestRate,
        @NotNull InterestRateType rateType,
        @NotNull RepaymentMethod repaymentMethod,
        @NotNull @Positive Long monthlyPayment,
        @Valid List<BonusPaymentRequest> bonusPayments,
        LocalDate occupancyDate,
        ResidenceType residenceType,
        PropertyType propertyType,
        Boolean taxDeductionEligible,
        Double taxDeductionRate,
        Long maxTaxDeductionAmount,
        Boolean hasFiveYearRule,
        @Valid List<MortgageEarlyRepaymentRequest> earlyRepayments) {

    @JsonIgnore
    @AssertTrue(message = "Early repayment date cannot be earlier than mortgage start date or today")
    public boolean isEarlyRepaymentsDateValid() {
        if (earlyRepayments == null || earlyRepayments.isEmpty()) {
            return true;
        }
        LocalDate today = LocalDate.now();
        LocalDate minDate = (startDate != null && startDate.isAfter(today)) ? startDate : today;
        for (var er : earlyRepayments) {
            if (er.repaymentDate() != null && er.repaymentDate().isBefore(minDate)) {
                return false;
            }
        }
        return true;
    }

    public record MortgageEarlyRepaymentRequest(
            Long id,
            @NotNull LocalDate repaymentDate,
            @NotNull @Positive Long amount,
            @NotNull EarlyRepaymentType repaymentType,
            @NotNull @Min(0) Long fee,
            Integer reducedMonths) {
    }

    public record BonusPaymentRequest(
            @NotNull @Min(1) @Max(12) Integer month,
            @NotNull @Min(0) Long amount) {
    }
}
