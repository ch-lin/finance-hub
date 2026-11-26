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
package ch.lin.finance.hub.backend.api.controller.accounting.nisa;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaContributionSettingService;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingResponse;

/**
 * REST controller for managing NISA contribution settings (fund allocations).
 */
@RestController
@RequestMapping("/assets/nisa/settings")
public class NisaContributionSettingController {

    private final NisaContributionSettingService settingService;
    private final NisaContributionSettingMapper settingMapper;

    public NisaContributionSettingController(NisaContributionSettingService settingService,
            NisaContributionSettingMapper settingMapper) {
        this.settingService = settingService;
        this.settingMapper = settingMapper;
    }

    /**
     * Retrieves all NISA contribution settings.
     *
     * @return A list of all NISA contribution settings.
     */
    @GetMapping
    public List<NisaContributionSettingResponse> getAllSettings() {
        return settingService.getAllSettings().stream()
                .map(settingMapper::toResponse)
                .collect(Collectors.toList());
    }
}
