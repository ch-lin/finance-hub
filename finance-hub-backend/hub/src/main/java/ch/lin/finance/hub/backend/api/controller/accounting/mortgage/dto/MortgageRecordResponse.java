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

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

public record MortgageRecordResponse(
        Long id,
        String bankName,
        String contractNumber,
        String loanName,
        Long totalLoanAmount,
        Long currentBalance,
        LocalDate balanceRecordDate,
        LocalDate startDate,
        LocalDate endDate,
        Integer loanTermMonths,
        Double interestRate,
        InterestRateType rateType,
        RepaymentMethod repaymentMethod,
        Long monthlyPayment,
        List<BonusPaymentResponse> bonusPayments,
        LocalDate occupancyDate,
        ResidenceType residenceType,
        PropertyType propertyType,
        Boolean taxDeductionEligible,
        Integer taxDeductionEndYear,
        Double taxDeductionRate,
        Long maxTaxDeductionAmount,
        Boolean hasFiveYearRule,
        List<MortgageEarlyRepaymentResponse> earlyRepayments,
        Boolean isOwnedByCurrentUser) {
}
