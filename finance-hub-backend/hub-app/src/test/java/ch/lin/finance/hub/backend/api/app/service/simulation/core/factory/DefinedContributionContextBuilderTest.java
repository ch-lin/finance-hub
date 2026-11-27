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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.factory;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

@ExtendWith(MockitoExtension.class)
class DefinedContributionContextBuilderTest {

    @Mock
    private DefinedContributionAccountSnapshotRepository dcRepository;

    @Mock
    private DefinedContributionUserSettingsRepository dcUserSettingsRepository;

    private DefinedContributionContextBuilder builder;
    private FinanceHubConfig config;

    @BeforeEach
    void setUp() {
        builder = new DefinedContributionContextBuilder(dcRepository, dcUserSettingsRepository);
        config = new FinanceHubConfig("default");
        config.setIdecoExpectedReturnRate(0.04);
    }

    @Test
    @DisplayName("Corporate DC without snapshot and with override should set contribution years directly and keep enrollment date null")
    void corporateDc_NoSnapshot_WithOverride_ShouldSetContributionYearsAndKeepEnrollmentDateNull() {
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.empty());

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setMonthlyDcMatchingContribution(20_000L);
        settings.setIsDcContributionYearsOverridden(true);
        settings.setDcContributionYearsOverride(5);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getDcContributionYears()).isEqualTo(5);
        assertThat(context.getDcEnrollmentDate()).isNull();
        assertThat(context.getDcFunds()).hasSize(1);
        assertThat(context.getDcFunds().get(0).getFundName()).isEqualTo("Corporate DC (Profile Setting)");
    }

    @Test
    @DisplayName("iDeCo without snapshot and with override should set contribution years directly and keep enrollment date null")
    void ideco_NoSnapshot_WithOverride_ShouldSetContributionYearsAndKeepEnrollmentDateNull() {
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.empty());
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.IDECO))
                .thenReturn(Optional.empty());

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setMonthlyIdecoContribution(23_000L);
        settings.setIsIdecoContributionYearsOverridden(true);
        settings.setIdecoContributionYearsOverride(4);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getIdecoContributionYears()).isEqualTo(4);
        assertThat(context.getIdecoEnrollmentDate()).isNull();
        assertThat(context.getIdecoFunds()).hasSize(1);
        assertThat(context.getIdecoFunds().get(0).getFundName()).isEqualTo("iDeCo (Profile Setting)");
    }

    @Test
    @DisplayName("Corporate DC with snapshot and with override should prioritize override and keep enrollment date null")
    void corporateDc_WithSnapshot_WithOverride_ShouldPrioritizeOverrideAndKeepEnrollmentDateNull() {
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                LocalDate.now(), DefinedContributionType.CORPORATE_DC, 1_000_000L, 800_000L, 20_000L, 0L, 20_000L);
        snapshot.setEnrollmentDate(LocalDate.now().minusYears(10));

        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.of(snapshot));

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setIsDcContributionYearsOverridden(true);
        settings.setDcContributionYearsOverride(7);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getDcContributionYears()).isEqualTo(7);
        assertThat(context.getDcEnrollmentDate()).isNull();
    }

    @Test
    @DisplayName("Corporate DC with snapshot and without override should set enrollment date and calculate years")
    void corporateDc_WithSnapshot_WithoutOverride_ShouldSetEnrollmentDateAndCalculateYears() {
        LocalDate enrollmentDate = LocalDate.now().minusYears(3);
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                LocalDate.now(), DefinedContributionType.CORPORATE_DC, 1_000_000L, 800_000L, 20_000L, 0L, 20_000L);
        snapshot.setEnrollmentDate(enrollmentDate);

        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.of(snapshot));

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setIsDcContributionYearsOverridden(false);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getDcEnrollmentDate()).isEqualTo(enrollmentDate);
        assertThat(context.getDcContributionYears()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("iDeCo with snapshot and with override should prioritize override and keep enrollment date null")
    void ideco_WithSnapshot_WithOverride_ShouldPrioritizeOverrideAndKeepEnrollmentDateNull() {
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.empty());

        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                LocalDate.now(), DefinedContributionType.IDECO, 500_000L, 400_000L, 0L, 23_000L, 23_000L);
        snapshot.setEnrollmentDate(LocalDate.now().minusYears(8));

        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.IDECO))
                .thenReturn(Optional.of(snapshot));

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setIsIdecoContributionYearsOverridden(true);
        settings.setIdecoContributionYearsOverride(6);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getIdecoContributionYears()).isEqualTo(6);
        assertThat(context.getIdecoEnrollmentDate()).isNull();
    }

    @Test
    @DisplayName("iDeCo with snapshot and without override should set enrollment date and calculate years")
    void ideco_WithSnapshot_WithoutOverride_ShouldSetEnrollmentDateAndCalculateYears() {
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.empty());

        LocalDate enrollmentDate = LocalDate.now().minusYears(2);
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                LocalDate.now(), DefinedContributionType.IDECO, 500_000L, 400_000L, 0L, 23_000L, 23_000L);
        snapshot.setEnrollmentDate(enrollmentDate);

        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user-1",
                DefinedContributionType.IDECO))
                .thenReturn(Optional.of(snapshot));

        DefinedContributionUserSettings settings = new DefinedContributionUserSettings();
        settings.setIsIdecoContributionYearsOverridden(false);

        when(dcUserSettingsRepository.findByOwnerUserId("user-1")).thenReturn(Optional.of(settings));

        SimulationContext context = new SimulationContext();
        builder.initializeDefinedContributionContext(context, config, "user-1");

        assertThat(context.getIdecoEnrollmentDate()).isEqualTo(enrollmentDate);
        assertThat(context.getIdecoContributionYears()).isGreaterThanOrEqualTo(2);
    }
}
