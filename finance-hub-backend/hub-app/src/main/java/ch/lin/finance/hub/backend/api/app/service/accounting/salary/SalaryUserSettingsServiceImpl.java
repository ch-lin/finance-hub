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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryUserSettingsResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryUserSettings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalaryUserSettingsServiceImpl implements SalaryUserSettingsService {

    private final SalaryUserSettingsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SalaryUserSettingsResult> getSettings() {
        return repository.findFirstBy().map(settings -> {
            // Because ownerUserId is implicit via hibernate filter, we can just pass null
            // or retrieve from context if needed.
            // For now, setting null as ownerUserId is fine, or we can just leave it blank
            // as it's not strictly needed for the response payload usually.
            return SalaryUserSettingsResult.fromEntity(settings, null);
        });
    }

    @Override
    @Transactional
    public SalaryUserSettingsResult saveSettings(@NonNull SaveSalaryUserSettingsCommand command) {
        SalaryUserSettings settings = repository.findFirstBy().orElseGet(() -> new SalaryUserSettings());

        if (command.salaryGrowthRate() != null) {
            settings.setSalaryGrowthRate(command.salaryGrowthRate());
        }

        if (command.useHistoricalBonusForProjection() != null) {
            settings.setUseHistoricalBonusForProjection(command.useHistoricalBonusForProjection());
        }

        if (command.expectedAnnualBonus() != null) {
            settings.setExpectedAnnualBonus(command.expectedAnnualBonus());
        }

        settings = repository.save(Objects.requireNonNull(settings));

        return SalaryUserSettingsResult.fromEntity(settings, null);
    }
}
