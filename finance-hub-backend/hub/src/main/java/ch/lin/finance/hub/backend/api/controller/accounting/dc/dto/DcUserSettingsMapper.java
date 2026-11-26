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
package ch.lin.finance.hub.backend.api.controller.accounting.dc.dto;

import org.mapstruct.Mapper;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionUserSettingsController.TargetFundAllocationRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionUserSettingsController.UpdateTargetAllocationsRequest;

@Mapper(componentModel = "spring")
public interface DcUserSettingsMapper {
    DcUserSettingsResponse toResponse(DcUserSettingsResult result);

    SaveDcUserSettingsCommand toCommand(DcUserSettingsRequest request);

    UpdateAccountAllocationsCommand toUpdateAccountAllocationsCommand(UpdateTargetAllocationsRequest request);

    FundAllocationCommand toFundAllocationCommand(TargetFundAllocationRequest request);
}
