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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

@ExtendWith(MockitoExtension.class)
class NisaContextBuilderTest {

    @Mock
    private NisaSnapshotRecordRepository nisaRepository;
    @Mock
    private NisaContributionSettingRepository nisaSettingRepository;
    @Mock
    private NisaUserSettingsRepository nisaUserSettingsRepository;

    private NisaContextBuilder nisaContextBuilder;
    private FinanceHubConfig config;

    @BeforeEach
    void setUp() {
        nisaContextBuilder = new NisaContextBuilder(nisaRepository, nisaSettingRepository, nisaUserSettingsRepository);
        config = new FinanceHubConfig("default");
        config.setNisaExpectedReturnRate(0.05);
    }

    @Test
    @DisplayName("NisaContextBuilder should calculate 52 weeks annual contribution for weekly course and populate course & settingAmount")
    void shouldCalculate52WeeksAnnualContributionForWeeklyCourse() {
        LocalDate today = LocalDate.now();
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim All Country",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);

        NisaContributionSetting setting = new NisaContributionSetting(
                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim All Country",
                "毎週", 10_000L);

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());

        SimulationContext context = new SimulationContext();
        nisaContextBuilder.initializeNisaContext(context, config);

        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund fund = context.getNisaFunds().get(0);
        assertThat(fund.getFundName()).isEqualTo("eMAXIS Slim All Country");
        assertThat(fund.getCourse()).isEqualTo("毎週");
        assertThat(fund.getSettingAmount()).isEqualTo(10_000L);
        assertThat(fund.getAnnualContribution()).isEqualTo(520_000L); // 10,000 * 52
    }

    @Test
    @DisplayName("NisaContextBuilder should calculate 240 days annual contribution for daily course")
    void shouldCalculate240DaysAnnualContributionForDailyCourse() {
        LocalDate today = LocalDate.now();
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                today, "NISA (成長)預り", "03311187", "TOPIX Fund",
                50.0, 20000.0, 22000.0, 500_000L, 550_000L, 50_000L);

        NisaContributionSetting setting = new NisaContributionSetting(
                today, "NISA (成長)預り", "03311187", "TOPIX Fund",
                "毎日", 1_000L);

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());

        SimulationContext context = new SimulationContext();
        nisaContextBuilder.initializeNisaContext(context, config);

        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund fund = context.getNisaFunds().get(0);
        assertThat(fund.getCourse()).isEqualTo("毎日");
        assertThat(fund.getSettingAmount()).isEqualTo(1_000L);
        assertThat(fund.getAnnualContribution()).isEqualTo(240_000L); // 1,000 * 240
    }

    @Test
    @DisplayName("NisaContextBuilder should calculate 12 months annual contribution for monthly course")
    void shouldCalculate12MonthsAnnualContributionForMonthlyCourse() {
        LocalDate today = LocalDate.now();
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                today, "NISA (成長)預り", "03311187", "S&P 500 Fund",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);

        NisaContributionSetting setting = new NisaContributionSetting(
                today, "NISA (成長)預り", "03311187", "S&P 500 Fund",
                "毎月", 50_000L);

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());

        SimulationContext context = new SimulationContext();
        nisaContextBuilder.initializeNisaContext(context, config);

        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund fund = context.getNisaFunds().get(0);
        assertThat(fund.getCourse()).isEqualTo("毎月");
        assertThat(fund.getSettingAmount()).isEqualTo(50_000L);
        assertThat(fund.getAnnualContribution()).isEqualTo(600_000L); // 50,000 * 12
    }

    @Test
    @DisplayName("NisaContextBuilder should set annualContribution to 0 without fallback when settingAmount is null or 0")
    void shouldNotFallbackToMonthlyEquivalentAmountWhenCourseOrSettingAmountInvalid() {
        LocalDate today = LocalDate.now();
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                today, "NISA (成長)預り", "03311187", "Unknown Fund",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);

        NisaContributionSetting setting = new NisaContributionSetting(
                today, "NISA (成長)預り", "03311187", "Unknown Fund",
                "不明", 0L);

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());

        SimulationContext context = new SimulationContext();
        nisaContextBuilder.initializeNisaContext(context, config);

        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund fund = context.getNisaFunds().get(0);
        assertThat(fund.getAnnualContribution()).isEqualTo(0L);
    }

    @Test
    @DisplayName("NisaContextBuilder should match setting by fundCode even when fundName differs slightly")
    void shouldMatchSettingByFundCodeWhenFundNameDiffers() {
        LocalDate today = LocalDate.now();
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim 全世界株式（オール・カントリー）　",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);

        NisaContributionSetting setting = new NisaContributionSetting(
                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim 全世界株式（オール・カントリー）",
                "毎月", 50_000L);
        setting.updateExpectedReturnRate(0.065);

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());

        SimulationContext context = new SimulationContext();
        nisaContextBuilder.initializeNisaContext(context, config);

        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund fund = context.getNisaFunds().get(0);
        assertThat(fund.getAnnualContribution()).isEqualTo(600_000L);
        assertThat(fund.getExpectedReturnRate()).isEqualTo(0.065);
    }
}
