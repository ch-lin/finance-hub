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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.profile.user.UserProfileService;
import ch.lin.finance.hub.backend.api.app.service.profile.user.result.UserProfileResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SalaryContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

@ExtendWith(MockitoExtension.class)
class PensionProjectionServiceTest {

    @Mock
    private PensionRecordRepository pensionRecordRepository;

    @Mock
    private SalaryRecordRepository salaryRecordRepository;

    @Mock
    private FinanceHubConfigService configService;

    @Mock
    private PensionRuleConfigService pensionRuleConfigService;

    @Mock
    private ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator pensionCalculator;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private SalaryContextBuilder salaryContextBuilder;

    @InjectMocks
    private PensionProjectionServiceImpl projectionService;

    @Test
    @SuppressWarnings("null")
    void calculateProjection_withExistingRecord_shouldCalculateCorrectly() {
        // Arrange
        AllPensionRuleConfigsData allPensionConfigs = new AllPensionRuleConfigsData("default", List.of());
        when(pensionRuleConfigService.getAllConfigs()).thenReturn(allPensionConfigs);
        PensionRuleConfigResult pensionRuleConfig = PensionRuleConfigResult.builder().maxBasicPensionAmount(816_000L)
                .build();
        when(pensionRuleConfigService.getConfig("default")).thenReturn(pensionRuleConfig);

        AllConfigsData allConfigs = new AllConfigsData("default", List.of());
        when(configService.getAllConfigs()).thenReturn(allConfigs);
        when(salaryRecordRepository.findFirstByTypeOrderByPaymentDateDesc(any())).thenReturn(Optional.empty());

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L); // 50万円
        when(configService.getConfig("default")).thenReturn(config);

        PensionRecord record = new PensionRecord(
                LocalDate.now(),
                500_000L, // basicPensionAmount
                600_000L, // kouseiPensionAmount
                1_100_000L, // totalPensionAmount
                10, // kokuminCategory1Months
                0, // kokuminCategory3Months
                10, // kokuminTotalMonths
                110, // kouseiGeneralMonths
                110, // kouseiTotalMonths
                120, // totalEnrolledMonths
                120, // qualifyingMonths
                100_000L, // kokuminPremiumPaid
                2_000_000L, // kouseiPremiumPaid
                2_100_000L // totalPremiumPaid
        );
        when(pensionRecordRepository.findAll(any(Sort.class))).thenReturn(List.of(record));

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(10),
                ArgumentMatchers.eq(110), ArgumentMatchers.eq(0), ArgumentMatchers.eq(65))).thenReturn(204_000L);

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(10),
                ArgumentMatchers.eq(110), ArgumentMatchers.eq(12), ArgumentMatchers.eq(65))).thenReturn(224_400L);

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(10),
                ArgumentMatchers.eq(110), ArgumentMatchers.eq(24), ArgumentMatchers.eq(65))).thenReturn(244_800L);

        when(salaryContextBuilder.resolveAnnualBonusInfo(any())).thenReturn(new SalaryContextBuilder.BonusInfo(0L, 0));
        when(pensionCalculator.calculateYearlyKouseiBenefitIncrement(any(PensionConfigDto.class),
                ArgumentMatchers.eq(6_000_000L), ArgumentMatchers.eq(0L), ArgumentMatchers.eq(0))).thenReturn(32_886L);
        when(pensionCalculator.calculatePensionMultiplier(65)).thenReturn(1.0);

        when(userProfileService.getUserProfile("user-1")).thenReturn(Optional.of(
                new UserProfileResult("user-1", null, LocalDate.of(1980, 1, 1), null, null, 60, 65, null, null, 85,
                        null, false)));

        // Act
        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null);

        // Assert
        assertThat(result).hasSize(3);

        // Year 0
        PensionProjectionYearlyResult year0 = result.get(0);
        assertThat(year0.getYearsAdditionalWorked()).isEqualTo(0);
        // Basic: 120 months => 816000 * 120 / 480 = 204000
        assertThat(year0.getProjectedBasicPensionAnnual()).isEqualTo(204_000L);
        // Kousei: 600,000 + 0 = 600,000
        assertThat(year0.getProjectedKouseiPensionAnnual()).isEqualTo(600_000L);
        assertThat(year0.getProjectedTotalPensionAnnual()).isEqualTo(804_000L);
        // Lifetime (85 - 65 = 20 years) => 804000 * 20 = 16,080,000
        assertThat(year0.getProjectedLifetimeTotal()).isEqualTo(16_080_000L);

        // Year 1
        PensionProjectionYearlyResult year1 = result.get(1);
        assertThat(year1.getYearsAdditionalWorked()).isEqualTo(1);
        // Basic: 132 months => 816000 * 132 / 480 = 224400
        assertThat(year1.getProjectedBasicPensionAnnual()).isEqualTo(224_400L);
        // Kousei: 600,000 + (500000 * 5.481/1000 * 12) = 600,000 + 32886 = 632886
        assertThat(year1.getProjectedKouseiPensionAnnual()).isEqualTo(632_886L);
        assertThat(year1.getProjectedTotalPensionAnnual()).isEqualTo(857_286L);

        // Year 2
        PensionProjectionYearlyResult year2 = result.get(2);
        assertThat(year2.getYearsAdditionalWorked()).isEqualTo(2);
        // Basic: 144 months => 816000 * 144 / 480 = 244800
        assertThat(year2.getProjectedBasicPensionAnnual()).isEqualTo(244_800L);
        // Kousei: 600,000 + (500000 * 5.481/1000 * 24) = 600,000 + 65772 = 665772
        assertThat(year2.getProjectedKouseiPensionAnnual()).isEqualTo(665_772L);
        assertThat(year2.getProjectedTotalPensionAnnual()).isEqualTo(910_572L);
    }

    @Test
    @SuppressWarnings("null")
    void calculateProjection_withCap_shouldCapBasicPension() {
        // Arrange
        AllPensionRuleConfigsData allPensionConfigs = new AllPensionRuleConfigsData("default", List.of());
        when(pensionRuleConfigService.getAllConfigs()).thenReturn(allPensionConfigs);
        PensionRuleConfigResult pensionRuleConfig = PensionRuleConfigResult.builder().maxBasicPensionAmount(816_000L)
                .build();
        when(pensionRuleConfigService.getConfig("default")).thenReturn(pensionRuleConfig);

        AllConfigsData allConfigs = new AllConfigsData("default", List.of());
        when(configService.getAllConfigs()).thenReturn(allConfigs);
        when(salaryRecordRepository.findFirstByTypeOrderByPaymentDateDesc(any())).thenReturn(Optional.empty());

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L);
        when(configService.getConfig("default")).thenReturn(config);

        PensionRecord record = new PensionRecord(
                LocalDate.now(),
                816_000L,
                600_000L,
                1_416_000L,
                0, 0, 0,
                470, 470, // 470 months already!
                470, 470,
                0L, 0L, 0L);
        when(pensionRecordRepository.findAll(any(Sort.class))).thenReturn(List.of(record));

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(0),
                ArgumentMatchers.eq(470), ArgumentMatchers.eq(0), ArgumentMatchers.eq(65))).thenReturn(799_000L);

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(0),
                ArgumentMatchers.eq(470), ArgumentMatchers.eq(12), ArgumentMatchers.eq(65))).thenReturn(816_000L);

        when(pensionCalculator.calculateProjectedBasicPension(any(PensionConfigDto.class), ArgumentMatchers.eq(0),
                ArgumentMatchers.eq(470), ArgumentMatchers.eq(24), ArgumentMatchers.eq(65))).thenReturn(816_000L);

        when(salaryContextBuilder.resolveAnnualBonusInfo(any())).thenReturn(new SalaryContextBuilder.BonusInfo(0L, 0));
        when(pensionCalculator.calculateYearlyKouseiBenefitIncrement(any(PensionConfigDto.class),
                ArgumentMatchers.eq(6_000_000L), ArgumentMatchers.eq(0L), ArgumentMatchers.eq(0))).thenReturn(32_886L);
        when(pensionCalculator.calculatePensionMultiplier(65)).thenReturn(1.0);

        when(userProfileService.getUserProfile("user-1")).thenReturn(Optional.empty());

        // Act
        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null);

        // Assert
        assertThat(result).hasSize(3);

        // Year 0 (470 months) -> 816000 * 470 / 480 = 799000
        assertThat(result.get(0).getProjectedBasicPensionAnnual()).isEqualTo(799_000L);

        // Year 1 (482 months, capped at 480) -> 816000
        assertThat(result.get(1).getProjectedBasicPensionAnnual()).isEqualTo(816_000L);

        // Year 2 (494 months, capped at 480) -> 816000
        assertThat(result.get(2).getProjectedBasicPensionAnnual()).isEqualTo(816_000L);
    }
}
