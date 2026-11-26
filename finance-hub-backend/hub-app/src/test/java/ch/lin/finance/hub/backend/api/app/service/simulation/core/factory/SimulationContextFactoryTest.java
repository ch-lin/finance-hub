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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationAbsoluteTimeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationProfileResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationTimelineProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.ResolvedEducationProfile;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class SimulationContextFactoryTest {

    @Mock
    private FinanceHubConfigService configService;
    @Mock
    private PensionRuleConfigService pensionRuleConfigService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ChildProfileRepository childProfileRepository;
    @Mock
    private IncomeExpenseCalculator incomeExpenseCalculator;
    @Mock
    private CurrencyConversionService currencyConversionService;
    @Mock
    private ETradeAccountSnapshotRepository etradeRepository;
    @Mock
    private BankTransactionRepository bankTransactionRepository;
    @Mock
    private MortgageRecordRepository mortgageRecordRepository;
    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;
    @Mock
    private MortgageCalculator mortgageSimulationService;
    @Mock
    private SalaryContextBuilder salaryContextBuilder;
    @Mock
    private NisaContextBuilder nisaContextBuilder;
    @Mock
    private PensionContextBuilder pensionContextBuilder;
    @Mock
    private DefinedContributionContextBuilder dcContextBuilder;

    @Mock
    private EducationConfigService educationConfigService;
    @Mock
    private ChildEducationSettingsRepository childEducationSettingsRepository;
    @Mock
    private EducationProfileResolver educationProfileResolver;
    @Mock
    private EducationTimelineProjector educationTimelineProjector;
    @Mock
    private EducationExpenseCalculator educationExpenseCalculator;

    @InjectMocks
    private SimulationContextFactory factory;

    @BeforeEach
    void setUp() {
        when(configService.getAllConfigs()).thenReturn(new AllConfigsData("default", List.of("default")));
        when(pensionRuleConfigService.getAllConfigs())
                .thenReturn(new AllPensionRuleConfigsData("default-pension", List.of("default-pension")));
        when(configService.getConfig("default")).thenReturn(new FinanceHubConfig("default"));
    }

    @Test
    void shouldCreateContextWithPensionConfigDto() {
        // Arrange
        PensionRuleConfigResult pensionConfigResult = PensionRuleConfigResult.builder()
                .name("default-pension")
                .enabled(true)
                .maxBasicPensionAmount(816000L)
                .maxBasicPensionMonths(480L)
                .kouseiMultiplierPerMille(new BigDecimal("5.481"))
                .kouseiSalaryCapMonthly(650000L)
                .kouseiBonusCapPerPayment(1500000L)
                .build();
        when(pensionRuleConfigService.getConfig("default-pension")).thenReturn(pensionConfigResult);

        when(insurancePolicyRepository.findAll()).thenReturn(List.of());

        // Act
        SimulationContext context = factory.createContext();

        // Assert
        assertThat(context.getPensionConfig()).isNotNull();
        assertThat(context.getPensionConfig().getMaxBasicPensionAmount()).isEqualTo(816000L);
        assertThat(context.getPensionConfig().getMaxBasicPensionMonths()).isEqualTo(480L);
        assertThat(context.getPensionConfig().getKouseiMultiplierPerMille()).isEqualTo(new BigDecimal("5.481"));
        assertThat(context.getPensionConfig().getKouseiSalaryCapMonthly()).isEqualTo(650000L);
        assertThat(context.getPensionConfig().getKouseiBonusCapPerPayment()).isEqualTo(1500000L);
    }

    @Test
    void shouldPrecomputeEducationCostCacheWhenChildrenExist() {
        // Arrange
        LocalDate birthDate = LocalDate.of(2020, 5, 10);
        ChildProfile child = ChildProfile.builder()
                .name("Taro")
                .birthDate(birthDate)
                .build();
        ReflectionTestUtils.setField(Objects.requireNonNull(child), "id", 101L);

        when(childProfileRepository.findAll()).thenReturn(List.of(child));
        when(insurancePolicyRepository.findAll()).thenReturn(List.of());

        PensionRuleConfigResult pensionConfigResult = PensionRuleConfigResult.builder()
                .name("default-pension")
                .build();
        when(pensionRuleConfigService.getConfig("default-pension")).thenReturn(pensionConfigResult);

        EducationConfig mockEduConfig = org.mockito.Mockito.mock(EducationConfig.class);
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(mockEduConfig);
        when(childEducationSettingsRepository.findByChildProfileId(101L)).thenReturn(Optional.empty());

        ResolvedEducationProfile mockProfile = org.mockito.Mockito.mock(ResolvedEducationProfile.class);
        when(educationProfileResolver.resolve(ArgumentMatchers.any(), ArgumentMatchers.eq(mockEduConfig)))
                .thenReturn(mockProfile);

        EducationAbsoluteTimeline mockTimeline = org.mockito.Mockito.mock(EducationAbsoluteTimeline.class);
        when(educationTimelineProjector.project(birthDate, mockProfile)).thenReturn(mockTimeline);

        Map<Integer, Long> mockCostMap = Map.of(2026, 500000L, 2027, 600000L);
        when(educationExpenseCalculator.buildAnnualCostMap(ArgumentMatchers.eq(mockTimeline), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(mockCostMap);

        // Act
        SimulationContext context = factory.createContext();

        // Assert
        assertThat(context.getChildEducationCostCache()).isNotNull();
        assertThat(context.getChildEducationCostCache()).containsKey(101L);
        assertThat(context.getEducationCost(101L, 2026)).isEqualTo(500000L);
        assertThat(context.getEducationCost(101L, 2027)).isEqualTo(600000L);
        assertThat(context.getEducationCost(101L, 2028)).isEqualTo(0L);
    }
}
