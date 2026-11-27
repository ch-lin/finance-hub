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
package ch.lin.finance.hub.backend.api.app.service.simulation.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.CareerEventRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.LifeEventRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionServiceImpl;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Pre2022TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2022To2023TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2024To2025TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2026OnwardsTaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.FundOverrideApplier;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.InvestmentReturnCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationProfileResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationTimelineProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.DefinedContributionContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.NisaContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.PensionContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SalaryContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SimulationContextFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline.YearlySimulationPipeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxCalculationResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
import ch.lin.finance.hub.backend.api.common.exception.CurrencyConversionException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEventType;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;

@ExtendWith(MockitoExtension.class)
class AssetSimulationEngineImplTest {

    @Mock
    private CareerEventRepository careerEventRepository;

    @Mock
    private LifeEventRepository lifeEventRepository;

    @Mock
    private FinanceHubConfigService configService;

    @Mock
    private SalaryRecordRepository salaryRecordRepository;

    @Mock
    private DefinedContributionAccountSnapshotRepository dcRepository;

    @Mock
    private NisaSnapshotRecordRepository nisaRepository;

    @Mock
    private ETradeAccountSnapshotRepository etradeRepository;

    @Mock
    private PensionRecordRepository pensionRecordRepository;

    @Mock
    private BankTransactionRepository bankTransactionRepository;

    @Mock
    private CreditCardTransactionRepository creditCardTransactionRepository;

    @Mock
    private TransactionLinkRepository transactionLinkRepository;

    @Mock
    private DefinedContributionUserSettingsRepository dcUserSettingsRepository;

    @Mock
    private SalaryUserSettingsRepository salaryUserSettingsRepository;

    @Mock
    private NisaUserSettingsRepository nisaUserSettingsRepository;

    private IncomeExpenseCalculator incomeExpenseCalculator;
    private InvestmentReturnCalculator investmentReturnCalculator;
    private MortgageCalculator mortgageSimulationService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ActiveTaxCalculationEngine taxEngine;

    @Mock
    private NisaContributionSettingRepository nisaSettingRepository;

    @Mock
    private ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository mortgageRecordRepository;

    @Mock
    private PensionRuleConfigService pensionRuleConfigService;

    private AssetSimulationEngineImpl engine;

    @BeforeEach
    @SuppressWarnings({ "unused", "null" })
    void setUp() {
        incomeExpenseCalculator = new IncomeExpenseCalculator(bankTransactionRepository,
                creditCardTransactionRepository, transactionLinkRepository,
                salaryUserSettingsRepository);
        investmentReturnCalculator = new InvestmentReturnCalculator();
        TaxStrategyFactory taxStrategyFactory = new TaxStrategyFactory(List.of(
                new Pre2022TaxStrategy(),
                new Years2022To2023TaxStrategy(),
                new Years2024To2025TaxStrategy(),
                new Years2026OnwardsTaxStrategy()));
        mortgageSimulationService = new MortgageCalculator(taxStrategyFactory);
        PensionCalculator pensionCalculator = new PensionCalculator();
        CashflowLiquidationCalculator liquidationSimulationService = new CashflowLiquidationCalculator();
        FundOverrideApplier fundOverrideApplier = new FundOverrideApplier();

        TaxAdvantagedAccountProjector projector = new TaxAdvantagedAccountProjector(investmentReturnCalculator,
                taxEngine);

        SalaryContextBuilder salaryContextBuilder = new SalaryContextBuilder(
                salaryRecordRepository, salaryUserSettingsRepository);
        NisaContextBuilder nisaContextBuilder = new NisaContextBuilder(nisaRepository, nisaSettingRepository,
                nisaUserSettingsRepository);
        PensionContextBuilder pensionContextBuilder = new PensionContextBuilder(
                pensionRecordRepository);
        DefinedContributionContextBuilder dcContextBuilder = new DefinedContributionContextBuilder(
                dcRepository, dcUserSettingsRepository);

        SimulationContextFactory factory = new SimulationContextFactory(
                configService, pensionRuleConfigService, userProfileRepository,
                org.mockito.Mockito.mock(ChildProfileRepository.class),
                incomeExpenseCalculator,
                new CurrencyConversionServiceImpl(),
                etradeRepository, bankTransactionRepository, mortgageRecordRepository,
                org.mockito.Mockito.mock(InsurancePolicyRepository.class),
                mortgageSimulationService,
                salaryContextBuilder, nisaContextBuilder, pensionContextBuilder, dcContextBuilder,
                org.mockito.Mockito.mock(EducationConfigService.class),
                org.mockito.Mockito.mock(ChildEducationSettingsRepository.class),
                new EducationProfileResolver(),
                new EducationTimelineProjector(),
                new EducationExpenseCalculator());

        YearlySimulationPipeline pipeline = new YearlySimulationPipeline(
                incomeExpenseCalculator, mortgageSimulationService, liquidationSimulationService,
                pensionCalculator, projector, taxEngine, new YearEndAdjustmentEngine());

        engine = new AssetSimulationEngineImpl(
                factory, careerEventRepository, lifeEventRepository, mortgageRecordRepository,
                fundOverrideApplier, pipeline);

        AllConfigsData configs = new AllConfigsData();
        configs.setEnabledConfigName("default");
        when(configService.getAllConfigs()).thenReturn(configs);

        AllPensionRuleConfigsData pensionConfigs = new AllPensionRuleConfigsData("default-pension",
                Collections.singletonList("default-pension"));
        lenient().when(pensionRuleConfigService.getAllConfigs()).thenReturn(pensionConfigs);

        PensionRuleConfigResult pensionResult = PensionRuleConfigResult.builder()
                .name("default-pension")
                .enabled(true)
                .maxBasicPensionAmount(816000L)
                .kouseiSalaryCapMonthly(650_000L)
                .kouseiBonusCapPerPayment(1_500_000L)
                .kouseiMultiplierPerMille(new BigDecimal("5.481"))
                .maxBasicPensionMonths(480L)
                .build();
        lenient().when(pensionRuleConfigService.getConfig("default-pension")).thenReturn(pensionResult);

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L); // 500k monthly base
        lenient().when(configService.getConfig("default")).thenReturn(config);
        lenient().when(salaryRecordRepository.findAllByOwnerUserIdOrderByPaymentDateDesc(anyString()))
                .thenReturn(Collections.emptyList());
        lenient().when(nisaRepository.findFirstByOwnerUserIdOrderByRecordDateDesc(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(etradeRepository.findFirstByOwnerUserIdOrderByRecordDateDesc(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(nisaSettingRepository.findLatestRecordDate()).thenReturn(Optional.empty());
        lenient().when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc(anyString(), any()))
                .thenReturn(Optional.empty());

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "user1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        lenient().when(pensionRecordRepository.findAllByOwnerUserId(anyString(), any(Sort.class)))
                .thenReturn(Collections.emptyList());
        lenient().when(bankTransactionRepository.findByTransactionDateBetween(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(creditCardTransactionRepository.findByTransactionDateBetween(any(), any()))
                .thenReturn(Collections.emptyList());

        UserProfile mockProfile = new UserProfile("test-user", LocalDate.of(1996, 1, 1), 250_000L,
                65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // Mock Settings to return defaults (0 for all values)
        SalaryUserSettings mockSalarySettings = new SalaryUserSettings();
        mockSalarySettings.setOwnerUserId("test-user");
        lenient().when(salaryUserSettingsRepository.findByOwnerUserId(anyString()))
                .thenReturn(Optional.of(mockSalarySettings));

        DefinedContributionUserSettings mockDcSettings = new DefinedContributionUserSettings();
        mockDcSettings.setOwnerUserId("test-user");
        lenient().when(dcUserSettingsRepository.findByOwnerUserId(anyString()))
                .thenReturn(Optional.of(mockDcSettings));

        NisaUserSettings mockNisaSettings = new NisaUserSettings();
        mockNisaSettings.setOwnerUserId("test-user");
        lenient().when(nisaUserSettingsRepository.findByOwnerUserId(anyString()))
                .thenReturn(Optional.of(mockNisaSettings));

        // Provide a dummy tax calculation result with 0 taxes, net income = gross
        // salary
        lenient()
                .when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    long salary = invocation.getArgument(1);
                    return new TaxCalculationResult(0L, 0L, 0L, salary);
                });

        lenient().when(taxEngine.calculateRetirementTax(any(), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    long income = invocation.getArgument(1);
                    return new TaxCalculationResult(0L, 0L, 0L, income);
                });

        lenient()
                .when(taxEngine.calculatePensionTax(any(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenAnswer(invocation -> {
                    long income = invocation.getArgument(1);
                    return new TaxCalculationResult(0L, 0L, 0L, income);
                });

        lenient().when(salaryUserSettingsRepository.findByOwnerUserId(anyString())).thenReturn(Optional.empty());
        lenient().when(dcUserSettingsRepository.findByOwnerUserId(anyString())).thenReturn(Optional.empty());
        lenient().when(nisaUserSettingsRepository.findByOwnerUserId(anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRunSimulationWithCareerEventAndCapKouseiPension() {
        // Given
        int currentYear = YearMonth.now().getYear();

        // Year 1: Salary bumps to 1,000,000 / month (12,000,000 / year). Should hit
        // 650k cap.
        CareerEvent overrideEvent = new CareerEvent(
                YearMonth.of(currentYear + 1, 1),
                "Promotion",
                CareerEventType.ABSOLUTE_OVERRIDE,
                12_000_000.0);

        when(careerEventRepository.findAllByOwnerUserIdOrderByEventYearMonthAsc("user1"))
                .thenReturn(Arrays.asList(overrideEvent));

        when(lifeEventRepository.findAllByOwnerUserIdOrderByStartYearMonthAsc("user1"))
                .thenReturn(Collections.emptyList());

        // When
        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(35); // 30
        // to
        // 35

        // Then
        assertThat(simulation).hasSize(6);

        // Year 0 (Age 30): Base 500k monthly. Since there are no YTD salary records
        // mocked, it's just remaining months.
        AssetSimulationYearlyResult year0 = simulation.get(0);
        int currentMonth = java.time.YearMonth.now().getMonthValue();
        int monthsToAdd = 12 - currentMonth + 1;
        long expectedYear0Salary = monthsToAdd * 500_000L;
        assertThat(year0.getProjectedAnnualSalary()).isEqualTo(expectedYear0Salary);

        // Kousei contribution prorated for the remaining months of the first year
        double remainingRatio = monthsToAdd / 12.0;
        long expectedPension = (long) (32886 * remainingRatio);
        assertThat(year0.getKouseiPensionContribution()).isBetween(expectedPension - 500, expectedPension + 500);

        // Year 1 (Age 31): Override to 12,000,000 annual (1,000,000 monthly)
        AssetSimulationYearlyResult year1 = simulation.get(1);
        assertThat(year1.getProjectedAnnualSalary()).isEqualTo(12_000_000L);
        // Capped contribution: 650,000 * 5.481 / 1000 * 12 = 42752 (approx)
        assertThat(year1.getKouseiPensionContribution()).isBetween(42000L, 43500L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldUnlockDcAndIdecoAtWithdrawalAge() {
        // Arrange
        when(careerEventRepository.findAllByOwnerUserIdOrderByEventYearMonthAsc("user1"))
                .thenReturn(Collections.emptyList());
        when(lifeEventRepository.findAllByOwnerUserIdOrderByStartYearMonthAsc("user1"))
                .thenReturn(Collections.emptyList());
        when(salaryRecordRepository.findAllByOwnerUserIdOrderByPaymentDateDesc("user1"))
                .thenReturn(Collections.emptyList());

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L);
        config.setDcUnlockAge(60);
        config.setIdecoUnlockAge(60);
        config.setIdecoExpectedReturnRate(0.0); // No growth for simplicity in this test
        lenient().when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile profile = new UserProfile("test-user", LocalDate.now().minusYears(58), 250_000L, 65);
        profile.setTaxAdvantagedWithdrawalAge(60);
        lenient().when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(profile));

        // We only mock dcRepository because etrade and nisa are empty optional by
        // default
        DefinedContributionAccountSnapshot dcSnapshot = new DefinedContributionAccountSnapshot(LocalDate.now(),
                DefinedContributionType.CORPORATE_DC, 0L, 0L, 0L, 0L, 0L);
        DefinedContributionFundSnapshot dcFund = new DefinedContributionFundSnapshot("Type", "Fund1", "DC_FUND_1", 0.0,
                0.0, 0.0, 0L,
                1_000_000L, 0L);
        dcSnapshot.addFund(dcFund);

        DefinedContributionAccountSnapshot idecoSnapshot = new DefinedContributionAccountSnapshot(LocalDate.now(),
                DefinedContributionType.IDECO, 0L, 0L, 0L, 0L, 0L);
        DefinedContributionFundSnapshot idecoFund = new DefinedContributionFundSnapshot("Type", "Fund2", "IDECO_FUND_1",
                0.0, 0.0, 0.0,
                0L, 2_000_000L, 0L);
        idecoSnapshot.addFund(idecoFund);

        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.of(dcSnapshot));
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user1",
                DefinedContributionType.IDECO))
                .thenReturn(Optional.of(idecoSnapshot));

        // When
        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(63); // 58 -> 63

        int currentMonth = java.time.YearMonth.now().getMonthValue();
        double remainingRatio = (12 - currentMonth + 1) / 12.0;
        long firstYearSavings = (long) (3_000_000L * remainingRatio);

        // Then
        // Age 58
        AssetSimulationYearlyResult year0 = simulation.get(0);
        assertThat(year0.getProjectedDcBalance()).isEqualTo(3_000_000L); // 1M DC + 2M iDeCo
        assertThat(year0.getProjectedLiquidCash()).isEqualTo(firstYearSavings); // from net savings

        // Age 60 (DC and iDeCo Unlocks at 60: 3M goes to liquid cash)
        AssetSimulationYearlyResult year2 = simulation.get(2);
        assertThat(year2.getProjectedDcBalance()).isEqualTo(0L); // All unlocked
        // first year savings + 2 full years savings + 3M DC+iDeCo
        assertThat(year2.getProjectedLiquidCash()).isEqualTo(firstYearSavings + 3_000_000L * 2 + 3_000_000L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement() {
        // Setup Mortgage H11
        long initialMortgageBalance = 10_000_000L;
        int remainingTermMonths = 420; // 35 years
        double interestRate = 0.01; // 1%

        MortgageRecord mortgage = org.mockito.Mockito.mock(MortgageRecord.class);
        lenient().when(mortgage.getId()).thenReturn(1L);
        lenient().when(mortgage.getStartDate()).thenReturn(java.time.LocalDate.now().minusYears(1));
        lenient().when(mortgage.getLoanTermMonths()).thenReturn(remainingTermMonths);
        lenient().when(mortgage.getInterestRate()).thenReturn(interestRate);
        lenient().when(mortgage.getBalanceRecordDate()).thenReturn(java.time.LocalDate.now());
        lenient().when(mortgage.getCurrentBalance()).thenReturn(initialMortgageBalance);
        lenient().when(mortgage.getEarlyRepayments()).thenReturn(Collections.emptyList());
        lenient().when(mortgage.getRepaymentMethod()).thenReturn(RepaymentMethod.EQUAL_PAYMENT);

        when(mortgageRecordRepository.findAllByOwnerUserId("user1")).thenReturn(Arrays.asList(mortgage));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L);
        when(configService.getConfig("default")).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", java.time.LocalDate.now().minusYears(59),
                400_000L, 60); // Retiring next year (Age 60)
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // Inject 30,000,000L initial liquid cash.
        long initialCash = 30_000_000L;
        when(bankTransactionRepository.findDistinctBankNamesByOwnerUserId("user1"))
                .thenReturn(Arrays.asList("TestBank"));
        BankTransaction mockTx = org.mockito.Mockito.mock(BankTransaction.class);
        lenient().when(mockTx.getBalance()).thenReturn((int) initialCash);
        when(bankTransactionRepository.findFirstByOwnerUserIdAndBankNameOrderByTransactionDateDesc("user1",
                "TestBank")).thenReturn(mockTx);

        // Override global mock to return realistic taxes for this test only
        when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
                anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    long salary = invocation.getArgument(1);
                    long incomeTax = salary * 10 / 100;
                    long residentTax = salary * 5 / 100;
                    long socialInsurance = salary * 5 / 100;
                    return new TaxCalculationResult(incomeTax, residentTax, socialInsurance,
                            salary - incomeTax - residentTax - socialInsurance);
                });

        // --- Hand-derive exact mathematics ---
        // 1. Calculate Monthly Payment (EQUAL_PAYMENT)
        double r = interestRate / 12.0;
        double mathPow = Math.pow(1 + r, remainingTermMonths);
        long monthlyPayment = Math.round(initialMortgageBalance * r * mathPow / (mathPow - 1));
        lenient().when(mortgage.getMonthlyPayment()).thenReturn(monthlyPayment);

        // 2. Simulate amortization for Year 0 (only for the remaining months of the
        // year)
        int currentMonth = java.time.YearMonth.now().getMonthValue();
        int monthsToAddYear0 = 12 - currentMonth + 1;
        double remainingRatioYear0 = monthsToAddYear0 / 12.0;

        long remainingPrincipalAtEndOfYear0 = initialMortgageBalance;
        long totalMortgagePaidInYear0 = 0L;

        for (int month = 1; month <= 12; month++) {
            if (month >= currentMonth) {
                long interest = (long) (remainingPrincipalAtEndOfYear0 * (interestRate / 12.0));
                long principalPayment = monthlyPayment - interest;
                remainingPrincipalAtEndOfYear0 -= principalPayment;
                totalMortgagePaidInYear0 += monthlyPayment;
            }
        }

        // 3. Expected Cash Math for Year 0
        long expectedAnnualSalaryYear0 = 500_000L * 12;
        long simulatedIncomeTax = expectedAnnualSalaryYear0 * 10 / 100;
        long simulatedResidentTax = expectedAnnualSalaryYear0 * 5 / 100;
        long simulatedSocialInsurance = expectedAnnualSalaryYear0 * 5 / 100;
        long trueAnnualNetIncome = expectedAnnualSalaryYear0 - simulatedIncomeTax - simulatedResidentTax
                - simulatedSocialInsurance;
        long netIncomeYear0 = Math.round(trueAnnualNetIncome * remainingRatioYear0);
        long expectedAnnualExpensesYear0 = 400_000L * monthsToAddYear0;

        long expectedYear0Cash = initialCash + netIncomeYear0 - expectedAnnualExpensesYear0 - totalMortgagePaidInYear0;

        // 4. Simulate amortization for Year 1 (11 months of regular payments, then
        // early repayment in Month 12)
        long remainingPrincipalAtEndOfYear1 = remainingPrincipalAtEndOfYear0;
        long totalMortgagePaidInYear1 = 0L;

        for (int month = 1; month <= 11; month++) {
            long interest = (long) (remainingPrincipalAtEndOfYear1 * (interestRate / 12.0));
            long principalPayment = monthlyPayment - interest;
            remainingPrincipalAtEndOfYear1 -= principalPayment;
            totalMortgagePaidInYear1 += monthlyPayment;
        }

        // 5. Expected Cash Math for Year 1 (Retirement Year, Age 60)
        // Under DECEMBER mode (Question 191), the user works all 12 months in the
        // retirement year
        long netIncomeYear1 = trueAnnualNetIncome;
        // Expenses = Baseline (12 months) + Retiree Health Insurance
        // (TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM = 400_000L)
        long expectedAnnualExpensesYear1 = (400_000L * 12) + 400_000L;

        // Year 1 cash increases by net income, decreases by regular expenses, 12 months
        // of regular mortgage
        // payments, AND the early repayment of the remaining balance at the END of Year
        // 1.
        long expectedYear1Cash = expectedYear0Cash + netIncomeYear1 - expectedAnnualExpensesYear1
                - totalMortgagePaidInYear1
                - remainingPrincipalAtEndOfYear1;
        int retirementCalendarYear = YearMonth.now().getYear() + 1;
        Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> mortgageOverrides = new HashMap<>();
        mortgageOverrides.put(1L, Collections.singletonList(
                new SimulationOverrides.MortgageEarlyRepaymentOverride(retirementCalendarYear, Long.MAX_VALUE)));

        SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, mortgageOverrides, null,
                null, null);
        // When
        List<AssetSimulationYearlyResult> simulation = engine.runSimulationWithOverrides(61, overrides);

        // Then
        assertThat(simulation).hasSize(3);
        AssetSimulationYearlyResult year0 = simulation.get(0);
        AssetSimulationYearlyResult year1 = simulation.get(1);

        // Exact Mathematical Assertions
        assertThat(year0.getProjectedLiquidCash()).isEqualTo(expectedYear0Cash);
        assertThat(year1.getProjectedLiquidCash()).isEqualTo(expectedYear1Cash);
    }

    @Test
    @SuppressWarnings("null")
    void shouldClampNisaAnnualContributionToLifetimeLimit() {
        // Setup NISA H12: Growth quota fund near the 12M lifetime limit
        NisaSnapshotRecord nisaSnapshot = Mockito.mock(NisaSnapshotRecord.class);
        lenient().when(nisaSnapshot.getRecordDate()).thenReturn(LocalDate.now());
        lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (成長)預り");
        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
        lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
        lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(11_900_000L);
        lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(11_900_000L);

        when(nisaRepository.findFirstByOwnerUserIdOrderByRecordDateDesc("user1"))
                .thenReturn(Optional.of(nisaSnapshot));
        when(nisaRepository.findByOwnerUserIdAndRecordDate(anyString(), any()))
                .thenReturn(Arrays.asList(nisaSnapshot));

        // Mock NisaContributionSettingRepository to provide 200k monthly contribution
        // (2.4M annually)
        NisaContributionSetting setting = new NisaContributionSetting(
                LocalDate.now(), "NISA (成長)預り", "03311187", "Fund1", "Monthly", 200_000L);
        setting.updateExpectedReturnRate(0.0); // 0% return for easier math

        when(nisaSettingRepository.findAllByOwnerUserIdOrderByRecordDateDesc("user1"))
                .thenReturn(Arrays.asList(setting));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setNisaExpectedReturnRate(0.0); // Override global config to 0
        config.setPensionExpectedMonthlySalary(500_000L); // Give a salary to prevent bankruptcy liquidation
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", LocalDate.of(1996, 1, 1), 100_000L,
                65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(32);

        // First year contribution should be clamped to 100k (remaining growth lifetime
        // quota) and rest goes
        // to liquid cash
        AssetSimulationYearlyResult year1 = simulation.get(1); // Year 1 full projection

        // 11.9M initial + 100k contribution = 12.0M
        assertThat(year1.getProjectedNisaBalance()).isEqualTo(12_000_000L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow() {
        // Setup M5: NISA principal is completely full (18,000,000)
        NisaSnapshotRecord nisaSnapshot = org.mockito.Mockito.mock(NisaSnapshotRecord.class);
        lenient().when(nisaSnapshot.getRecordDate()).thenReturn(java.time.LocalDate.now());
        lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (つみたて)預り");
        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
        lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
        lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(18_000_000L);
        lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(18_000_000L);

        when(nisaRepository.findFirstByOwnerUserIdOrderByRecordDateDesc("user1")).thenReturn(Optional.of(nisaSnapshot));
        when(nisaRepository.findByOwnerUserIdAndRecordDate(anyString(), any())).thenReturn(Arrays.asList(nisaSnapshot));

        // NISA setting: 100k monthly (1.2M annually), 0% return for simple math
        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (つみたて)預り", "03311187",
                "Fund1",
                "Monthly", 100_000L);
        setting.updateExpectedReturnRate(0.0);
        when(nisaSettingRepository.findAllByOwnerUserIdOrderByRecordDateDesc("user1"))
                .thenReturn(Arrays.asList(setting));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setNisaExpectedReturnRate(0.0);
        config.setPensionExpectedMonthlySalary(500_000L); // Give a salary to prevent bankruptcy liquidation in Year 2
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", java.time.LocalDate.of(1996, 1, 1), 100_000L,
                65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // Force a negative cashflow via LifeEvent in Year 1 to sell NISA
        // Setting it to 22M ensures it depletes 18M NISA but doesn't create
        // insurmountable debt for Year 2
        LifeEvent bigExpense = org.mockito.Mockito.mock(LifeEvent.class);
        lenient().when(bigExpense.getCostPerOccurrence()).thenReturn(22_000_000L);
        lenient().when(bigExpense.getFrequency()).thenReturn(LifeEventFrequency.ONE_TIME);
        lenient().when(bigExpense.getStartYearMonth()).thenReturn(YearMonth.now().plusYears(1).withMonth(1)); // Year 1
        lenient().when(bigExpense.getEndYearMonth()).thenReturn(YearMonth.now().plusYears(1).withMonth(1));

        when(lifeEventRepository.findAllByOwnerUserIdOrderByStartYearMonthAsc("user1"))
                .thenReturn(Arrays.asList(bigExpense));

        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(32);

        AssetSimulationYearlyResult year1 = simulation.get(1);
        long nisaBalanceYear1 = year1.getProjectedNisaBalance();
        // NISA should be sold substantially to cover the deficit, dropping well
        // below 18M
        assertThat(nisaBalanceYear1).isLessThan(18_000_000L);

        AssetSimulationYearlyResult year2 = simulation.get(2);
        // If principal carryover works correctly, the sold principal is recovered.
        // Because Year 1 balance < 18M, the 1.2M annual NISA contribution in Year 2
        // must successfully go through.
        // If the carryover logic is broken, the lifetime limit remains 18M and blocks
        // this contribution.
        assertThat(year2.getProjectedNisaBalance()).isEqualTo(nisaBalanceYear1 + 1_200_000L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldRecoverGrowthNisaPrincipalAcrossYearsOnNegativeCashflow() {
        // Setup M5: Growth NISA principal is completely full (12,000,000)
        NisaSnapshotRecord nisaSnapshot = Mockito.mock(NisaSnapshotRecord.class);
        lenient().when(nisaSnapshot.getRecordDate()).thenReturn(java.time.LocalDate.now());
        lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (成長)預り");
        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
        lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
        lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(12_000_000L);
        lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(12_000_000L);

        when(nisaRepository.findFirstByOwnerUserIdOrderByRecordDateDesc("user1")).thenReturn(Optional.of(nisaSnapshot));
        when(nisaRepository.findByOwnerUserIdAndRecordDate(anyString(), any())).thenReturn(Arrays.asList(nisaSnapshot));

        // NISA setting: 200k monthly (2.4M annually), 0% return for simple math
        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (成長)預り", "03311187",
                "Fund1",
                "Monthly", 200_000L);
        setting.updateExpectedReturnRate(0.0);
        when(nisaSettingRepository.findAllByOwnerUserIdOrderByRecordDateDesc("user1"))
                .thenReturn(Arrays.asList(setting));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setNisaExpectedReturnRate(0.0);
        config.setPensionExpectedMonthlySalary(500_000L); // Give a salary to prevent bankruptcy liquidation in Year 2
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", java.time.LocalDate.of(1996, 1, 1), 100_000L,
                65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // Force a negative cashflow via LifeEvent in Year 1 to sell NISA
        // Setting it to 16M ensures it depletes 12M NISA but doesn't create
        // insurmountable debt for Year 2
        LifeEvent bigExpense = Mockito.mock(LifeEvent.class);
        lenient().when(bigExpense.getCostPerOccurrence()).thenReturn(16_000_000L);
        lenient().when(bigExpense.getFrequency()).thenReturn(LifeEventFrequency.ONE_TIME);
        lenient().when(bigExpense.getStartYearMonth()).thenReturn(YearMonth.now().plusYears(1).withMonth(1)); // Year 1
        lenient().when(bigExpense.getEndYearMonth()).thenReturn(YearMonth.now().plusYears(1).withMonth(1));

        when(lifeEventRepository.findAllByOwnerUserIdOrderByStartYearMonthAsc("user1"))
                .thenReturn(Arrays.asList(bigExpense));

        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(32);

        AssetSimulationYearlyResult year1 = simulation.get(1);
        long nisaBalanceYear1 = year1.getProjectedNisaBalance();
        // NISA should be sold substantially to cover the deficit, dropping well
        // below 12M
        assertThat(nisaBalanceYear1).isLessThan(12_000_000L);

        AssetSimulationYearlyResult year2 = simulation.get(2);
        // If principal carryover works correctly, the sold principal is recovered.
        // Because Year 1 balance < 12M, the 2.4M annual Growth NISA contribution in
        // Year 2
        // must successfully go through.
        // If the carryover logic is broken, the lifetime limit remains 12M and blocks
        // this contribution.
        assertThat(year2.getProjectedNisaBalance()).isEqualTo(nisaBalanceYear1 + 2_400_000L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldThrowExceptionWhenETradeHasNullExchangeRateAndNoFallbackAvailable() {
        // Setup ETrade with USD and null exchange rate
        ETradeAccountSnapshot snapshot = Mockito.mock(ETradeAccountSnapshot.class);

        lenient().when(snapshot.getRecordDate()).thenReturn(LocalDate.now());
        lenient().when(snapshot.getCurrentAccountValue())
                .thenReturn(MonetaryAmount.of(50000L, CurrencyCode.USD));
        lenient().when(snapshot.getExchangeRate()).thenReturn(null);

        when(etradeRepository.findFirstByOwnerUserIdOrderByRecordDateDesc("user1"))
                .thenReturn(Optional.of(snapshot));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L);
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", LocalDate.of(1996, 1, 1), 100_000L, 65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // When/Then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.runSimulation(30))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("No valid exchange rate found between USD and JPY");
    }

    @Test
    @SuppressWarnings("null")
    void shouldReconcileTaxesAndNetIncomeUsingYearEndAdjustmentEngineForYear0() {
        // Setup YTD data
        int currentYear = YearMonth.now().getYear();
        SalaryRecord mockRecord = org.mockito.Mockito.mock(SalaryRecord.class);
        lenient().when(mockRecord.getPaymentDate()).thenReturn(LocalDate.of(currentYear, 1, 25));
        lenient().when(mockRecord.getType()).thenReturn(SalaryType.MONTHLY_SALARY);
        lenient().when(mockRecord.getGrossAmount()).thenReturn(500_000L);
        lenient().when(mockRecord.getBaseSalary()).thenReturn(500_000L);
        lenient().when(mockRecord.getIncomeTax()).thenReturn(20_000L);
        lenient().when(mockRecord.getResidentTax()).thenReturn(15_000L);
        lenient().when(mockRecord.getHealthInsurance()).thenReturn(25_000L);
        lenient().when(mockRecord.getPension()).thenReturn(45_000L);
        lenient().when(mockRecord.getEmploymentInsurance()).thenReturn(3_000L);
        lenient().when(mockRecord.getNetAmount()).thenReturn(392_000L);

        when(salaryRecordRepository.findAllByOwnerUserIdOrderByPaymentDateDesc("user1"))
                .thenReturn(Arrays.asList(mockRecord));

        // Stub the tax engine to return a non-zero tax result for the full year
        when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
                anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    long salary = invocation.getArgument(1);
                    // Let's pretend taxes are roughly 20% of salary
                    long incomeTax = salary * 10 / 100;
                    long residentTax = salary * 5 / 100;
                    long socialInsurance = salary * 5 / 100;
                    return new TaxCalculationResult(incomeTax, residentTax, socialInsurance,
                            salary - incomeTax - residentTax - socialInsurance);
                });

        FinanceHubConfig config = new FinanceHubConfig("default");
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", LocalDate.now().minusYears(30), 100_000L, 65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // When
        List<AssetSimulationYearlyResult> simulation = engine.runSimulation(30);

        // Then
        assertThat(simulation).hasSize(1);
        AssetSimulationYearlyResult year0 = simulation.get(0);

        // Assert Year-1 netIncome correctly reflects the remaining net income (Full
        // Year Net Income - YTD Net Income)
        long fullYearGross = year0.getProjectedAnnualSalary();
        long fullYearTaxes = (fullYearGross * 10 / 100) + (fullYearGross * 5 / 100) + (fullYearGross * 5 / 100);
        long fullYearNetIncome = fullYearGross - fullYearTaxes;
        long mockYtdNetIncome = 500_000L - 20_000L - 15_000L - 25_000L - 45_000L - 3_000L; // 392,000L
        assertThat(year0.getNetIncome()).isEqualTo(fullYearNetIncome - mockYtdNetIncome);

        // Assert the remaining taxes correctly subtracted YTD
        long expectedTrueAnnualSalary = year0.getProjectedAnnualSalary();
        long expectedFullIncomeTax = expectedTrueAnnualSalary * 10 / 100;
        assertThat(year0.getEstimatedIncomeTax()).isEqualTo(expectedFullIncomeTax - 20_000L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldUseUserSettingsAsFallbackWhenSnapshotsAreMissing() {
        // Arrange
        // No snapshots
        when(nisaRepository.findFirstByOwnerUserIdOrderByRecordDateDesc("user1")).thenReturn(Optional.empty());
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user1",
                DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.empty());
        when(dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc("user1",
                DefinedContributionType.IDECO))
                .thenReturn(Optional.empty());

        // But we DO have Profile settings
        NisaUserSettings nisaSettings = new NisaUserSettings();
        nisaSettings.setMonthlyGrowthContribution(30_000L);
        nisaSettings.setNisaExpectedReturnRate(0.04);
        when(nisaUserSettingsRepository.findByOwnerUserId("user1")).thenReturn(Optional.of(nisaSettings));

        DefinedContributionUserSettings dcSettings = new DefinedContributionUserSettings();
        dcSettings.setMonthlyIdecoContribution(20_000L);
        dcSettings.setIdecoExpectedReturnRate(0.03);
        dcSettings.setMonthlyDcMatchingContribution(10_000L);
        when(dcUserSettingsRepository.findByOwnerUserId("user1")).thenReturn(Optional.of(dcSettings));

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setPensionExpectedMonthlySalary(500_000L);
        config.setIdecoExpectedReturnRate(0.05); // Global config
        when(configService.getConfig(anyString())).thenReturn(config);

        UserProfile mockProfile = new UserProfile("test-user", LocalDate.now().minusYears(30), 100_000L, 65);
        when(userProfileRepository.findById(anyString())).thenReturn(Optional.of(mockProfile));

        // When
        SimulationData data = engine.fetchSimulationData();
        SimulationContext context = data.getBaseContext();

        // Then
        // 1. NISA virtual fund
        assertThat(context.getNisaFunds()).hasSize(1);
        SimulationFund dummyNisa = context.getNisaFunds().get(0);
        assertThat(dummyNisa.getExpectedReturnRate()).isEqualTo(0.04);

        // 2. iDeCo virtual fund
        assertThat(context.getIdecoFunds()).hasSize(1);
        SimulationFund dummyIdeco = context.getIdecoFunds().get(0);
        assertThat(dummyIdeco.getExpectedReturnRate()).isEqualTo(0.03);

        // 3. DC Matching virtual fund
        assertThat(context.getDcFunds()).hasSize(1);
        SimulationFund dummyDc = context.getDcFunds().get(0);
        // DC Matching falls back to global iDeCo return rate when missing
        assertThat(dummyDc.getExpectedReturnRate()).isEqualTo(0.05);
    }
}
