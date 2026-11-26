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

import java.util.Collections;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.command.SaveMortgageRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result.MortgageEarlyRepaymentResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result.MortgageRecordResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MortgageRecordMapper {
    SaveMortgageRecordCommand toCommand(MortgageRecordRequest request);

    @org.mapstruct.Mapping(target = "isOwnedByCurrentUser", expression = "java(java.util.Objects.equals(result.getOwnerUserId(), currentUserId))")
    MortgageRecordResponse toResponse(MortgageRecordResult result, @Context String currentUserId);

    MortgageEarlyRepaymentResponse toResponse(MortgageEarlyRepaymentResult result, @Context String currentUserId);

    default MortgageRecordResponse toMaskedResponse(MortgageRecordResult result, @Context String currentUserId) {
        if (result == null) {
            return null;
        }

        MortgageRecordResponse response = toResponse(result, currentUserId);

        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            return new MortgageRecordResponse(
                    response.id(),
                    response.bankName(),
                    response.contractNumber(),
                    PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.MORTGAGE),
                    null, // totalLoanAmount
                    null, // currentBalance
                    response.balanceRecordDate(),
                    response.startDate(),
                    response.endDate(),
                    response.loanTermMonths(),
                    null, // interestRate
                    response.rateType(),
                    response.repaymentMethod(),
                    null, // monthlyPayment
                    Collections.<BonusPaymentResponse>emptyList(), // bonusPayments
                    response.occupancyDate(),
                    response.residenceType(),
                    response.propertyType(),
                    response.taxDeductionEligible(),
                    response.taxDeductionEndYear(),
                    null, // taxDeductionRate
                    null, // maxTaxDeductionAmount
                    response.hasFiveYearRule(),
                    Collections.<MortgageEarlyRepaymentResponse>emptyList(), // earlyRepayments
                    false // isOwnedByCurrentUser
            );
        }

        return response;
    }
}
