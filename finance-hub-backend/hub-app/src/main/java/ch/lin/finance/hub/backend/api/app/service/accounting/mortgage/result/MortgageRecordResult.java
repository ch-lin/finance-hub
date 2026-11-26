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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MortgageRecordResult extends BaseResult {

    private Long id;
    private String bankName;
    private String contractNumber;
    private String loanName;
    private Long totalLoanAmount;
    private Long currentBalance;
    private LocalDate balanceRecordDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer loanTermMonths;
    private Double interestRate;
    private InterestRateType rateType;
    private RepaymentMethod repaymentMethod;
    private Long monthlyPayment;
    private List<BonusPaymentResult> bonusPayments;
    private LocalDate occupancyDate;
    private ResidenceType residenceType;
    private PropertyType propertyType;
    private Boolean taxDeductionEligible;
    private Integer taxDeductionEndYear;
    private Double taxDeductionRate;
    private Long maxTaxDeductionAmount;
    private Boolean hasFiveYearRule;
    private List<MortgageEarlyRepaymentResult> earlyRepayments;

    public static MortgageRecordResult fromEntity(MortgageRecord entity) {
        if (entity == null) {
            return null;
        }
        MortgageRecordResult dto = new MortgageRecordResult();
        dto.setId(entity.getId());
        dto.setBankName(entity.getBankName());
        dto.setContractNumber(entity.getContractNumber());
        dto.setLoanName(entity.getLoanName());
        dto.setTotalLoanAmount(entity.getTotalLoanAmount());
        dto.setCurrentBalance(entity.getCurrentBalance());
        dto.setBalanceRecordDate(entity.getBalanceRecordDate());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setLoanTermMonths(entity.getLoanTermMonths());
        dto.setInterestRate(entity.getInterestRate());
        dto.setRateType(entity.getRateType());
        dto.setRepaymentMethod(entity.getRepaymentMethod());
        dto.setMonthlyPayment(entity.getMonthlyPayment());
        dto.setBonusPayments(entity.getBonusPayments() != null
                ? entity.getBonusPayments().stream().map(BonusPaymentResult::fromEntity).toList()
                : Collections.emptyList());
        dto.setOccupancyDate(entity.getOccupancyDate());
        dto.setResidenceType(entity.getResidenceType());
        dto.setPropertyType(entity.getPropertyType());
        dto.setTaxDeductionEligible(entity.getTaxDeductionEligible());
        dto.setTaxDeductionEndYear(entity.getTaxDeductionEndYear());
        dto.setTaxDeductionRate(entity.getTaxDeductionRate());
        dto.setMaxTaxDeductionAmount(entity.getMaxTaxDeductionAmount());
        dto.setHasFiveYearRule(entity.getHasFiveYearRule());
        dto.setEarlyRepayments(entity.getEarlyRepayments() != null
                ? entity.getEarlyRepayments().stream().map(MortgageEarlyRepaymentResult::fromEntity).toList()
                : Collections.emptyList());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setPrivacyLevel(entity.getPrivacyLevel());
        return dto;
    }
}
