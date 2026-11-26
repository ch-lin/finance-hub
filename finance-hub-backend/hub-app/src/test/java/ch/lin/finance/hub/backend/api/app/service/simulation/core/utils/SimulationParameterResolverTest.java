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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

class SimulationParameterResolverTest {

    @Test
    void resolveTaxAdvantagedWithdrawalAge_whenNullAndTargetRetirement65_returns65() {
        UserProfile profile = new UserProfile("test", null, null, null);
        profile.setTaxAdvantagedWithdrawalAge(null);

        int resolved = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                profile, 60, 60, null, 65);

        assertThat(resolved).isEqualTo(65);
    }

    @Test
    void resolveTaxAdvantagedWithdrawalAge_whenNullAndFireAt45_clampsToMinUnlockAge60() {
        UserProfile profile = new UserProfile("test", null, null, null);
        profile.setTaxAdvantagedWithdrawalAge(null);

        int resolved = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                profile, 60, 60, null, 45);

        assertThat(resolved).isEqualTo(60);
    }

    @Test
    void resolveTaxAdvantagedWithdrawalAge_whenUserDeclared68_ignoresTargetRetirementAge() {
        UserProfile profile = new UserProfile("test", null, null, null);
        profile.setTaxAdvantagedWithdrawalAge(68);

        int resolved = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                profile, 60, 60, null, 65);

        assertThat(resolved).isEqualTo(68);
    }

    @Test
    void resolveTaxAdvantagedWithdrawalAge_whenNullAndTargetRetirement80_clampsToMax75() {
        UserProfile profile = new UserProfile("test", null, null, null);
        profile.setTaxAdvantagedWithdrawalAge(null);

        int resolved = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                profile, 60, 60, null, 80);

        assertThat(resolved).isEqualTo(75);
    }

    @Test
    void resolveTaxAdvantagedWithdrawalAge_whenOverrideProvided_prioritizesOverrideClampedToRange() {
        UserProfile profile = new UserProfile("test", null, null, null);
        profile.setTaxAdvantagedWithdrawalAge(68);

        int resolved = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                profile, 60, 60, 70, 65);

        assertThat(resolved).isEqualTo(70);
    }
}
