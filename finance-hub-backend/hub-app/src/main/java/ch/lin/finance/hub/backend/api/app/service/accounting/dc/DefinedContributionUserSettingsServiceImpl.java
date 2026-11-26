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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefinedContributionUserSettingsServiceImpl implements DefinedContributionUserSettingsService {

    private final DefinedContributionUserSettingsRepository repository;
    private final DefinedContributionAccountSnapshotRepository dcAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<DcUserSettingsResult> getSettings() {
        return repository.findFirstBy().map(settings -> {
            return DcUserSettingsResult.fromEntity(settings, null);
        });
    }

    @Override
    @Transactional
    public DcUserSettingsResult saveSettings(@NonNull SaveDcUserSettingsCommand command) {
        DefinedContributionUserSettings settings = repository.findFirstBy()
                .orElseGet(() -> new DefinedContributionUserSettings());

        if (command.monthlyIdecoContribution() != null) {
            settings.setMonthlyIdecoContribution(command.monthlyIdecoContribution());
        }
        if (command.idecoExpectedReturnRate() != null) {
            settings.setIdecoExpectedReturnRate(command.idecoExpectedReturnRate());
        }
        if (command.monthlyDcMatchingContribution() != null) {
            settings.setMonthlyDcMatchingContribution(command.monthlyDcMatchingContribution());
        }

        if (command.isDcContributionYearsOverridden() != null) {
            settings.setIsDcContributionYearsOverridden(command.isDcContributionYearsOverridden());
        }
        if (command.dcContributionYearsOverride() != null) {
            settings.setDcContributionYearsOverride(command.dcContributionYearsOverride());
        }

        if (command.isIdecoContributionYearsOverridden() != null) {
            settings.setIsIdecoContributionYearsOverridden(command.isIdecoContributionYearsOverridden());
        }
        if (command.idecoContributionYearsOverride() != null) {
            settings.setIdecoContributionYearsOverride(command.idecoContributionYearsOverride());
        }

        if (Boolean.TRUE.equals(settings.getIsDcContributionYearsOverridden())
                && settings.getDcContributionYearsOverride() == null) {
            throw new IllegalArgumentException(
                    "DC contribution years override value cannot be null when the override is enabled");
        }
        if (Boolean.TRUE.equals(settings.getIsIdecoContributionYearsOverridden())
                && settings.getIdecoContributionYearsOverride() == null) {
            throw new IllegalArgumentException(
                    "iDeCo contribution years override value cannot be null when the override is enabled");
        }

        settings = repository.save(Objects.requireNonNull(settings));

        return DcUserSettingsResult.fromEntity(settings, null);
    }

    @Override
    @Transactional
    public DcUserSettingsResult updateTargetAllocations(@NonNull DefinedContributionType type,
            UpdateAccountAllocationsCommand command) {
        if (command == null || command.getRemainderFundCode() == null || command.getRemainderFundCode().isBlank()
                || command.getAllocations() == null || command.getAllocations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remainderFundCode and non-empty allocations are required");
        }

        DefinedContributionAccountSnapshot latestSnapshot = dcAccountRepository
                .findFirstByTypeOrderByRecordDateDesc(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No snapshot found for account type: " + type));

        List<DefinedContributionFundSnapshot> snapshotFunds = latestSnapshot.getFunds();
        if (snapshotFunds == null || snapshotFunds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Latest snapshot has no funds for account type: " + type);
        }

        Set<String> requiredFundCodes = new HashSet<>();
        for (DefinedContributionFundSnapshot fund : snapshotFunds) {
            requiredFundCodes.add(fund.getFundCode());
        }

        String remainderFundCode = command.getRemainderFundCode();
        Map<String, Double> validatedPercentages = DefinedContributionAllocationCalculator
                .validateAndExtractPercentages(command.getAllocations(), requiredFundCodes, remainderFundCode);

        DefinedContributionUserSettings settings = repository.findFirstBy()
                .orElseGet(DefinedContributionUserSettings::new);
        settings.updateTargetAllocation(type, new DcTargetAllocationSetting(remainderFundCode, validatedPercentages));

        settings = repository.save(Objects.requireNonNull(settings));
        return DcUserSettingsResult.fromEntity(settings, null);
    }

    @Override
    @Transactional
    public Optional<DcUserSettingsResult> clearTargetAllocations(@NonNull DefinedContributionType type) {
        return repository.findFirstBy().map(settings -> {
            settings.updateTargetAllocation(type, null);
            DefinedContributionUserSettings saved = repository.save(Objects.requireNonNull(settings));
            return DcUserSettingsResult.fromEntity(saved, null);
        });
    }
}
