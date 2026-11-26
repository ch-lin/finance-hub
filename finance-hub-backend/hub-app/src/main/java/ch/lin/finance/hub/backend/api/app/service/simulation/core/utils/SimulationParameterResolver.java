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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.utils;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

/**
 * A stateless utility class to resolve simulation parameters.
 * Handles the fallback priority logic: Override > UserProfile > System Config.
 */
public final class SimulationParameterResolver {

    private SimulationParameterResolver() {
        // Prevent instantiation
    }

    /**
     * Resolves the target retirement age.
     * Priority: 1. override, 2. profileValue, 3. configValue
     */
    public static int resolveRetirementAge(Integer profileValue, Integer configValue, Integer override) {
        if (override != null) {
            return override;
        }
        if (profileValue != null) {
            return profileValue;
        }
        if (configValue == null) {
            throw new IllegalArgumentException("System config target retirement age cannot be null.");
        }
        return configValue;
    }

    /**
     * Resolves the target retirement age using UserProfile and FinanceHubConfig.
     */
    public static int resolveRetirementAge(UserProfile profile, FinanceHubConfig config, Integer override) {
        Integer profileAge = (profile != null) ? profile.getTargetRetirementAge() : null;
        Integer configAge = (config != null) ? config.getTargetRetirementAge() : null;
        return resolveRetirementAge(profileAge, configAge, override);
    }

    /**
     * Resolves the pension start age.
     * Priority: 1. override, 2. profileValue, 3. configValue
     */
    public static int resolvePensionStartAge(Integer profileValue, Integer configValue, Integer override) {
        if (override != null) {
            return override;
        }
        if (profileValue != null) {
            return profileValue;
        }
        if (configValue == null) {
            throw new IllegalArgumentException("System config pension start age cannot be null.");
        }
        return configValue;
    }

    /**
     * Resolves the pension start age using UserProfile and FinanceHubConfig.
     */
    public static int resolvePensionStartAge(UserProfile profile, FinanceHubConfig config, Integer override) {
        Integer profileAge = (profile != null) ? profile.getPensionStartAge() : null;
        Integer configAge = (config != null) ? config.getPensionStartAge() : null;
        return resolvePensionStartAge(profileAge, configAge, override);
    }

    /**
     * Resolves the life expectancy.
     * Priority: 1. override, 2. profileValue, 3. configValue
     */
    public static int resolveLifeExpectancy(Integer profileValue, Integer configValue, Integer override) {
        if (override != null) {
            return override;
        }
        if (profileValue != null) {
            return profileValue;
        }
        if (configValue == null) {
            throw new IllegalArgumentException("System config life expectancy cannot be null.");
        }
        return configValue;
    }

    /**
     * Resolves the life expectancy using UserProfile and FinanceHubConfig.
     */
    public static int resolveLifeExpectancy(UserProfile profile, FinanceHubConfig config, Integer override) {
        Integer profileAge = (profile != null) ? profile.getLifeExpectancy() : null;
        Integer configAge = (config != null) ? config.getLifeExpectancy() : null;
        return resolveLifeExpectancy(profileAge, configAge, override);
    }

    /**
     * Resolves the iDeCo unlock age.
     * Priority: 1. FinanceHubConfig, 2. Default 60
     */
    public static int resolveIdecoUnlockAge(FinanceHubConfig config) {
        if (config != null && config.getIdecoUnlockAge() != null) {
            return Math.max(60, Math.min(75, config.getIdecoUnlockAge()));
        }
        return 60;
    }

    /**
     * Resolves the DC unlock age.
     * Priority: 1. FinanceHubConfig, 2. Default 60
     */
    public static int resolveDcUnlockAge(FinanceHubConfig config) {
        if (config != null && config.getDcUnlockAge() != null) {
            return Math.max(60, Math.min(75, config.getDcUnlockAge()));
        }
        return 60;
    }

    /**
     * Resolves the Tax-Advantaged (iDeCo/DC) target withdrawal age.
     * It ensures the withdrawal age is not earlier than the statutory unlock ages.
     * Priority: 1. override, 2. profileValue, 3. targetRetirementAge (clamped to
     * [minUnlockAge, 75])
     */
    public static int resolveTaxAdvantagedWithdrawalAge(UserProfile profile, int dcUnlockAge, int idecoUnlockAge,
            Integer override, int targetRetirementAge) {
        int minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge);
        if (override != null) {
            return Math.max(minUnlockAge, Math.min(75, override));
        }
        if (profile != null && profile.getTaxAdvantagedWithdrawalAge() != null) {
            return Math.max(minUnlockAge, Math.min(75, profile.getTaxAdvantagedWithdrawalAge()));
        }
        return Math.max(minUnlockAge, Math.min(75, targetRetirementAge));
    }
}
