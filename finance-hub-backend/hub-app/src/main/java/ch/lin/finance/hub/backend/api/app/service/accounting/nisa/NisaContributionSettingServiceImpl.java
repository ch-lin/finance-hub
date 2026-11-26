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

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NisaContributionSettingServiceImpl implements NisaContributionSettingService {

    private final NisaContributionSettingRepository settingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NisaContributionSettingResult> getAllSettings() {
        return settingRepository.findAllByOrderByRecordDateDesc().stream()
                .map(NisaContributionSettingResult::fromEntity)
                .collect(Collectors.toList());
    }
}
