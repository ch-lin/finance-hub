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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;

import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.SaveNisaUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaUserSettingsResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaUserSettings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NisaUserSettingsServiceImpl implements NisaUserSettingsService {

    private final NisaUserSettingsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<NisaUserSettingsResult> getSettings() {
        return repository.findFirstBy().map(settings -> {
            return NisaUserSettingsResult.fromEntity(settings, null);
        });
    }

    @Override
    @Transactional
    public NisaUserSettingsResult saveSettings(@NonNull SaveNisaUserSettingsCommand command) {
        NisaUserSettings settings = repository.findFirstBy().orElseGet(() -> new NisaUserSettings());
        settings.setMonthlyGrowthContribution(command.monthlyGrowthContribution());
        settings.setMonthlyTsumitateContribution(command.monthlyTsumitateContribution());
        if (command.nisaExpectedReturnRate() != null) {
            settings.setNisaExpectedReturnRate(command.nisaExpectedReturnRate());
        }
        settings = repository.save(Objects.requireNonNull(settings));
        return NisaUserSettingsResult.fromEntity(settings, null);
    }
}
