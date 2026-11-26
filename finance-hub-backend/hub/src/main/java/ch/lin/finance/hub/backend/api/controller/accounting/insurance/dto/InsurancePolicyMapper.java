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
package ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.command.SaveInsurancePolicyCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.result.InsurancePolicyResult;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InsurancePolicyMapper {

    InsurancePolicyResponse toResponse(InsurancePolicyResult result);

    default InsurancePolicyResponse toMaskedResponse(InsurancePolicyResult result, String currentUserId) {
        if (result == null) {
            return null;
        }
        // In this simple case, we don't necessarily have complex masking logic for
        // insurance policies yet,
        // but we keep the method for consistency with MortgageRecordMapper pattern.
        return toResponse(result);
    }

    SaveInsurancePolicyCommand toCommand(InsurancePolicyRequest request);
}
