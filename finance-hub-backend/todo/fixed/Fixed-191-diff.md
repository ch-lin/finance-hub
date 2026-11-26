 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT  git --no-pager show 9e2b6e49                                   10012  12.02G   3.21    15:30:58  
commit 9e2b6e49adc2d2d80567f3321105da2d5399985d
Author: Che-Hung Lin <37680512+ch-lin@users.noreply.github.com>
Date:   Wed Nov 4 11:41:20 2026 +0900

    191

diff --git a/.env.base b/.env.base
index d981b507..53c5423f 100644
--- a/.env.base
+++ b/.env.base
@@ -68,6 +68,7 @@ FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
 FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
+FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/.env.dev b/.env.dev
index e09d9e4f..689d8d39 100644
--- a/.env.dev
+++ b/.env.dev
@@ -67,6 +67,7 @@ FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
 FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
+FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/.env.prod b/.env.prod
index bde5d303..7e991c6c 100644
--- a/.env.prod
+++ b/.env.prod
@@ -69,6 +69,7 @@ FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
 FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
+FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
index dd8ff7d4..c1b6f42f 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
@@ -91,6 +91,7 @@ public class DefaultConfigFactory {
         config.setGaMutationRate(properties.getGaMutationRate());
         config.setGaEliteRatio(properties.getGaEliteRatio());
         config.setSimulationEarlyRepaymentMonth(properties.getSimulationEarlyRepaymentMonth());
+        config.setRetirementMonthMode(properties.getRetirementMonthMode());
 
         return config;
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
index da56afaa..a0f2dd1d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
@@ -29,6 +29,7 @@ import java.util.Map;
 import org.springframework.boot.context.properties.ConfigurationProperties;
 import org.springframework.stereotype.Component;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
 import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
@@ -117,5 +118,6 @@ public class FinanceHubDefaultProperties {
 
         // Simulation
         private Integer simulationEarlyRepaymentMonth = 12;
+        private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
index 373d0e1d..ac8a0d57 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
@@ -87,12 +87,13 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
     /**
      * Constructs the service with its required dependencies.
      *
-     * @param repository The repository for {@link FinanceHubConfig} entities.
+     * @param repository        The repository for {@link FinanceHubConfig}
+     *                          entities.
      * @param defaultProperties The externalized properties for the default
-     * config.
-     * @param factory The factory for creating default config instances.
-     * @param eventPublisher The event publisher used to notify listeners of
-     * changes.
+     *                          config.
+     * @param factory           The factory for creating default config instances.
+     * @param eventPublisher    The event publisher used to notify listeners of
+     *                          changes.
      */
     public FinanceHubConfigServiceImpl(FinanceHubConfigRepository repository,
             FinanceHubDefaultProperties defaultProperties,
@@ -210,6 +211,9 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
         if (command.getSimulationEarlyRepaymentMonth() != null) {
             newConfig.setSimulationEarlyRepaymentMonth(command.getSimulationEarlyRepaymentMonth());
         }
+        if (command.getRetirementMonthMode() != null) {
+            newConfig.setRetirementMonthMode(command.getRetirementMonthMode());
+        }
 
         FinanceHubConfig saved = repository.save(newConfig);
         eventPublisher.publishEvent(new ConfigUpdatedEvent(this));
@@ -263,7 +267,7 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
         String defaultName = defaultProperties.getSystemConfig().getName();
         return repository.findByName(name)
                 .or(() -> defaultName.equalsIgnoreCase(name) ? Optional.of(findOrCreateDefaultConfig())
-                : Optional.empty())
+                        : Optional.empty())
                 .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));
     }
 
@@ -307,6 +311,7 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
         command.getGaMutationRate().ifPresent(config::setGaMutationRate);
         command.getGaEliteRatio().ifPresent(config::setGaEliteRatio);
         command.getSimulationEarlyRepaymentMonth().ifPresent(config::setSimulationEarlyRepaymentMonth);
+        command.getRetirementMonthMode().ifPresent(config::setRetirementMonthMode);
 
         FinanceHubConfig savedConfig = repository.save(config);
 
@@ -390,6 +395,9 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
             if (dbConfig.getSimulationEarlyRepaymentMonth() == null) {
                 dbConfig.setSimulationEarlyRepaymentMonth(defaultProps.getSimulationEarlyRepaymentMonth());
             }
+            if (dbConfig.getRetirementMonthMode() == null) {
+                dbConfig.setRetirementMonthMode(defaultProps.getRetirementMonthMode());
+            }
             return dbConfig;
         }).orElseGet(this::findOrCreateDefaultConfig);
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
index 431f4472..2a803da7 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.config.system.command;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import jakarta.validation.constraints.Max;
 import jakarta.validation.constraints.Min;
@@ -106,4 +107,5 @@ public class CreateFinanceHubConfigCommand {
     Integer gaMaxGenerations;
     Double gaMutationRate;
     Integer simulationEarlyRepaymentMonth;
+    RetirementMonthMode retirementMonthMode;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
index d2619778..15cb8d93 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
@@ -25,6 +25,7 @@ package ch.lin.finance.hub.backend.api.app.service.config.system.command;
 
 import java.util.Optional;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import lombok.Builder;
 import lombok.Value;
@@ -122,4 +123,6 @@ public class UpdateFinanceHubConfigCommand {
     Optional<Integer> gaEliteRatio = Optional.empty();
     @Builder.Default
     Optional<Integer> simulationEarlyRepaymentMonth = Optional.empty();
+    @Builder.Default
+    Optional<RetirementMonthMode> retirementMonthMode = Optional.empty();
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
index e171c781..09c0136a 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
@@ -24,6 +24,7 @@
 package ch.lin.finance.hub.backend.api.app.service.config.system.result;
 
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import lombok.Data;
 
@@ -53,6 +54,7 @@ public class FinanceHubConfigResult {
     private Double gaMutationRate;
     private Integer gaEliteRatio;
     private Integer simulationEarlyRepaymentMonth;
+    private RetirementMonthMode retirementMonthMode;
 
     /**
      * Converts a FinanceHubConfig domain entity to a FinanceHubConfigResult.
@@ -88,6 +90,7 @@ public class FinanceHubConfigResult {
         result.setGaMutationRate(entity.getGaMutationRate());
         result.setGaEliteRatio(entity.getGaEliteRatio());
         result.setSimulationEarlyRepaymentMonth(entity.getSimulationEarlyRepaymentMonth());
+        result.setRetirementMonthMode(entity.getRetirementMonthMode());
 
         return result;
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
index ff8a0e91..746fa089 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
@@ -63,7 +63,7 @@ public class IncomeExpenseCalculator {
 
     public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents, Long ytdSalary,
             Integer monthsToAdd) {
-        if (context.isRetired()) {
+        if (context.isRetired() && (monthsToAdd == null || monthsToAdd == 0)) {
             context.setCurrentAnnualSalary(0L);
             return 0L;
         } else {
@@ -230,7 +230,8 @@ public class IncomeExpenseCalculator {
                         }
                     }
                     // Education cost for transition year is already accurately pre-calculated by
-                    // EducationExpenseCalculator (filtering events >= currentMonth and prorating stage costs),
+                    // EducationExpenseCalculator (filtering events >= currentMonth and prorating
+                    // stage costs),
                     // so no erroneous linear scaling (monthsToAdd / 12.0) should be applied here.
                     annualExpenses += eduCost;
                 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index fd8f01a1..886c5c26 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -353,8 +353,9 @@ public class MortgageCalculator {
                     }
 
                     boolean isOccupancyYear = (year == occupancyDate.getYear());
+                    boolean noYearEndAdjustmentAvailable = isOccupancyYear || context.isRetired();
 
-                    if (isOccupancyYear) {
+                    if (noYearEndAdjustmentAvailable) {
                         kakuteiDeduction = deduction;
                         yearEndDeduction = 0L;
                     } else {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java
new file mode 100644
index 00000000..17b6502a
--- /dev/null
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java
@@ -0,0 +1,63 @@
+/*
+ * The MIT License (MIT)
+ *
+ * Copyright (c) 2026 Che-Hung Lin
+ *
+ * Permission is hereby granted, free of charge, to any person obtaining a copy
+ * of this software and associated documentation files (the "Software"), to deal
+ * in the Software without restriction, including without limitation the rights
+ * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
+ * copies of the Software, and to permit persons to whom the Software is
+ * furnished to do so, subject to the following conditions:
+ *
+ * The above copyright notice and this permission notice shall be included in
+ * all copies or substantial portions of the Software.
+ *
+ * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
+ * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
+ * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
+ * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
+ * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
+ * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
+ * THE SOFTWARE.
+ */
+package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;
+
+import java.time.LocalDate;
+
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
+
+/**
+ * Utility to resolve the retirement month (the last active working month)
+ * for a user during their target retirement year.
+ */
+public final class RetirementMonthResolver {
+
+    private RetirementMonthResolver() {
+        // Utility class
+    }
+
+    /**
+     * Resolves the last working month (1-12) of employment for the retirement year.
+     * <p>
+     * - Under {@link RetirementMonthMode#DECEMBER} (or if mode is
+     * null/unspecified),
+     * returns 12 regardless of whether birthDate is present (the user works through
+     * the end of December).
+     * - Under {@link RetirementMonthMode#BIRTH_MONTH}, returns the month of
+     * {@code birthDate} (1-12),
+     * or {@code null} if {@code birthDate} is unavailable.
+     *
+     * @param birthDate the user's birth date, or null if unknown
+     * @param mode      the retirement month mode configured, or null for default
+     *                  DECEMBER
+     * @return the last calendar month of working (1-12), or null if birthDate is
+     *         required but missing
+     */
+    public static Integer resolveRetirementMonth(LocalDate birthDate, RetirementMonthMode mode) {
+        if (mode == RetirementMonthMode.BIRTH_MONTH) {
+            return birthDate != null ? birthDate.getMonthValue() : null;
+        }
+        return 12; // DECEMBER mode (default)
+    }
+}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
index cceb5020..11a6a685 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
@@ -32,5 +32,6 @@ public record PreviousYearTaxRecord(
         long taxableIncomeForIncomeTax,
         long baseIncomeTax,
         long pendingKakuteiShinkokuRefund,
-        long unusedMortgageDeductionForInhabitantTax
-) {}
+        long unusedMortgageDeductionForInhabitantTax,
+        long pendingWorkingSegmentTaxAdjustment) {
+}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index a596fe0d..598651e7 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -36,6 +36,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.Mor
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.RetirementMonthResolver;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
@@ -46,6 +47,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdj
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentResult;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.TaxDateUtils;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
 import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
 import lombok.RequiredArgsConstructor;
@@ -78,10 +80,20 @@ public class YearlySimulationPipeline {
         // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and
         // inhabitant tax rollover credit)
         PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
-        if (prevTax != null && prevTax.pendingKakuteiShinkokuRefund() > 0) {
-            context.setCurrentLiquidCash(context.getCurrentLiquidCash() + prevTax.pendingKakuteiShinkokuRefund());
-            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) upon cash disbursement
-            // to prevent duplicate payout.
+        if (prevTax != null) {
+            long refundPayout = 0L;
+            if (prevTax.pendingKakuteiShinkokuRefund() > 0) {
+                refundPayout += prevTax.pendingKakuteiShinkokuRefund();
+            }
+            if (prevTax.pendingWorkingSegmentTaxAdjustment() != 0) {
+                refundPayout += prevTax.pendingWorkingSegmentTaxAdjustment();
+            }
+            if (refundPayout != 0) {
+                context.setCurrentLiquidCash(context.getCurrentLiquidCash() + refundPayout);
+            }
+            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) and
+            // pendingWorkingSegmentTaxAdjustment (0L)
+            // upon cash disbursement to prevent duplicate payout.
             // DO NOT set context.setPreviousYearTaxRecord(null) here:
             // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in
             // context so that
@@ -93,9 +105,17 @@ public class YearlySimulationPipeline {
                     prevTax.taxableIncomeForIncomeTax(),
                     prevTax.baseIncomeTax(),
                     0L, // Refund disbursed; zeroed to prevent double redemption
-                    prevTax.unusedMortgageDeductionForInhabitantTax()));
+                    prevTax.unusedMortgageDeductionForInhabitantTax(),
+                    0L // Adjustment disbursed; zeroed to prevent double redemption
+            ));
         }
 
+        boolean isRetirementYear = (age == targetRetirementAge);
+        LocalDate birthDate = (context.getProfile() != null) ? context.getProfile().getBirthDate() : null;
+        RetirementMonthMode mode = (context.getConfig() != null) ? context.getConfig().getRetirementMonthMode() : null;
+        Integer retirementMonth = isRetirementYear ? RetirementMonthResolver.resolveRetirementMonth(birthDate, mode)
+                : null;
+
         int monthsToAdd = 12;
         int currentMonth = nowMonth.getMonthValue();
         if (age == initialAge) {
@@ -105,16 +125,43 @@ public class YearlySimulationPipeline {
         long annualizedSalaryRate;
         long effectiveAnnualSalary;
 
-        if (age == initialAge) {
-            int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
-                    ? 12 - currentMonth
-                    : monthsToAdd;
-            effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
-                    context, careerEvents, context.getYtdBaseSalary(), salaryMonthsToAdd);
-            annualizedSalaryRate = context.getCurrentAnnualSalary();
+        if (isRetirementYear && retirementMonth == null) {
+            // birthDate missing under BIRTH_MONTH mode: fallback to full-year retired (0
+            // working months)
+            annualizedSalaryRate = 0L;
+            effectiveAnnualSalary = 0L;
+        } else if (isRetirementYear && retirementMonth != null) {
+            // Split retirement year (worked retirementMonth months)
+            if (age == initialAge) {
+                if (retirementMonth < currentMonth) {
+                    // Already retired prior to current month; salary already in bank deposits/YTD
+                    effectiveAnnualSalary = 0L;
+                    annualizedSalaryRate = 0L;
+                } else {
+                    int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
+                            ? retirementMonth - currentMonth
+                            : retirementMonth - currentMonth + 1;
+                    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
+                            context, careerEvents, context.getYtdBaseSalary(), Math.max(0, salaryMonthsToAdd));
+                    annualizedSalaryRate = context.getCurrentAnnualSalary();
+                }
+            } else {
+                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
+                        context, careerEvents, null, retirementMonth);
+                annualizedSalaryRate = context.getCurrentAnnualSalary();
+            }
         } else {
-            annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
-            effectiveAnnualSalary = annualizedSalaryRate;
+            if (age == initialAge) {
+                int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
+                        ? 12 - currentMonth
+                        : monthsToAdd;
+                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
+                        context, careerEvents, context.getYtdBaseSalary(), salaryMonthsToAdd);
+                annualizedSalaryRate = context.getCurrentAnnualSalary();
+            } else {
+                annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
+                effectiveAnnualSalary = annualizedSalaryRate;
+            }
         }
         long annualExpenses = incomeExpenseCalculator.calculateExpensesForYear(context, lifeEvents, monthsToAdd,
                 currentMonth);
@@ -129,26 +176,45 @@ public class YearlySimulationPipeline {
         state.setAnnualExpenses(annualExpenses);
 
         return calculateTaxesAndBuildResult(context, pensionStartAge, initialAge, age, monthsToAdd,
-                annualizedSalaryRate, effectiveAnnualSalary, state, mortgageResult, contributions);
+                annualizedSalaryRate, effectiveAnnualSalary, state, mortgageResult, contributions,
+                isRetirementYear, retirementMonth);
     }
 
     private AssetSimulationYearlyResult calculateTaxesAndBuildResult(SimulationContext context,
             int pensionStartAge, int initialAge, int age, int monthsToAdd,
             long annualizedSalaryRate, long effectiveAnnualSalary, YearlySimulationState state,
-            MortgageYearlyResult mortgageResult, InvestmentContributions contributions) {
+            MortgageYearlyResult mortgageResult, InvestmentContributions contributions,
+            boolean isRetirementYear, Integer retirementMonth) {
+
+        int workingMonths = monthsToAdd;
+        int pensionMonths = monthsToAdd;
+        boolean isHandlingPreRetirementSegment = false;
+        boolean isHandlingPostRetirementSegment = false;
+
+        if (isRetirementYear) {
+            workingMonths = (retirementMonth != null) ? retirementMonth : 0;
+            pensionMonths = 12 - workingMonths;
+            isHandlingPreRetirementSegment = true;
+            isHandlingPostRetirementSegment = true;
+        }
 
-        applyWorkingTaxes(context, state, age, initialAge, monthsToAdd, annualizedSalaryRate, effectiveAnnualSalary,
-                mortgageResult, contributions);
+        applyWorkingTaxes(context, state, age, initialAge, workingMonths, annualizedSalaryRate, effectiveAnnualSalary,
+                mortgageResult, contributions, isHandlingPreRetirementSegment);
         log.debug("Age {} [After Working Taxes]: {}", age, state);
 
-        applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge);
+        applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge,
+                isHandlingPostRetirementSegment, pensionMonths, mortgageResult);
         log.debug("Age {} [After Retiree Taxes]: {}", age, state);
 
         applyNationalInsuranceOutOfPocket(context, state, age, effectiveAnnualSalary, monthsToAdd);
         log.debug("Age {} [After National Insurance]: {}", age, state);
 
-        applyYearEndAdjustment(context, state, age, initialAge, effectiveAnnualSalary);
-        log.debug("Age {} [After Year End Adj]: {}", age, state);
+        if (isRetirementYear && workingMonths < 12) {
+            // Mid-year retirement: no employer at year end to perform Year-End Adjustment
+        } else {
+            applyYearEndAdjustment(context, state, age, initialAge, effectiveAnnualSalary);
+            log.debug("Age {} [After Year End Adj]: {}", age, state);
+        }
 
         return applyCashflowAndSettlement(context, effectiveAnnualSalary, state, mortgageResult,
                 contributions);
@@ -156,15 +222,15 @@ public class YearlySimulationPipeline {
 
     private void applyWorkingTaxes(SimulationContext context, YearlySimulationState state, int age, int initialAge,
             int monthsToAdd, long annualizedSalaryRate, long effectiveAnnualSalary, MortgageYearlyResult mortgageResult,
-            InvestmentContributions contributions) {
-        if (!context.isRetired() && effectiveAnnualSalary > 0) {
+            InvestmentContributions contributions, boolean isHandlingPreRetirementSegment) {
+        if ((!context.isRetired() || isHandlingPreRetirementSegment) && effectiveAnnualSalary > 0) {
             long kouseiContribution = pensionCalculator.calculateYearlyKouseiBenefitIncrement(
                     context.getPensionConfig(), annualizedSalaryRate, context.getCurrentAnnualBonus(),
                     context.getCurrentBonusPaymentCount());
 
-            // For Year 1, we still want to proportionally accumulate the Kousei pension
-            // future benefit based on months worked
-            if (age == initialAge) {
+            // For Year 1 or pre-retirement segment, proportionally accumulate Kousei
+            // pension benefit
+            if (age == initialAge || isHandlingPreRetirementSegment) {
                 double remainingRatio = monthsToAdd / 12.0;
                 kouseiContribution = Math.round(kouseiContribution * remainingRatio);
             }
@@ -201,16 +267,42 @@ public class YearlySimulationPipeline {
                     contributions.annualIdecoContribution(),
                     contributions.annualDcMatchingContribution(),
                     mortgageResult);
-            state.setEstimatedIncomeTax(taxResult.incomeTax());
-            state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
-            state.setEstimatedSocialInsurance(taxResult.socialInsurance());
-            state.setNetIncome(taxResult.netIncome());
+            state.setEstimatedIncomeTax(state.getEstimatedIncomeTax() + taxResult.incomeTax());
+            state.setEstimatedInhabitantTax(state.getEstimatedInhabitantTax() + taxResult.inhabitantTax());
+            state.setEstimatedSocialInsurance(state.getEstimatedSocialInsurance() + taxResult.socialInsurance());
+            state.setNetIncome(state.getNetIncome() + taxResult.netIncome());
+
+            // Initial transition year mid-year retirement: calculate working segment YTD
+            // difference and update PreviousYearTaxRecord
+            if (age == initialAge && isHandlingPreRetirementSegment && monthsToAdd < 12) {
+                long ytdSocialInsurance = context.getYtdHealthInsurance() + context.getYtdPension()
+                        + context.getYtdEmploymentInsurance();
+                long totalAdjustment = (context.getYtdIncomeTax() - taxResult.incomeTax())
+                        + (context.getYtdResidentTax() - taxResult.inhabitantTax())
+                        + (ytdSocialInsurance - taxResult.socialInsurance());
+
+                PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
+                if (prevTax != null) {
+                    context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                            prevTax.grossSalary(),
+                            prevTax.taxableIncomeForIncomeTax(),
+                            prevTax.baseIncomeTax(),
+                            prevTax.pendingKakuteiShinkokuRefund(),
+                            prevTax.unusedMortgageDeductionForInhabitantTax(),
+                            totalAdjustment));
+                }
+            }
         }
     }
 
-    private void applyRetireeTaxes(SimulationContext context, YearlySimulationState state, int age,
-            int initialAge, int monthsToAdd, int pensionStartAge) {
+    private void applyRetireeTaxes(SimulationContext context, YearlySimulationState state, int age, int initialAge,
+            int monthsToAdd, int pensionStartAge, boolean isHandlingPostRetirementSegment, int pensionMonths,
+            MortgageYearlyResult mortgageResult) {
         if (context.isRetired()) {
+            if (isHandlingPostRetirementSegment && pensionMonths == 0) {
+                return; // DECEMBER mode: worked 12 months in-job, 0 months pension
+            }
+
             long basicPension = 0;
             long kouseiPension = 0;
             int validPensionStartAge = pensionCalculator.getValidPensionStartAge(pensionStartAge);
@@ -227,22 +319,33 @@ public class YearlySimulationPipeline {
             }
             long pensionIncome = basicPension + kouseiPension;
 
-            var taxResult = taxEngine.calculatePensionTax(context, pensionIncome, age);
+            var taxResult = taxEngine.calculatePensionTax(context, pensionIncome, age, mortgageResult);
 
-            if (age == initialAge && monthsToAdd < 12) {
-                double remainingRatio = (double) monthsToAdd / 12.0;
-                state.setPensionIncome(Math.round(pensionIncome * remainingRatio));
-                state.setEstimatedIncomeTax(Math.round(taxResult.incomeTax() * remainingRatio));
-                state.setEstimatedInhabitantTax(Math.round(taxResult.inhabitantTax() * remainingRatio));
-                state.setEstimatedSocialInsurance(Math.round(taxResult.socialInsurance() * remainingRatio));
-                state.setNetIncome(Math.round(taxResult.netIncome() * remainingRatio));
-            } else {
-                state.setPensionIncome(pensionIncome);
-                state.setEstimatedIncomeTax(taxResult.incomeTax());
-                state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
-                state.setEstimatedSocialInsurance(taxResult.socialInsurance());
-                state.setNetIncome(taxResult.netIncome());
+            double remainingRatio = 1.0;
+            boolean shouldScale = false;
+            if (isHandlingPostRetirementSegment && pensionMonths < 12) {
+                remainingRatio = (double) pensionMonths / 12.0;
+                shouldScale = true;
+            } else if (age == initialAge && monthsToAdd < 12) {
+                remainingRatio = (double) monthsToAdd / 12.0;
+                shouldScale = true;
             }
+
+            long estPensionIncome = shouldScale ? Math.round(pensionIncome * remainingRatio) : pensionIncome;
+            long estIncomeTax = shouldScale ? Math.round(taxResult.incomeTax() * remainingRatio)
+                    : taxResult.incomeTax();
+            long estInhabitantTax = shouldScale ? Math.round(taxResult.inhabitantTax() * remainingRatio)
+                    : taxResult.inhabitantTax();
+            long estSocialInsurance = shouldScale ? Math.round(taxResult.socialInsurance() * remainingRatio)
+                    : taxResult.socialInsurance();
+            long estNetIncome = shouldScale ? Math.round(taxResult.netIncome() * remainingRatio)
+                    : taxResult.netIncome();
+
+            state.setPensionIncome(estPensionIncome);
+            state.setEstimatedIncomeTax(state.getEstimatedIncomeTax() + estIncomeTax);
+            state.setEstimatedInhabitantTax(state.getEstimatedInhabitantTax() + estInhabitantTax);
+            state.setEstimatedSocialInsurance(state.getEstimatedSocialInsurance() + estSocialInsurance);
+            state.setNetIncome(state.getNetIncome() + estNetIncome);
         }
     }
 
@@ -316,7 +419,7 @@ public class YearlySimulationPipeline {
         long fullYearNetIncome = state.getNetIncome();
         long ytdNetIncome = 0;
 
-        if (context.getYear() == context.getCurrentYear() && !context.isRetired()) {
+        if (context.getYear() == context.getCurrentYear() && (!context.isRetired() || effectiveAnnualSalary > 0)) {
             ytdNetIncome = computeYtdNetIncomeBaseline(context);
         }
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index 1a99ef34..65317ed3 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -185,11 +185,11 @@ public class ActiveTaxCalculationEngine {
 
         long lifeDeductionIncomeTax = Math.min(120_000L,
                 calculateLifeInsuranceDeductionIncomeTax(generalLifePremium)
-                + calculateLifeInsuranceDeductionIncomeTax(nursingCarePremium));
+                        + calculateLifeInsuranceDeductionIncomeTax(nursingCarePremium));
 
         long lifeDeductionInhabitantTax = Math.min(70_000L,
                 calculateLifeInsuranceDeductionInhabitantTax(generalLifePremium)
-                + calculateLifeInsuranceDeductionInhabitantTax(nursingCarePremium));
+                        + calculateLifeInsuranceDeductionInhabitantTax(nursingCarePremium));
 
         long eqDeductionIncomeTax = Math.min(50_000L, earthquakePremium);
         long eqDeductionInhabitantTax = Math.min(25_000L, earthquakePremium / 2);
@@ -215,9 +215,12 @@ public class ActiveTaxCalculationEngine {
                 .calculateIncomeTax(taxableIncomeForIncomeTax);
 
         // --- Apply Mortgage Tax Deduction (directly against income tax) ---
-        // Under Article 41, Paragraph 1 of the Act on Special Measures Concerning Taxation,
-        // mortgage tax deduction is strictly disqualified (reduced to 0) if the taxpayer's
-        // total employment income exceeds the statutory threshold for the occupancy year
+        // Under Article 41, Paragraph 1 of the Act on Special Measures Concerning
+        // Taxation,
+        // mortgage tax deduction is strictly disqualified (reduced to 0) if the
+        // taxpayer's
+        // total employment income exceeds the statutory threshold for the occupancy
+        // year
         // (e.g., 20,000,000 JPY for 2022 onwards, 30,000,000 JPY prior to 2022).
         if ((yearEndDeduction > 0 || kakuteiDeduction > 0)
                 && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
@@ -259,7 +262,8 @@ public class ActiveTaxCalculationEngine {
                 taxableIncomeForIncomeTax,
                 baseIncomeTax,
                 actualKakuteiRefund,
-                unusedForInhabitantTax));
+                unusedForInhabitantTax,
+                0L));
 
         // 7. Net Income
         long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;
@@ -335,7 +339,11 @@ public class ActiveTaxCalculationEngine {
                 .build();
     }
 
-    public TaxCalculationResult calculatePensionTax(SimulationContext context, long pensionIncome, int age) {
+    public TaxCalculationResult calculatePensionTax(
+            SimulationContext context,
+            long pensionIncome,
+            int age,
+            MortgageYearlyResult mortgageResult) {
         long publicPensionDeduction;
         if (pensionIncome <= 4_100_000) {
             publicPensionDeduction = (long) (pensionIncome * 0.25) + 275_000;
@@ -348,7 +356,8 @@ public class ActiveTaxCalculationEngine {
         long minDeduction = (age >= 65) ? 1_100_000L : 600_000L;
         publicPensionDeduction = Math.max(publicPensionDeduction, minDeduction);
 
-        // Statutory miscellaneous income from public pensions (公的年金等に係る雑所得) under Income Tax Act Article 35
+        // Statutory miscellaneous income from public pensions (公的年金等に係る雑所得) under
+        // Income Tax Act Article 35
         // (所得税法第35条), serving as total income (合計所得金額) before deductions for retirees.
         long miscellaneousIncome = Math.max(0, pensionIncome - publicPensionDeduction);
 
@@ -380,6 +389,46 @@ public class ActiveTaxCalculationEngine {
 
         long netIncome = pensionIncome - socialInsurance - incomeTax - inhabitantTax;
 
+        if (mortgageResult != null) {
+            long kakuteiDeduction = mortgageResult.kakuteiDeduction();
+
+            // Statutory income ceiling check
+            if (kakuteiDeduction > 0
+                    && miscellaneousIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
+                kakuteiDeduction = 0L;
+            }
+
+            PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
+            long usedByWorking = (prevTax != null) ? prevTax.pendingKakuteiShinkokuRefund() : 0L;
+            long remainingKakuteiDeduction = Math.max(0, kakuteiDeduction - usedByWorking);
+
+            if (kakuteiDeduction > 0 || usedByWorking > 0) {
+                long usedByPension = Math.min(baseIncomeTax, remainingKakuteiDeduction);
+                long totalRefund = usedByWorking + usedByPension;
+
+                long remainingDeduction = remainingKakuteiDeduction - usedByPension;
+                long unusedForInhabitantTax = 0L;
+                if (remainingDeduction > 0) {
+                    long maxInhabitantTaxDeduction = mortgageResult.inhabitantTaxLimitStrategy()
+                            .calculateLimit(taxableIncomeForIncomeTax);
+                    unusedForInhabitantTax = Math.min(remainingDeduction, maxInhabitantTaxDeduction);
+                }
+
+                long grossSalary = (prevTax != null) ? prevTax.grossSalary() : 0L;
+                long workingTaxable = (prevTax != null) ? prevTax.taxableIncomeForIncomeTax() : 0L;
+                long workingBaseTax = (prevTax != null) ? prevTax.baseIncomeTax() : 0L;
+                long pendingAdjustment = (prevTax != null) ? prevTax.pendingWorkingSegmentTaxAdjustment() : 0L;
+
+                context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                        grossSalary,
+                        workingTaxable + taxableIncomeForIncomeTax,
+                        workingBaseTax + baseIncomeTax,
+                        totalRefund,
+                        unusedForInhabitantTax,
+                        pendingAdjustment));
+            }
+        }
+
         return TaxCalculationResult.builder()
                 .incomeTax(incomeTax)
                 .inhabitantTax(inhabitantTax)
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
index 50da7437..5dc1d04f 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
@@ -49,6 +49,7 @@ import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFi
 import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
 import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.platform.exception.ConfigNotFoundException;
 import ch.lin.platform.exception.InvalidRequestException;
 
@@ -233,14 +234,17 @@ class FinanceHubConfigServiceImplTest {
     void testGetResolvedConfig() {
         FinanceHubConfig config = new FinanceHubConfig("test");
         config.setFileProcessingTimeoutSeconds(null);
+        config.setRetirementMonthMode(null);
         when(repository.findByName("test")).thenReturn(Optional.of(config));
         when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
         defaultSysConfig.setFileProcessingTimeoutSeconds(30);
+        defaultSysConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);
 
         FinanceHubConfig result = service.getResolvedConfig("test");
 
         assertEquals("test", result.getName());
         assertEquals(30, result.getFileProcessingTimeoutSeconds());
+        assertEquals(RetirementMonthMode.DECEMBER, result.getRetirementMonthMode());
     }
 
     @Test
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index 27f13e6b..f080e9fd 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -322,7 +322,7 @@ class AssetSimulationEngineImplTest {
 
         lenient()
                 .when(taxEngine.calculatePensionTax(any(), org.mockito.ArgumentMatchers.anyLong(),
-                        org.mockito.ArgumentMatchers.anyInt()))
+                        org.mockito.ArgumentMatchers.anyInt(), any()))
                 .thenAnswer(invocation -> {
                     long income = invocation.getArgument(1);
                     return new TaxCalculationResult(0L, 0L, 0L, income);
@@ -540,15 +540,19 @@ class AssetSimulationEngineImplTest {
         }
 
         // 5. Expected Cash Math for Year 1 (Retirement Year, Age 60)
-        // Income = 0 (Retired)
+        // Under DECEMBER mode (Question 191), the user works all 12 months in the
+        // retirement year
+        long netIncomeYear1 = trueAnnualNetIncome;
         // Expenses = Baseline (12 months) + Retiree Health Insurance
         // (TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM = 400_000L)
         long expectedAnnualExpensesYear1 = (400_000L * 12) + 400_000L;
 
-        // Year 1 cash decreases by regular expenses, 12 months of regular mortgage
-        // payments,
-        // AND the early repayment of the remaining balance at the END of Year 1.
-        long expectedYear1Cash = expectedYear0Cash - expectedAnnualExpensesYear1 - totalMortgagePaidInYear1
+        // Year 1 cash increases by net income, decreases by regular expenses, 12 months
+        // of regular mortgage
+        // payments, AND the early repayment of the remaining balance at the END of Year
+        // 1.
+        long expectedYear1Cash = expectedYear0Cash + netIncomeYear1 - expectedAnnualExpensesYear1
+                - totalMortgagePaidInYear1
                 - remainingPrincipalAtEndOfYear1;
         int retirementCalendarYear = YearMonth.now().getYear() + 1;
         Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> mortgageOverrides = new HashMap<>();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
index 80c7898b..25dff1fd 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
@@ -12,6 +12,7 @@ import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Pre2022TaxStrategy;
+import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.SpecialSpecificAcquisitionTaxStrategy;
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2022To2023TaxStrategy;
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2024To2025TaxStrategy;
@@ -33,6 +34,7 @@ class MortgageCalculatorTest {
     void setUp() {
         TaxStrategyFactory taxStrategyFactory = new TaxStrategyFactory(List.of(
                 new Pre2022TaxStrategy(),
+                new SpecialSpecificAcquisitionTaxStrategy(),
                 new Years2022To2023TaxStrategy(),
                 new Years2024To2025TaxStrategy(),
                 new Years2026OnwardsTaxStrategy()));
@@ -70,11 +72,11 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        long[] balances = new long[]{10000000L};
-        long[] payments = new long[]{30000L};
-        double[] rates = new double[]{1.0};
-        long[] expected = new long[]{30000L};
-        int[] months = new int[]{0};
+        long[] balances = new long[] { 10000000L };
+        long[] payments = new long[] { 30000L };
+        double[] rates = new double[] { 1.0 };
+        long[] expected = new long[] { 30000L };
+        int[] months = new int[] { 0 };
 
         context.setMortgageBalances(balances);
         context.setMortgageMonthlyPayments(payments);
@@ -89,7 +91,8 @@ class MortgageCalculatorTest {
         MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);
 
         // Assert
-        // In the first year (2026 == occupancy year), the deduction goes to kakuteiDeduction channel
+        // In the first year (2026 == occupancy year), the deduction goes to
+        // kakuteiDeduction channel
         assertEquals(0L, result.yearEndDeduction());
         assertTrue(result.kakuteiDeduction() > 0);
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
@@ -126,11 +129,11 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        long[] balances = new long[]{10000000L};
-        long[] payments = new long[]{30000L};
-        double[] rates = new double[]{1.0};
-        long[] expected = new long[]{30000L};
-        int[] months = new int[]{12}; // 12 months passed
+        long[] balances = new long[] { 10000000L };
+        long[] payments = new long[] { 30000L };
+        double[] rates = new double[] { 1.0 };
+        long[] expected = new long[] { 30000L };
+        int[] months = new int[] { 12 }; // 12 months passed
 
         context.setMortgageBalances(balances);
         context.setMortgageMonthlyPayments(payments);
@@ -145,7 +148,8 @@ class MortgageCalculatorTest {
         MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);
 
         // Assert
-        // In the second year (2027 > 2026), the deduction for THIS year should be in yearEndDeduction channel
+        // In the second year (2027 > 2026), the deduction for THIS year should be in
+        // yearEndDeduction channel
         assertTrue(result.yearEndDeduction() > 0);
         assertEquals(0L, result.kakuteiDeduction());
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
@@ -181,18 +185,20 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        context2027.setMortgageBalances(new long[]{0L});
-        context2027.setMortgageMonthlyPayments(new long[]{30000L});
-        context2027.setMortgageCurrentRates(new double[]{0.01});
-        context2027.setMortgageCurrentExpectedPayments(new long[]{30000L});
-        context2027.setMortgageMonthsSinceStart(new int[]{0});
+        context2027.setMortgageBalances(new long[] { 0L });
+        context2027.setMortgageMonthlyPayments(new long[] { 30000L });
+        context2027.setMortgageCurrentRates(new double[] { 0.01 });
+        context2027.setMortgageCurrentExpectedPayments(new long[] { 30000L });
+        context2027.setMortgageMonthsSinceStart(new int[] { 0 });
 
         YearMonth nowMonth = YearMonth.of(2027, 1);
 
-        // Act 1: Simulate Year 2027 (loan starts in Nov 2027, but not yet occupied in 2027)
+        // Act 1: Simulate Year 2027 (loan starts in Nov 2027, but not yet occupied in
+        // 2027)
         MortgageYearlyResult result2027 = calculator.simulateMortgagesForYear(context2027, List.of(m), nowMonth, null);
 
-        // Assert 1: In 2027 (prior to occupancy year 2028), tax deductions MUST strictly be 0
+        // Assert 1: In 2027 (prior to occupancy year 2028), tax deductions MUST
+        // strictly be 0
         assertEquals(0L, result2027.yearEndDeduction(), "No year-end tax deduction before occupancy year");
         assertEquals(0L, result2027.kakuteiDeduction(), "No kakutei tax deduction before occupancy year");
 
@@ -207,7 +213,8 @@ class MortgageCalculatorTest {
 
         MortgageYearlyResult result2028 = calculator.simulateMortgagesForYear(context2028, List.of(m), nowMonth, null);
 
-        // Assert 2: In 2028 (occupancy year), kakuteiDeduction is awarded, yearEndDeduction is 0
+        // Assert 2: In 2028 (occupancy year), kakuteiDeduction is awarded,
+        // yearEndDeduction is 0
         assertTrue(result2028.kakuteiDeduction() > 0, "Kakutei deduction applies in occupancy year");
         assertEquals(0L, result2028.yearEndDeduction(), "No year-end deduction in first occupancy year");
     }
@@ -244,15 +251,15 @@ class MortgageCalculatorTest {
         org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
 
         long initialPayment = 33000L;
-        context.setMortgageBalances(new long[]{10000000L});
-        context.setMortgageMonthlyPayments(new long[]{initialPayment});
-        context.setMortgageCurrentRates(new double[]{0.012});
-        context.setMortgageCurrentExpectedPayments(new long[]{initialPayment});
-        context.setMortgageMonthsSinceStart(new int[]{12});
+        context.setMortgageBalances(new long[] { 10000000L });
+        context.setMortgageMonthlyPayments(new long[] { initialPayment });
+        context.setMortgageCurrentRates(new double[] { 0.012 });
+        context.setMortgageCurrentExpectedPayments(new long[] { initialPayment });
+        context.setMortgageMonthsSinceStart(new int[] { 12 });
 
         // Override in month 12 of 2026 with REDUCE_PAYMENT for 3,000,000
-        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride
-                = new SimulationOverrides.MortgageEarlyRepaymentOverride(2026, 3000000L, EarlyRepaymentType.REDUCE_PAYMENT);
+        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride = new SimulationOverrides.MortgageEarlyRepaymentOverride(
+                2026, 3000000L, EarlyRepaymentType.REDUCE_PAYMENT);
         SimulationOverrides overrides = new SimulationOverrides(
                 null, null, null, null, null,
                 Map.of(1L, List.of(erOverride)),
@@ -264,7 +271,8 @@ class MortgageCalculatorTest {
         calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);
 
         // Assert:
-        // Monthly payment should be recalculated down from 33,000 (around 22,000~24,000)
+        // Monthly payment should be recalculated down from 33,000 (around
+        // 22,000~24,000)
         long newPayment = context.getMortgageMonthlyPayments()[0];
         assertTrue(newPayment < 26000L, "Monthly payment should be reduced significantly, was: " + newPayment);
         assertTrue(newPayment > 15000L, "Monthly payment should be reasonable, was: " + newPayment);
@@ -305,15 +313,15 @@ class MortgageCalculatorTest {
         org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
 
         long initialPayment = 33000L;
-        context.setMortgageBalances(new long[]{10000000L});
-        context.setMortgageMonthlyPayments(new long[]{initialPayment});
-        context.setMortgageCurrentRates(new double[]{0.012});
-        context.setMortgageCurrentExpectedPayments(new long[]{initialPayment});
-        context.setMortgageMonthsSinceStart(new int[]{12});
+        context.setMortgageBalances(new long[] { 10000000L });
+        context.setMortgageMonthlyPayments(new long[] { initialPayment });
+        context.setMortgageCurrentRates(new double[] { 0.012 });
+        context.setMortgageCurrentExpectedPayments(new long[] { initialPayment });
+        context.setMortgageMonthsSinceStart(new int[] { 12 });
 
         // Override in month 12 of 2026 with REDUCE_TERM for 3,000,000
-        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride
-                = new SimulationOverrides.MortgageEarlyRepaymentOverride(2026, 3000000L, EarlyRepaymentType.REDUCE_TERM);
+        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride = new SimulationOverrides.MortgageEarlyRepaymentOverride(
+                2026, 3000000L, EarlyRepaymentType.REDUCE_TERM);
         SimulationOverrides overrides = new SimulationOverrides(
                 null, null, null, null, null,
                 Map.of(1L, List.of(erOverride)),
@@ -336,11 +344,11 @@ class MortgageCalculatorTest {
     void testSimulateMortgagesForYear_Pre2022Occupancy_Returns30MIncomeLimit() {
         SimulationContext context = new SimulationContext();
         context.setYear(2021);
-        context.setMortgageBalances(new long[]{10_000_000L});
-        context.setMortgageMonthlyPayments(new long[]{30_000L});
-        context.setMortgageCurrentRates(new double[]{1.0});
-        context.setMortgageCurrentExpectedPayments(new long[]{30_000L});
-        context.setMortgageMonthsSinceStart(new int[]{12});
+        context.setMortgageBalances(new long[] { 10_000_000L });
+        context.setMortgageMonthlyPayments(new long[] { 30_000L });
+        context.setMortgageCurrentRates(new double[] { 1.0 });
+        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
+        context.setMortgageMonthsSinceStart(new int[] { 12 });
 
         MortgageRecord m = new MortgageRecord(
                 "ソニー銀行",
@@ -366,7 +374,8 @@ class MortgageCalculatorTest {
                 400000L,
                 true);
 
-        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2021, 1), null);
+        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2021, 1),
+                null);
 
         assertEquals(30_000_000L, result.maxTotalIncomeForMortgageDeduction());
     }
@@ -375,11 +384,11 @@ class MortgageCalculatorTest {
     void testSimulateMortgagesForYear_Post2022Occupancy_Returns20MIncomeLimit() {
         SimulationContext context = new SimulationContext();
         context.setYear(2026);
-        context.setMortgageBalances(new long[]{10_000_000L});
-        context.setMortgageMonthlyPayments(new long[]{30_000L});
-        context.setMortgageCurrentRates(new double[]{1.0});
-        context.setMortgageCurrentExpectedPayments(new long[]{30_000L});
-        context.setMortgageMonthsSinceStart(new int[]{12});
+        context.setMortgageBalances(new long[] { 10_000_000L });
+        context.setMortgageMonthlyPayments(new long[] { 30_000L });
+        context.setMortgageCurrentRates(new double[] { 1.0 });
+        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
+        context.setMortgageMonthsSinceStart(new int[] { 12 });
 
         MortgageRecord m = new MortgageRecord(
                 "ソニー銀行",
@@ -405,7 +414,8 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1), null);
+        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1),
+                null);
 
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
     }
@@ -414,11 +424,11 @@ class MortgageCalculatorTest {
     void testSimulateMortgagesForYear_NotEligible_ReturnsZeroIncomeLimit() {
         SimulationContext context = new SimulationContext();
         context.setYear(2026);
-        context.setMortgageBalances(new long[]{10_000_000L});
-        context.setMortgageMonthlyPayments(new long[]{30_000L});
-        context.setMortgageCurrentRates(new double[]{1.0});
-        context.setMortgageCurrentExpectedPayments(new long[]{30_000L});
-        context.setMortgageMonthsSinceStart(new int[]{12});
+        context.setMortgageBalances(new long[] { 10_000_000L });
+        context.setMortgageMonthlyPayments(new long[] { 30_000L });
+        context.setMortgageCurrentRates(new double[] { 1.0 });
+        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
+        context.setMortgageMonthsSinceStart(new int[] { 12 });
 
         MortgageRecord m = new MortgageRecord(
                 "ソニー銀行",
@@ -444,8 +454,53 @@ class MortgageCalculatorTest {
                 null,
                 true);
 
-        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1), null);
+        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1),
+                null);
 
         assertEquals(0L, result.maxTotalIncomeForMortgageDeduction());
     }
+
+    @Test
+    void testSimulateMortgagesForYear_SecondEligibleYear_Retired_RoutesToKakuteiDeduction() {
+        SimulationContext context = new SimulationContext();
+        context.setYear(2027); // Year 2 after 2026 occupancy
+        context.setRetired(true); // Retired
+        context.setMortgageBalances(new long[] { 10_000_000L });
+        context.setMortgageMonthlyPayments(new long[] { 30_000L });
+        context.setMortgageCurrentRates(new double[] { 1.0 });
+        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
+        context.setMortgageMonthsSinceStart(new int[] { 24 });
+
+        MortgageRecord m = new MortgageRecord(
+                "ソニー銀行",
+                "1",
+                "New Loan",
+                10000000L,
+                10000000L,
+                LocalDate.of(2026, 1, 1),
+                LocalDate.of(2056, 1, 1),
+                LocalDate.of(2026, 1, 1),
+                360,
+                1.0,
+                InterestRateType.FIXED,
+                RepaymentMethod.EQUAL_PAYMENT,
+                30000L,
+                Collections.emptyList(),
+                LocalDate.of(2026, 2, 1), // Occupancy in 2026
+                null,
+                null,
+                true,
+                2039,
+                0.007,
+                273000L,
+                true);
+
+        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2027, 1),
+                null);
+
+        // When retired, Year 2+ mortgage deduction must route to kakuteiDeduction (0
+        // for yearEndDeduction)
+        assertTrue(result.kakuteiDeduction() > 0L);
+        assertEquals(0L, result.yearEndDeduction());
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolverTest.java
new file mode 100644
index 00000000..17afcc05
--- /dev/null
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolverTest.java
@@ -0,0 +1,65 @@
+/*
+ * The MIT License (MIT)
+ *
+ * Copyright (c) 2026 Che-Hung Lin
+ *
+ * Permission is hereby granted, free of charge, to any person obtaining a copy
+ * of this software and associated documentation files (the "Software"), to deal
+ * in the Software without restriction, including without limitation the rights
+ * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
+ * copies of the Software, and to permit persons to whom the Software is
+ * furnished to do so, subject to the following conditions:
+ *
+ * The above copyright notice and this permission notice shall be included in
+ * all copies or substantial portions of the Software.
+ *
+ * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
+ * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
+ * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
+ * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
+ * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
+ * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
+ * THE SOFTWARE.
+ */
+package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;
+
+import java.time.LocalDate;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertNull;
+import org.junit.jupiter.api.Test;
+
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
+
+class RetirementMonthResolverTest {
+
+    @Test
+    void testResolveRetirementMonth_DecemberMode_ReturnsTwelveRegardlessOfBirthDate() {
+        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 5, 20),
+                RetirementMonthMode.DECEMBER));
+        assertEquals(12,
+                RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 1, 1), RetirementMonthMode.DECEMBER));
+        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(null, RetirementMonthMode.DECEMBER));
+    }
+
+    @Test
+    void testResolveRetirementMonth_BirthMonthMode_ReturnsBirthMonthValue() {
+        assertEquals(1, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 1, 15),
+                RetirementMonthMode.BIRTH_MONTH));
+        assertEquals(6, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 6, 30),
+                RetirementMonthMode.BIRTH_MONTH));
+        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 12, 5),
+                RetirementMonthMode.BIRTH_MONTH));
+    }
+
+    @Test
+    void testResolveRetirementMonth_BirthMonthMode_NullBirthDate_ReturnsNull() {
+        assertNull(RetirementMonthResolver.resolveRetirementMonth(null, RetirementMonthMode.BIRTH_MONTH));
+    }
+
+    @Test
+    void testResolveRetirementMonth_NullMode_DefaultsToDecember() {
+        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 7, 10), null));
+        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(null, null));
+    }
+}
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
index fc18a55b..c6b590c0 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -27,10 +27,12 @@ import static org.assertj.core.api.Assertions.assertThat;
 import static org.mockito.ArgumentMatchers.any;
 import static org.mockito.ArgumentMatchers.anyInt;
 import static org.mockito.ArgumentMatchers.anyLong;
+import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.Mockito.lenient;
 import static org.mockito.Mockito.when;
 
 import java.math.BigDecimal;
+import java.time.LocalDate;
 import java.time.YearMonth;
 import java.util.Collections;
 
@@ -62,6 +64,8 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.DependentD
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.SpouseDeductionEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
+import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 
 @ExtendWith(MockitoExtension.class)
@@ -299,7 +303,7 @@ class YearlySimulationPipelineTest {
         // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY
         // inhabitant rollover
         context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                salary, 2_000_000L, 50_000L, 50_000L, 97_500L));
+                salary, 2_000_000L, 50_000L, 50_000L, 97_500L, 0L));
 
         when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
@@ -417,4 +421,328 @@ class YearlySimulationPipelineTest {
         long expectedCash = initialCash - expectedAnnualExpenses;
         assertThat(result.getProjectedLiquidCash()).isEqualTo(expectedCash);
     }
+
+    /**
+     * Question 191-03 Test 1:
+     * Future retirement year split-year (age > initialAge):
+     * Working segment (Jan-Jun) and retiree segment (Jul-Dec) are each calculated
+     * and summed into state.
+     * No YTD adjustment packaged into PreviousYearTaxRecord.
+     */
+    @Test
+    void testFutureRetirementYear_splitYear_sumsWorkingAndPensionTaxes() {
+        int initialAge = 60;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        int retirementAge = 65;
+        long salary = 6_000_000L;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, salary, initialCash);
+        context.setCurrentKokuminMonths(480);
+        context.setCurrentKouseiMonths(480);
+        context.setAccumulatedKousei(1_000_000L);
+        YearMonth nowMonth = YearMonth.of(2026, 1);
+
+        FinanceHubConfig config = new FinanceHubConfig("test");
+        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
+        context.setConfig(config);
+        context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month 6
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(6))).thenReturn(3_000_000L);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, retirementAge);
+
+        assertThat(result.getYear()).isEqualTo(2031);
+        assertThat(result.getAge()).isEqualTo(65);
+        assertThat(result.isRetired()).isTrue();
+        assertThat(result.getProjectedAnnualSalary()).isEqualTo(3_000_000L);
+        assertThat(result.getEstimatedIncomeTax()).isGreaterThan(0L);
+        assertThat(result.getNetIncome()).isGreaterThan(0L);
+
+        // Future retirement year: no YTD difference packaging
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        if (record != null) {
+            assertThat(record.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
+        }
+    }
+
+    /**
+     * Question 191-03 Test 2:
+     * Initial year retirement split-year (age == initialAge):
+     * Calculates working segment tax, compares with YTD history, and packages the
+     * difference
+     * into PreviousYearTaxRecord.pendingWorkingSegmentTaxAdjustment without calling
+     * applyYearEndAdjustment.
+     */
+    @Test
+    void testInitialYearRetirement_splitYear_packagesWorkingSegmentYtdAdjustment() {
+        int initialAge = 65;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        long salary = 6_000_000L;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, salary, initialCash);
+        context.setCurrentKokuminMonths(480);
+        context.setCurrentKouseiMonths(480);
+        context.setAccumulatedKousei(1_000_000L);
+        YearMonth nowMonth = YearMonth.of(2026, 3); // Month 3
+
+        FinanceHubConfig config = new FinanceHubConfig("test");
+        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
+        context.setConfig(config);
+        context.getProfile().setBirthDate(LocalDate.of(1961, 6, 15)); // Month 6
+
+        // Working segment salary for remaining months (e.g. 3,000,000)
+        context.setYtdBaseSalary(1_500_000L);
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), anyInt())).thenReturn(3_000_000L);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        // YTD deductions recorded up to March (gross: 1,500,000)
+        context.setYtdGrossIncome(1_500_000L);
+        context.setYtdIncomeTax(100_000L);
+        context.setYtdResidentTax(80_000L);
+        context.setYtdHealthInsurance(75_000L);
+        context.setYtdPension(135_000L);
+        context.setYtdEmploymentInsurance(9_000L);
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, initialAge);
+
+        assertThat(result.getYear()).isEqualTo(2026);
+        assertThat(result.isRetired()).isTrue();
+        assertThat(result.getProjectedAnnualSalary()).isEqualTo(3_000_000L);
+
+        // Verify YTD difference packaged into PreviousYearTaxRecord
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        assertThat(record).isNotNull();
+        assertThat(record.pendingWorkingSegmentTaxAdjustment()).isNotEqualTo(0L);
+    }
+
+    /**
+     * Question 191-03 Test 3:
+     * Next-year simulation:
+     * Pending working segment tax adjustment is disbursed into liquid cash (+ for
+     * refund)
+     * and cleared from PreviousYearTaxRecord to prevent double redemption.
+     */
+    @Test
+    void testNextYearSimulation_disbursesWorkingSegmentTaxAdjustmentAndClearsRecord() {
+        int initialAge = 65;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        long initialCash = 5_000_000L;
+        long pendingAdj = 120_000L; // Refund due to excess YTD withholding
+        SimulationContext context = createContext(2027, 66, 0L, initialCash);
+
+        // Pre-package PreviousYearTaxRecord with pendingWorkingSegmentTaxAdjustment
+        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                3_000_000L, 1_500_000L, 50_000L, 0L, 0L, pendingAdj));
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(0L);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2027, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 66);
+
+        // 1. Adjustment disbursed into liquid cash
+        long cashDelta = result.getProjectedLiquidCash() - initialCash;
+        assertThat(cashDelta).isEqualTo(result.getNetSavings() + pendingAdj);
+
+        // 2. Pending adjustment zeroed out to prevent duplicate payout in following
+        // year
+        PreviousYearTaxRecord updatedRecord = context.getPreviousYearTaxRecord();
+        assertThat(updatedRecord).isNotNull();
+        assertThat(updatedRecord.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
+    }
+
+    /**
+     * Question 191-03 Test 4:
+     * Retirement year with DECEMBER mode:
+     * Works through December 31 (12 months in-job, 0 months pension).
+     */
+    @Test
+    void testRetirementYear_decemberMode_fullYearWorkingNoPension() {
+        int initialAge = 60;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        long salary = 6_000_000L;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, salary, initialCash);
+
+        FinanceHubConfig config = new FinanceHubConfig("test");
+        config.setRetirementMonthMode(RetirementMonthMode.DECEMBER);
+        context.setConfig(config);
+        context.getProfile().setBirthDate(LocalDate.of(1966, 4, 10)); // Birthday in April, but DECEMBER mode
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(12))).thenReturn(salary);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);
+
+        assertThat(result.getYear()).isEqualTo(2031);
+        assertThat(result.getProjectedAnnualSalary()).isEqualTo(salary);
+        // Working taxes applied
+        assertThat(result.getEstimatedIncomeTax()).isGreaterThan(0L);
+
+        // No YTD difference packaged
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        if (record != null) {
+            assertThat(record.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
+        }
+    }
+
+    /**
+     * Question 191-03 Test 5:
+     * Missing birthDate under BIRTH_MONTH mode:
+     * Fallback to full-year retired (0 working months, 12 pension months) without
+     * NPE.
+     */
+    @Test
+    void testRetirementYear_missingBirthDateUnderBirthMonthMode_fallbackToFullYearRetired() {
+        int initialAge = 60;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, 6_000_000L, initialCash);
+        context.setCurrentKokuminMonths(480);
+        context.setCurrentKouseiMonths(480);
+        context.setAccumulatedKousei(1_000_000L);
+
+        FinanceHubConfig config = new FinanceHubConfig("test");
+        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
+        context.setConfig(config);
+        context.getProfile().setBirthDate(null); // Missing birthDate
+
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);
+
+        assertThat(result.getYear()).isEqualTo(2031);
+        assertThat(result.getProjectedAnnualSalary()).isEqualTo(0L);
+        assertThat(result.isRetired()).isTrue();
+        // Pension calculated for 12 months
+        assertThat(result.getNetIncome()).isGreaterThan(0L);
+    }
+
+    /**
+     * Question 191-04 Test 1:
+     * Post-retirement full retiree year with mortgage deduction:
+     * Year 1 (Age 66):
+     * - Retired (12 months pension).
+     * - Mortgage deduction routed to kakuteiDeduction (100,000 JPY).
+     * - calculatePensionTax caps mortgage refund against pension baseIncomeTax.
+     * - Pending refund and unused deduction packaged into PreviousYearTaxRecord.
+     * Year 2 (Age 67):
+     * - Spring cash refund strictly disburses pendingKakuteiShinkokuRefund into
+     * liquid cash.
+     * - pendingKakuteiShinkokuRefund is zeroed out in PreviousYearTaxRecord to
+     * prevent duplicate payout.
+     */
+    @Test
+    void testQuestion191_04_postRetirementYear_mortgageDeductionPackagesKakuteiRefund_andDisbursesNextYear() {
+        int initialAge = 66;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, 0L, initialCash);
+        context.setCurrentKokuminMonths(480);
+        context.setCurrentKouseiMonths(480);
+        context.setAccumulatedKousei(3_000_000L);
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), any())).thenReturn(0L);
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(0L);
+
+        // --- Year 1 (2026, Age 66): Post-retirement year with 100,000 JPY mortgage
+        // deduction ---
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 100_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 66);
+
+        assertThat(resultYear1.getYear()).isEqualTo(2026);
+        assertThat(resultYear1.isRetired()).isTrue();
+        assertThat(resultYear1.getProjectedAnnualSalary()).isEqualTo(0L);
+
+        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear1).isNotNull();
+        assertThat(recordAfterYear1.baseIncomeTax()).isGreaterThan(0L);
+        long expectedRefund = Math.min(recordAfterYear1.baseIncomeTax(), 100_000L);
+        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(expectedRefund);
+
+        long cashAtEndOfYear1 = context.getCurrentLiquidCash();
+
+        // --- Year 2 (2027, Age 67): Next year spring cash payout ---
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2027, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 67);
+
+        assertThat(resultYear2.getYear()).isEqualTo(2027);
+
+        // Spring cash received expectedRefund
+        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
+        assertThat(cashDelta).isEqualTo(resultYear2.getNetSavings() + expectedRefund);
+
+        // Record has refund zeroed to prevent double redemption
+        PreviousYearTaxRecord recordAfterYear2 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear2).isNotNull();
+        assertThat(recordAfterYear2.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
+    }
+
+    /**
+     * Question 191-04 Test 2:
+     * Split retirement year:
+     * Mortgage deduction relays across working segment and pension segment.
+     */
+    @Test
+    void testQuestion191_04_splitRetirementYear_mortgageDeductionRelaysAcrossWorkingAndPensionSegments() {
+        int initialAge = 60;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 65;
+        SimulationContext context = createContext(2026, initialAge, 6_000_000L, 5_000_000L);
+        context.setCurrentKokuminMonths(480);
+        context.setCurrentKouseiMonths(480);
+        context.setAccumulatedKousei(3_000_000L);
+
+        FinanceHubConfig config = new FinanceHubConfig("test");
+        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
+        context.setConfig(config);
+        context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month = 6 (split year: 6 working, 6 pension)
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(6))).thenReturn(3_000_000L);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 250_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);
+
+        assertThat(result.getYear()).isEqualTo(2031);
+        assertThat(result.isRetired()).isTrue();
+
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        assertThat(record).isNotNull();
+        // Combined refund must be capped within kakuteiDeduction
+        assertThat(record.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
+        assertThat(record.pendingKakuteiShinkokuRefund()).isLessThanOrEqualTo(250_000L);
+        assertThat(record.grossSalary()).isEqualTo(3_000_000L);
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index 5a335bbc..ccd96682 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -97,8 +97,10 @@ class ActiveTaxCalculationEngineTest {
     @Test
     void calculate_reiwa8_dynamicBasicDeduction() {
         // Given 8,000,000 JPY gross salary in year 2026 (Reiwa 8)
-        // Employment income = 8,000,000 - 1,900,000 = 6,100,000 JPY (within 4.89M ~ 6.55M range)
-        // Basic deduction for 2026 in this bracket is 670,000 JPY (instead of flat 480,000 JPY)
+        // Employment income = 8,000,000 - 1,900,000 = 6,100,000 JPY (within 4.89M ~
+        // 6.55M range)
+        // Basic deduction for 2026 in this bracket is 670,000 JPY (instead of flat
+        // 480,000 JPY)
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
@@ -117,7 +119,8 @@ class ActiveTaxCalculationEngineTest {
                 0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
-        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 670,000 (basic deduction)
+        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 670,000 (basic
+        // deduction)
         // = 4,268,301 -> round down to 1,000 = 4,268,000 JPY
         // baseIncomeTax = (4,268,000 * 0.2) - 427,500 = 426,100 JPY
         // recon = 426,100 * 1.021 = 435,048 JPY
@@ -151,7 +154,8 @@ class ActiveTaxCalculationEngineTest {
                 0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
-        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 620,000 (basic deduction)
+        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 620,000 (basic
+        // deduction)
         // = 4,318,301 -> round down to 1,000 = 4,318,000 JPY
         // baseIncomeTax = (4,318,000 * 0.2) - 427,500 = 436,100 JPY
         // recon = 436,100 * 1.021 = 445,258 JPY
@@ -172,10 +176,11 @@ class ActiveTaxCalculationEngineTest {
         context.setYear(2026); // Reiwa 8: miscellaneousIncome <= 4.89M gets 1,040,000 JPY deduction
 
         // When
-        TaxCalculationResult result = engine.calculatePensionTax(context, pensionIncome, age);
+        TaxCalculationResult result = engine.calculatePensionTax(context, pensionIncome, age, null);
 
         // Math:
-        // publicPensionDeduction = max((3,000,000 * 0.25 + 275,000 = 1,025,000), 1,100,000) = 1,100,000
+        // publicPensionDeduction = max((3,000,000 * 0.25 + 275,000 = 1,025,000),
+        // 1,100,000) = 1,100,000
         // miscellaneousIncome = 3,000,000 - 1,100,000 = 1,900,000
         // socialInsurance = 3,000,000 * 0.095 = 285,000
         // basicDeduction (2026, 1.9M <= 4.89M) = 1,040,000
@@ -196,10 +201,12 @@ class ActiveTaxCalculationEngineTest {
 
     @Test
     void calculate_highIncome26M_dynamicInhabitantTaxDeduction() {
-        // Given 26,000,000 JPY gross salary (within 24.0M ~ 24.5M employment income bracket)
+        // Given 26,000,000 JPY gross salary (within 24.0M ~ 24.5M employment income
+        // bracket)
         // Employment income = 26,000,000 - 1,950,000 (cap) = 24,050,000 JPY
         // Basic deduction for income tax in 2026: 320,000 JPY
-        // Basic deduction for inhabitant tax: 290,000 JPY (reduced from standard 430,000 JPY)
+        // Basic deduction for inhabitant tax: 290,000 JPY (reduced from standard
+        // 430,000 JPY)
         long grossSalary = 26_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
@@ -226,8 +233,10 @@ class ActiveTaxCalculationEngineTest {
 
         // Inhabitant tax:
         // employmentIncome = 24,050,000
-        // totalDeductionsInhabitant = 1,736,640 + 290,000 (basic deduction) = 2,026,640 JPY
-        // taxableInhabitant = 24,050,000 - 2,026,640 = 22,023,360 -> round down to 1,000 = 22,023,000 JPY
+        // totalDeductionsInhabitant = 1,736,640 + 290,000 (basic deduction) = 2,026,640
+        // JPY
+        // taxableInhabitant = 24,050,000 - 2,026,640 = 22,023,360 -> round down to
+        // 1,000 = 22,023,000 JPY
         // inhabitantTax = 22,023,000 * 0.1 + 5,000 (flat levy) = 2,207,300 JPY
 
         assertThat(result.socialInsurance()).isEqualTo(1_736_640L);
@@ -264,7 +273,8 @@ class ActiveTaxCalculationEngineTest {
         // Inhabitant tax:
         // employmentIncome = 25,050,000
         // totalDeductionsInhabitant = 1,742,640 + 0 (basic deduction) = 1,742,640 JPY
-        // taxableInhabitant = 25,050,000 - 1,742,640 = 23,307,360 -> round down to 1,000 = 23,307,000 JPY
+        // taxableInhabitant = 25,050,000 - 1,742,640 = 23,307,360 -> round down to
+        // 1,000 = 23,307,000 JPY
         // inhabitantTax = 23,307,000 * 0.1 + 5,000 (flat levy) = 2,335,700 JPY
 
         assertThat(result.socialInsurance()).isEqualTo(1_742_640L);
@@ -275,7 +285,8 @@ class ActiveTaxCalculationEngineTest {
     void calculate_reiwa8_mortgageDeduction_atIncomeCapBoundary_eligible() {
         // Gross salary: 21,950,000 JPY
         // Employment income deduction: 1,950,000 JPY cap
-        // Employment income = 21,950,000 - 1,950,000 = 20,000,000 JPY (exactly at the 20M cap)
+        // Employment income = 21,950,000 - 1,950,000 = 20,000,000 JPY (exactly at the
+        // 20M cap)
         // Mortgage deduction: 200,000 JPY with maxTotalIncome = 20,000,000 JPY
         long grossSalary = 21_950_000L;
         long mortgageDeduction = 200_000L;
@@ -299,7 +310,8 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
+                        new ZeroInhabitantTaxLimitStrategy()));
 
         // Assert: 20,000,000 <= 20,000,000 -> eligible, deduction applied
         // Saving = 200,000 * 1.021 (reconstruction tax) = 204,200 JPY
@@ -311,7 +323,8 @@ class ActiveTaxCalculationEngineTest {
     void calculate_reiwa8_mortgageDeduction_exceedsCapBy1Yen_disqualified() {
         // Gross salary: 21,950,001 JPY
         // Employment income deduction: 1,950,000 JPY cap
-        // Employment income = 21,950,001 - 1,950,000 = 20,000,001 JPY (exceeds 20M cap by 1 JPY)
+        // Employment income = 21,950,001 - 1,950,000 = 20,000,001 JPY (exceeds 20M cap
+        // by 1 JPY)
         // Mortgage deduction: 200,000 JPY with maxTotalIncome = 20,000,000 JPY
         long grossSalary = 21_950_001L;
         long mortgageDeduction = 200_000L;
@@ -335,9 +348,11 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
+                        new ZeroInhabitantTaxLimitStrategy()));
 
-        // Assert: 20,000,001 > 20,000,000 -> disqualified, mortgage deduction forced to 0
+        // Assert: 20,000,001 > 20,000,000 -> disqualified, mortgage deduction forced to
+        // 0
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
         assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
     }
@@ -346,7 +361,8 @@ class ActiveTaxCalculationEngineTest {
     void calculate_pre2022_mortgageDeduction_atIncomeCapBoundary_eligible() {
         // Gross salary: 31_950,000 JPY
         // Employment income deduction: 1,950,000 JPY cap
-        // Employment income = 31_950,000 - 1,950,000 = 30,000,000 JPY (exactly at the pre-2022 30M cap)
+        // Employment income = 31_950,000 - 1,950,000 = 30,000,000 JPY (exactly at the
+        // pre-2022 30M cap)
         // Mortgage deduction: 200,000 JPY with maxTotalIncome = 30,000,000 JPY
         long grossSalary = 31_950_000L;
         long mortgageDeduction = 200_000L;
@@ -370,9 +386,11 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
+                        new ZeroInhabitantTaxLimitStrategy()));
 
-        // Assert: 30,000,000 <= 30,000,000 -> eligible under pre-2022 rules, deduction applied
+        // Assert: 30,000,000 <= 30,000,000 -> eligible under pre-2022 rules, deduction
+        // applied
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax() - 204_200L);
         assertThat(result.netIncome()).isEqualTo(controlResult.netIncome() + 204_200L);
     }
@@ -381,7 +399,8 @@ class ActiveTaxCalculationEngineTest {
     void calculate_pre2022_mortgageDeduction_exceedsCapBy1Yen_disqualified() {
         // Gross salary: 31_950_001 JPY
         // Employment income deduction: 1,950,000 JPY cap
-        // Employment income = 31_950,001 - 1,950,000 = 30,000,001 JPY (exceeds pre-2022 30M cap by 1 JPY)
+        // Employment income = 31_950,001 - 1,950,000 = 30,000,001 JPY (exceeds pre-2022
+        // 30M cap by 1 JPY)
         // Mortgage deduction: 200,000 JPY with maxTotalIncome = 30,000,000 JPY
         long grossSalary = 31_950_001L;
         long mortgageDeduction = 200_000L;
@@ -405,9 +424,11 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
+                        new ZeroInhabitantTaxLimitStrategy()));
 
-        // Assert: 30,000,001 > 30,000,000 -> disqualified, mortgage deduction forced to 0
+        // Assert: 30,000,001 > 30,000,000 -> disqualified, mortgage deduction forced to
+        // 0
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
         assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
     }
@@ -438,7 +459,8 @@ class ActiveTaxCalculationEngineTest {
                 0, 0, new MortgageYearlyResult(0L, 0L, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
         TaxCalculationResult result2021 = engine.calculate(
                 context2021, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0,
+                new MortgageYearlyResult(0L, mortgageDeduction, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Under 2021 rules (cap = 30M): 23.05M <= 30M -> eligible!
         assertThat(result2021.incomeTax()).isEqualTo(control2021.incomeTax() - 204_200L);
@@ -457,7 +479,8 @@ class ActiveTaxCalculationEngineTest {
                 0, 0, new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
         TaxCalculationResult result2026 = engine.calculate(
                 context2026, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0,
+                new MortgageYearlyResult(0L, mortgageDeduction, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Under 2026 rules (cap = 20M): 23.05M > 20M -> disqualified!
         assertThat(result2026.incomeTax()).isEqualTo(control2026.incomeTax());
@@ -482,7 +505,8 @@ class ActiveTaxCalculationEngineTest {
         // 3 years of service -> deduction = max(800,000, 400,000 * 3) = 1,200,000 JPY
         // Retirement income = 3,200,000 JPY -> excess = 2,000,000 JPY (<= 3,000,000)
         // Short-term strategy applies half taxation: taxableIncome = 1,000,000 JPY
-        // Income tax rate: 5% (offset 0) -> base = 50,000 JPY -> with 2.1% surtax = 51,050 JPY
+        // Income tax rate: 5% (offset 0) -> base = 50,000 JPY -> with 2.1% surtax =
+        // 51,050 JPY
         // Inhabitant tax rate: 10% -> 100,000 JPY
         SimulationContext context = new SimulationContext();
         context.setYear(2024);
@@ -498,8 +522,10 @@ class ActiveTaxCalculationEngineTest {
     void calculateRetirementTax_shortTerm_exceedsThreeMillionExcess_antiAbuseLadderApplied() {
         // 3 years of service (<= 5 years) -> deduction = 1,200,000 JPY
         // Retirement income = 10,000,000 JPY -> excess = 8,800,000 JPY (> 3,000,000)
-        // Short-term anti-abuse: taxableIncome = 1,500,000 + (8,800,000 - 3,000,000) = 7,300,000 JPY
-        // (Without anti-abuse, taxableIncome would have been 8,800,000 / 2 = 4,400,000 JPY)
+        // Short-term anti-abuse: taxableIncome = 1,500,000 + (8,800,000 - 3,000,000) =
+        // 7,300,000 JPY
+        // (Without anti-abuse, taxableIncome would have been 8,800,000 / 2 = 4,400,000
+        // JPY)
         // Income tax rate: 23% (offset -636,000)
         // baseIncomeTax = 7,300,000 * 0.23 - 636,000 = 1,043,000 JPY
         // incomeTax = 1,043,000 + (long)(1,043,000 * 0.021) = 1,064,903 JPY
@@ -518,7 +544,8 @@ class ActiveTaxCalculationEngineTest {
     void calculateRetirementTax_standard_overFiveYears_fullHalfTaxationPreserved() {
         // 10 years of service (> 5 years) -> deduction = 4,000,000 JPY
         // Retirement income = 12,800,000 JPY -> excess = 8,800,000 JPY
-        // Standard strategy: full half-taxation -> taxableIncome = 8,800,000 / 2 = 4,400,000 JPY
+        // Standard strategy: full half-taxation -> taxableIncome = 8,800,000 / 2 =
+        // 4,400,000 JPY
         // Income tax rate: 20% (offset -427,500)
         // baseIncomeTax = 4,400,000 * 0.20 - 427,500 = 452,500 JPY
         // incomeTax = 452,500 + (long)(452,500 * 0.021) = 462,002 JPY
@@ -537,13 +564,20 @@ class ActiveTaxCalculationEngineTest {
     void calculate_crossEraEmploymentIncomeDeduction_provesBugFixFor2028() {
         // Gross salary: 1,800,000 JPY
         // Statutory Minimum Deduction across 4 Eras:
-        // - 2024 (Legacy): 1.8M * 40% - 100k = 620,000 JPY -> Employment Income = 1,180,000 JPY
-        // - 2025 (Reiwa 7): 650,000 JPY (Minimum Guarantee) -> Employment Income = 1,150,000 JPY
-        // - 2026 (Reiwa 8): 740,000 JPY (Special Sunset) -> Employment Income = 1,060,000 JPY
-        // - 2028 (Reiwa 10): 690,000 JPY (Statutory Baseline) -> Employment Income = 1,110,000 JPY
-        // (Bug in Issue 142 caused 2028+ to wrongly remain at 740,000 JPY, underestimating income by 50,000 JPY)
-        // 1.8M JPY is an optimal boundary test value exceeding the 1.68M/1.78M exemption thresholds,
-        // ensuring non-zero income and inhabitant taxes across all 4 eras without loose assertions.
+        // - 2024 (Legacy): 1.8M * 40% - 100k = 620,000 JPY -> Employment Income =
+        // 1,180,000 JPY
+        // - 2025 (Reiwa 7): 650,000 JPY (Minimum Guarantee) -> Employment Income =
+        // 1,150,000 JPY
+        // - 2026 (Reiwa 8): 740,000 JPY (Special Sunset) -> Employment Income =
+        // 1,060,000 JPY
+        // - 2028 (Reiwa 10): 690,000 JPY (Statutory Baseline) -> Employment Income =
+        // 1,110,000 JPY
+        // (Bug in Issue 142 caused 2028+ to wrongly remain at 740,000 JPY,
+        // underestimating income by 50,000 JPY)
+        // 1.8M JPY is an optimal boundary test value exceeding the 1.68M/1.78M
+        // exemption thresholds,
+        // ensuring non-zero income and inhabitant taxes across all 4 eras without loose
+        // assertions.
         long grossSalary = 1_800_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
@@ -563,8 +597,10 @@ class ActiveTaxCalculationEngineTest {
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 620,000 = 1,180,000
         // Basic deduction 2024: 480,000 (income tax), 430,000 (inhabitant tax)
-        // Taxable income tax = 1,180,000 - 480,000 = 700,000 -> 700,000 * 5% = 35,000 -> recon: 35,735
-        // Taxable inhab tax = 1,180,000 - 430,000 = 750,000 -> 750,000 * 10% + 5,000 (flat levy) = 80,000
+        // Taxable income tax = 1,180,000 - 480,000 = 700,000 -> 700,000 * 5% = 35,000
+        // -> recon: 35,735
+        // Taxable inhab tax = 1,180,000 - 430,000 = 750,000 -> 750,000 * 10% + 5,000
+        // (flat levy) = 80,000
         assertThat(res2024.incomeTax()).isEqualTo(35_735L);
         assertThat(res2024.inhabitantTax()).isEqualTo(80_000L);
 
@@ -582,8 +618,10 @@ class ActiveTaxCalculationEngineTest {
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 650,000 = 1,150,000
         // Basic deduction 2025 (Reiwa 7, income <= 1.32M): 950,000 (income tax)
-        // Taxable income tax = 1,150,000 - 950,000 = 200,000 -> 200,000 * 5% = 10,000 -> recon: 10,210
-        // Taxable inhab tax = 1,150,000 - 430,000 = 720,000 -> 720,000 * 10% + 5,000 (flat levy) = 77,000
+        // Taxable income tax = 1,150,000 - 950,000 = 200,000 -> 200,000 * 5% = 10,000
+        // -> recon: 10,210
+        // Taxable inhab tax = 1,150,000 - 430,000 = 720,000 -> 720,000 * 10% + 5,000
+        // (flat levy) = 77,000
         assertThat(res2025.incomeTax()).isEqualTo(10_210L);
         assertThat(res2025.inhabitantTax()).isEqualTo(77_000L);
 
@@ -601,8 +639,10 @@ class ActiveTaxCalculationEngineTest {
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 740,000 = 1,060,000
         // Basic deduction 2026 (Reiwa 8, income <= 4.89M): 1,040,000 (income tax)
-        // Taxable income tax = 1,060,000 - 1,040,000 = 20,000 -> 20,000 * 5% = 1,000 -> recon: 1,021
-        // Taxable inhab tax = 1,060,000 - 430,000 = 630,000 -> 630,000 * 10% + 5,000 (flat levy) = 68,000
+        // Taxable income tax = 1,060,000 - 1,040,000 = 20,000 -> 20,000 * 5% = 1,000 ->
+        // recon: 1,021
+        // Taxable inhab tax = 1,060,000 - 430,000 = 630,000 -> 630,000 * 10% + 5,000
+        // (flat levy) = 68,000
         assertThat(res2026.incomeTax()).isEqualTo(1_021L);
         assertThat(res2026.inhabitantTax()).isEqualTo(68_000L);
 
@@ -620,8 +660,10 @@ class ActiveTaxCalculationEngineTest {
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 690,000 = 1,110,000 (50k higher than 2026!)
         // Basic deduction 2028 (Reiwa 10, income <= 1.32M): 990,000 (income tax)
-        // Taxable income tax = 1,110,000 - 990,000 = 120,000 -> 120,000 * 5% = 6,000 -> recon: 6,126 (5,105 higher than 2026!)
-        // Taxable inhab tax = 1,110,000 - 430,000 = 680,000 -> 680,000 * 10% + 5,000 (flat levy) = 73,000 (5,000 higher than 2026!)
+        // Taxable income tax = 1,110,000 - 990,000 = 120,000 -> 120,000 * 5% = 6,000 ->
+        // recon: 6,126 (5,105 higher than 2026!)
+        // Taxable inhab tax = 1,110,000 - 430,000 = 680,000 -> 680,000 * 10% + 5,000
+        // (flat levy) = 73,000 (5,000 higher than 2026!)
         assertThat(res2028.incomeTax()).isEqualTo(6_126L);
         assertThat(res2028.inhabitantTax()).isEqualTo(73_000L);
     }
@@ -659,7 +701,8 @@ class ActiveTaxCalculationEngineTest {
                 context2015, grossSalary, 0L, 0, 0, 0,
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
-        // Income tax for 2015 should be strictly higher than 2014 due to the 45% statutory bracket
+        // Income tax for 2015 should be strictly higher than 2014 due to the 45%
+        // statutory bracket
         assertThat(res2015.incomeTax()).isGreaterThan(res2014.incomeTax());
     }
 
@@ -696,14 +739,17 @@ class ActiveTaxCalculationEngineTest {
                 context2007, grossSalary, 0L, 0, 0, 0,
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
-        // Under 2006 tiers, taxable income around 1.5M was taxed at 5%, whereas in 2007 it was taxed at flat 10%
+        // Under 2006 tiers, taxable income around 1.5M was taxed at 5%, whereas in 2007
+        // it was taxed at flat 10%
         assertThat(res2007.inhabitantTax()).isGreaterThan(res2006.inhabitantTax());
     }
 
     @Test
     void calculate_firstYearMortgage_exceedsCap_recordRefundAndInhabitantCreditAreZero() {
-        // Issue 139: Gross salary 25,000,000 JPY (employment income > 20,000,000 JPY) in 2026
-        // Mortgage in occupancy year: kakuteiDeduction = 200,000 JPY, yearEndDeduction = 0L
+        // Issue 139: Gross salary 25,000,000 JPY (employment income > 20,000,000 JPY)
+        // in 2026
+        // Mortgage in occupancy year: kakuteiDeduction = 200,000 JPY, yearEndDeduction
+        // = 0L
         long grossSalary = 25_000_000L;
         long kakuteiDeduction = 200_000L;
         long statutoryIncomeCap = 20_000_000L;
@@ -721,9 +767,11 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
+                        new Post2022InhabitantTaxLimitStrategy()));
 
-        // Assert: high income earner is disqualified in first year; packaged record must be 0 for refund and inhabitant rollover
+        // Assert: high income earner is disqualified in first year; packaged record
+        // must be 0 for refund and inhabitant rollover
         assertThat(result).isNotNull();
         PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
         assertThat(record).isNotNull();
@@ -758,18 +806,23 @@ class ActiveTaxCalculationEngineTest {
         // First year mortgage run
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
+                        new Post2022InhabitantTaxLimitStrategy()));
 
-        // In first year, withholding tax is preserved (current year income tax is NOT reduced)
+        // In first year, withholding tax is preserved (current year income tax is NOT
+        // reduced)
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
         assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
 
-        // PreviousYearTaxRecord packages the Kakutei Shinkoku refund capped by base income tax
+        // PreviousYearTaxRecord packages the Kakutei Shinkoku refund capped by base
+        // income tax
         PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
         assertThat(record).isNotNull();
-        // Since baseIncomeTax (426,100) > kakuteiDeduction (200,000), refund is full 200,000
+        // Since baseIncomeTax (426,100) > kakuteiDeduction (200,000), refund is full
+        // 200,000
         assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(200_000L);
-        // All kakutei deduction was absorbed by income tax, so no leftover for inhabitant tax
+        // All kakutei deduction was absorbed by income tax, so no leftover for
+        // inhabitant tax
         assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
     }
 
@@ -794,7 +847,8 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
+                        new Post2022InhabitantTaxLimitStrategy()));
 
         assertThat(result).isNotNull();
         assertThat(result.incomeTax()).isGreaterThan(0L);
@@ -805,9 +859,11 @@ class ActiveTaxCalculationEngineTest {
         assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(record.baseIncomeTax());
         assertThat(record.pendingKakuteiShinkokuRefund()).isLessThan(kakuteiDeduction);
 
-        // Remaining deduction rolls over to inhabitant tax, capped at statutory inhabitant tax limit (<= 97,500)
+        // Remaining deduction rolls over to inhabitant tax, capped at statutory
+        // inhabitant tax limit (<= 97,500)
         long remaining = kakuteiDeduction - record.pendingKakuteiShinkokuRefund();
-        long expectedRollover = Math.min(remaining, Math.min(97_500L, (long) (record.taxableIncomeForIncomeTax() * 0.05)));
+        long expectedRollover = Math.min(remaining,
+                Math.min(97_500L, (long) (record.taxableIncomeForIncomeTax() * 0.05)));
         assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(expectedRollover);
         assertThat(record.unusedMortgageDeductionForInhabitantTax()).isGreaterThan(0L);
     }
@@ -816,7 +872,8 @@ class ActiveTaxCalculationEngineTest {
     void calculate_lowIncome_belowFlatLevyThreshold_exemptFromInhabitantTax() {
         // Gross salary: 900,000 JPY
         // Employment income deduction: 550,000 JPY (minimum deduction in 2024)
-        // Employment income = 350,000 JPY (<= 450,000 JPY statutory threshold under Local Tax Act Art. 295(1)(2))
+        // Employment income = 350,000 JPY (<= 450,000 JPY statutory threshold under
+        // Local Tax Act Art. 295(1)(2))
         long grossSalary = 900_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
@@ -840,11 +897,14 @@ class ActiveTaxCalculationEngineTest {
     @Test
     void calculate_incomeAboveFlatLevyThreshold_zeroTaxableIncome_paysOnlyFlatLevy() {
         // Gross salary: 1,050,000 JPY
-        // Employment income deduction in 2024: 550,000 JPY -> Employment income = 500,000 JPY (> 450,000 JPY threshold)
+        // Employment income deduction in 2024: 550,000 JPY -> Employment income =
+        // 500,000 JPY (> 450,000 JPY threshold)
         // Health insurance at 10% -> approx 105,600 JPY
-        // Total inhabitant deductions = 105,600 + 430,000 (basic deduction) = 535,600 JPY > 500,000 JPY
+        // Total inhabitant deductions = 105,600 + 430,000 (basic deduction) = 535,600
+        // JPY > 500,000 JPY
         // Taxable income for inhabitant tax = 0 JPY
-        // But total income (500,000 JPY) > 450,000 JPY threshold -> Flat levy 5,000 JPY applies!
+        // But total income (500,000 JPY) > 450,000 JPY threshold -> Flat levy 5,000 JPY
+        // applies!
         long grossSalary = 1_050_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
@@ -868,8 +928,10 @@ class ActiveTaxCalculationEngineTest {
     @Test
     void calculateRetirementTax_highRetirementIncome_exemptFromFlatLevy() {
         // High retirement income: 20,000,000 JPY, 10 years of service
-        // Under Local Tax Act Art. 50-2 & 328, retirement income is separate taxation exempt from flat levy.
-        // Deduction: 4,000,000 JPY -> excess = 16,000,000 JPY -> taxable = 8,000,000 JPY
+        // Under Local Tax Act Art. 50-2 & 328, retirement income is separate taxation
+        // exempt from flat levy.
+        // Deduction: 4,000,000 JPY -> excess = 16,000,000 JPY -> taxable = 8,000,000
+        // JPY
         // Inhabitant tax: 8,000,000 * 10% = 800,000 JPY (strictly 0 JPY flat levy)
         SimulationContext context = new SimulationContext();
         context.setYear(2024);
@@ -879,4 +941,3 @@ class ActiveTaxCalculationEngineTest {
         assertThat(result.inhabitantTax()).isEqualTo(800_000L);
     }
 }
-
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
index feb45ad0..98ebdece 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
@@ -30,6 +30,8 @@ import ch.lin.platform.domain.model.AuditableEntity;
 import ch.lin.platform.domain.model.BaseEntity;
 import jakarta.persistence.Column;
 import jakarta.persistence.Entity;
+import jakarta.persistence.EnumType;
+import jakarta.persistence.Enumerated;
 import jakarta.persistence.Index;
 import jakarta.persistence.Table;
 import jakarta.persistence.UniqueConstraint;
@@ -47,9 +49,9 @@ import lombok.Setter;
  */
 @Entity
 @Table(name = FinanceHubConfig.TABLE_NAME, indexes = {
-    @Index(name = FinanceHubConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
+        @Index(name = FinanceHubConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
 }, uniqueConstraints = {
-    @UniqueConstraint(name = FinanceHubConfig.UNIQUE_NAME, columnNames = FinanceHubConfig.NAME_COLUMN)
+        @UniqueConstraint(name = FinanceHubConfig.UNIQUE_NAME, columnNames = FinanceHubConfig.NAME_COLUMN)
 })
 @Getter
 // Hibernate requires a no-argument constructor for proxying and reflective
@@ -58,7 +60,7 @@ import lombok.Setter;
 // fields. Therefore, we use AccessLevel.PROTECTED to satisfy JPA/Hibernate
 // requirements while preventing arbitrary instantiation in business logic.
 @NoArgsConstructor(access = AccessLevel.PROTECTED)
-@EqualsAndHashCode(of = {"name", "enabled", "fileProcessingTimeoutSeconds"}, callSuper = false)
+@EqualsAndHashCode(of = { "name", "enabled", "fileProcessingTimeoutSeconds" }, callSuper = false)
 public class FinanceHubConfig extends AuditableEntity {
 
     /**
@@ -131,6 +133,7 @@ public class FinanceHubConfig extends AuditableEntity {
     public static final String GA_MUTATION_RATE_COLUMN = "ga_mutation_rate";
     public static final String GA_ELITE_RATIO_COLUMN = "ga_elite_ratio";
     public static final String SIMULATION_EARLY_REPAYMENT_MONTH_COLUMN = "simulation_early_repayment_month";
+    public static final String RETIREMENT_MONTH_MODE_COLUMN = "retirement_month_mode";
     public static final String DEFAULT_CURRENCY_COLUMN = "default_currency";
 
     /**
@@ -297,6 +300,17 @@ public class FinanceHubConfig extends AuditableEntity {
     @Setter
     private Integer simulationEarlyRepaymentMonth = 12;
 
+    /**
+     * Determines which month retirement takes effect in: the December of the
+     * target retirement age's year, or that age's birth month.
+     */
+    @NotNull
+    @Enumerated(EnumType.STRING)
+    @ColumnDefault("'DECEMBER'")
+    @Column(name = FinanceHubConfig.RETIREMENT_MONTH_MODE_COLUMN, nullable = false, length = 20)
+    @Setter
+    private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;
+
     /**
      * Creates a new FinanceHubConfig with the specified name.
      *
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/RetirementMonthMode.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/RetirementMonthMode.java
new file mode 100644
index 00000000..ef8aac4a
--- /dev/null
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/RetirementMonthMode.java
@@ -0,0 +1,43 @@
+/*
+ * The MIT License (MIT)
+ *
+ * Copyright (c) 2026 Che-Hung Lin
+ *
+ * Permission is hereby granted, free of charge, to any person obtaining a copy
+ * of this software and associated documentation files (the "Software"), to deal
+ * in the Software without restriction, including without limitation the rights
+ * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
+ * copies of the Software, and to permit persons to whom the Software is
+ * furnished to do so, subject to the following conditions:
+ *
+ * The above copyright notice and this permission notice shall be included in
+ * all copies or substantial portions of the Software.
+ *
+ * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
+ * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
+ * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
+ * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
+ * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
+ * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
+ * THE SOFTWARE.
+ */
+package ch.lin.finance.hub.backend.api.domain.model.config;
+
+/**
+ * Determines which month retirement takes effect in: either December of the
+ * retirement year (working through year-end) or the user's birth month.
+ */
+public enum RetirementMonthMode {
+
+    /**
+     * Retirement month is fixed to December of the target retirement age year
+     * (the user works through the end of December).
+     */
+    DECEMBER,
+
+    /**
+     * Retirement month is the user's birth month of the target retirement age year
+     * (the user works through the end of their birth month).
+     */
+    BIRTH_MONTH
+}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
index fc4d8b7b..3f5c5bf6 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
@@ -77,8 +77,8 @@ public class FinanceHubConfigController {
      * Constructs a new FinanceHubConfigController.
      *
      * @param configService The service responsible for managing global Finance
-     * Hub configurations.
-     * @param configMapper The mapper to convert results to responses.
+     *                      Hub configurations.
+     * @param configMapper  The mapper to convert results to responses.
      */
     public FinanceHubConfigController(FinanceHubConfigService configService, FinanceHubConfigMapper configMapper) {
         this.configService = configService;
@@ -90,7 +90,7 @@ public class FinanceHubConfigController {
      * which one is currently enabled.
      *
      * @return A {@link ResponseEntity} containing an
-     * {@link AllConfigsResponse}.
+     *         {@link AllConfigsResponse}.
      */
     @GetMapping
     public ResponseEntity<AllConfigsResponse> getAllConfigs() {
@@ -105,7 +105,7 @@ public class FinanceHubConfigController {
      * Creates a new global configuration.
      *
      * @param request The request body containing the details of the new
-     * configuration.
+     *                configuration.
      * @return A {@link ResponseEntity} with an HTTP 201 Created status.
      */
     @PostMapping
@@ -132,6 +132,7 @@ public class FinanceHubConfigController {
                 .gaMutationRate(request.getGaMutationRate())
                 .gaEliteRatio(request.getGaEliteRatio())
                 .simulationEarlyRepaymentMonth(request.getSimulationEarlyRepaymentMonth())
+                .retirementMonthMode(request.getRetirementMonthMode())
                 .build();
 
         FinanceHubConfigResponse createdConfig = configMapper.toResponse(configService.createConfigResult(command));
@@ -165,9 +166,9 @@ public class FinanceHubConfigController {
     /**
      * Creates or partially updates a specific global configuration.
      *
-     * @param name The name of the configuration to create or update.
+     * @param name    The name of the configuration to create or update.
      * @param request The request body containing the configuration fields to
-     * update.
+     *                update.
      * @return A {@link ResponseEntity} with an HTTP 200 OK status.
      */
     @PatchMapping("/{name}")
@@ -194,6 +195,7 @@ public class FinanceHubConfigController {
                 .gaMutationRate(Optional.ofNullable(request.getGaMutationRate()))
                 .gaEliteRatio(Optional.ofNullable(request.getGaEliteRatio()))
                 .simulationEarlyRepaymentMonth(Optional.ofNullable(request.getSimulationEarlyRepaymentMonth()))
+                .retirementMonthMode(Optional.ofNullable(request.getRetirementMonthMode()))
                 .build();
 
         FinanceHubConfigResponse savedConfig = configMapper.toResponse(configService.saveConfigResult(command));
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
index 81f2ac1d..8afe8363 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.controller.config.dto.system;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import jakarta.validation.constraints.DecimalMin;
 import jakarta.validation.constraints.Max;
@@ -142,4 +143,7 @@ public class CreateFinanceHubConfigRequest {
     @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
     @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
     private Integer simulationEarlyRepaymentMonth = 12;
+
+    @NotNull(message = "Retirement month mode cannot be null")
+    private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
index cafc2223..d09f8985 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.controller.config.dto.system;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import lombok.Data;
 
@@ -49,4 +50,5 @@ public class FinanceHubConfigResponse {
     private Double gaMutationRate;
     private Integer gaEliteRatio;
     private Integer simulationEarlyRepaymentMonth;
+    private RetirementMonthMode retirementMonthMode;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
index e06186c4..bc555648 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.controller.config.dto.system;
 
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 import jakarta.validation.constraints.Max;
 import jakarta.validation.constraints.Min;
@@ -107,4 +108,6 @@ public class UpdateFinanceHubConfigRequest {
     @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
     @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
     private Integer simulationEarlyRepaymentMonth;
+
+    private RetirementMonthMode retirementMonthMode;
 }
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index e4b23d44..55477d2b 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -110,6 +110,10 @@ finance.hub.system-config.ga-max-generations=20
 finance.hub.system-config.ga-mutation-rate=0.2
 finance.hub.system-config.ga-elite-ratio=10
 
+# Simulation Defaults
+finance.hub.system-config.simulation-early-repayment-month=12
+finance.hub.system-config.retirement-month-mode=DECEMBER
+
 # =============================================================================
 # Pension Rule Defaults
 # =============================================================================
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index aec850eb..2ebb05a5 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -103,6 +103,7 @@ finance.hub.system-config.ga-elite-ratio=10
 
 # Simulation Defaults
 finance.hub.system-config.simulation-early-repayment-month=12
+finance.hub.system-config.retirement-month-mode=DECEMBER
 
 # =============================================================================
 # Pension Rule Defaults
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
index c1e8b9e3..5db484da 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
@@ -60,6 +60,7 @@ import ch.lin.finance.hub.backend.api.app.service.config.system.result.FinanceHu
 import ch.lin.finance.hub.backend.api.controller.config.dto.system.CreateFinanceHubConfigRequest;
 import ch.lin.finance.hub.backend.api.controller.config.dto.system.FinanceHubConfigMapper;
 import ch.lin.finance.hub.backend.api.controller.config.dto.system.UpdateFinanceHubConfigRequest;
+import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
 
 @ExtendWith(MockitoExtension.class)
 class FinanceHubConfigControllerTest {
@@ -118,6 +119,7 @@ class FinanceHubConfigControllerTest {
         createdConfig.setEnabled(true);
         createdConfig.setFileProcessingTimeoutSeconds(60);
         createdConfig.setSimulationEarlyRepaymentMonth(12);
+        createdConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);
 
         when(configService.createConfigResult(any(CreateFinanceHubConfigCommand.class))).thenReturn(createdConfig);
 
@@ -131,7 +133,8 @@ class FinanceHubConfigControllerTest {
                                 .containsString("/configs/system/new-config"))))
                 .andExpect(jsonPath("$.data.name").value("new-config"))
                 .andExpect(jsonPath("$.data.enabled").value(true))
-                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(12));
+                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(12))
+                .andExpect(jsonPath("$.data.retirementMonthMode").value("DECEMBER"));
 
         verify(configService, times(1)).createConfigResult(any(CreateFinanceHubConfigCommand.class));
     }
@@ -174,6 +177,7 @@ class FinanceHubConfigControllerTest {
         savedConfig.setEnabled(false);
         savedConfig.setFileProcessingTimeoutSeconds(30);
         savedConfig.setSimulationEarlyRepaymentMonth(6);
+        savedConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);
 
         when(configService.saveConfigResult(any(UpdateFinanceHubConfigCommand.class))).thenReturn(savedConfig);
 
@@ -183,7 +187,8 @@ class FinanceHubConfigControllerTest {
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.name").value("test-config"))
                 .andExpect(jsonPath("$.data.enabled").value(false))
-                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(6));
+                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(6))
+                .andExpect(jsonPath("$.data.retirementMonthMode").value("DECEMBER"));
 
         verify(configService, times(1)).saveConfigResult(any(UpdateFinanceHubConfigCommand.class));
     }
diff --git a/finance-hub-backend/todo/pending fix/question-191-02.md b/finance-hub-backend/todo/pending fix/question-191-02.md
index bab61350..6fbdd006 100644
--- a/finance-hub-backend/todo/pending fix/question-191-02.md
+++ b/finance-hub-backend/todo/pending fix/question-191-02.md
@@ -86,3 +86,75 @@ public final class RetirementMonthResolver {
   - `mode=BIRTH_MONTH`時，回傳`birthDate.getMonthValue()`（測試涵蓋1月、6月、12月幾個邊界值）。
   - `birthDate=null`時，回傳null（不管mode是什�么）。
 - 這份文件不包含「退休月怎麼被使用」的驗證——那部分的測試屬於191-03、191-04各自的範圍。
+
+
+---
+
+## 討論收斂與實作清單（依據與使用者/PM搭配 question-191 & 191-01 討論結果與系統現行規範更新）
+
+### 1. 核心規則收斂確認
+- **DECEMBER 模式行為**：無視生日，固定回傳 `12`（即使 `birthDate == null` 也固定回傳 `12`，不退回 null）。
+- **BIRTH_MONTH 模式行為**：依 `birthDate.getMonthValue()` 回傳；僅當 `mode == BIRTH_MONTH` 且 `birthDate == null` 時回傳 `null`（呼叫端退回現行整年退休或 fallback）。
+- **回傳值語意**：回傳的月份代表**「最後在職月（Last Working Month）」**（做滿該月最後一天退職）。
+  - 例：`DECEMBER` 模式回傳 12，代表該退休年 1～12 月領滿薪水，退休年無年金，次年（66歲）起領年金。
+  - 例：`BIRTH_MONTH` 且 1 月生日回傳 1，代表該退休年 1 月領薪水，2～12 月領 11 個月年金，滿 65 歲當月做完即停，不工作到明年。
+
+### 2. 實作檔案清單（含配置生態系與 UI）
+後端設定需完整貫穿配置生態系，前端 UI 亦需提供對應設定項：
+
+#### 後端（Backend）：
+1. **Enum 定義**：
+   - `ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode`
+2. **Domain Entity**：
+   - `ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig`（新增欄位、getter/setter、常數）
+3. **外部配置屬性**：
+   - `ch.lin.finance.hub.backend.api.app.config.system.FinanceHubDefaultProperties.SystemConfig`（新增預設屬性 `retirementMonthMode = RetirementMonthMode.DECEMBER`）
+4. **預設設定工廠**：
+   - `ch.lin.finance.hub.backend.api.app.config.system.DefaultConfigFactory`（`create` 方法中映射此欄位）
+5. **DTO 與 Command**：
+   - `ch.lin.finance.hub.backend.api.app.service.config.system.result.FinanceHubConfigResult`（API 回傳物件新增欄位與 `fromEntity` 映射）
+   - `ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand`（建立命令 DTO）
+   - `ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand`（更新命令 DTO）
+6. **服務實作**：
+   - `ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigServiceImpl`（`createConfig`、`updateConfig`、`getResolvedConfig` 中的映射與 fallback 補齊）
+7. **無狀態計算工具**：
+   - `ch.lin.finance.hub.backend.api.app.service.simulation.core.model.RetirementMonthResolver`（實作 `resolveRetirementMonth`）
+
+#### 後端 Web 層與配置檔（Backend Web & Config Properties）：
+1. **Controller & Web DTOs**：
+   - [`FinanceHubConfigController.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java)（`createConfig` 與 `saveConfig` 映射）
+   - [`FinanceHubConfigResponse.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java)（新增回應欄位）
+   - [`CreateFinanceHubConfigRequest.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java)（新增建立欄位與校驗）
+   - [`UpdateFinanceHubConfigRequest.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java)（新增更新欄位）
+   - [`FinanceHubConfigControllerTest.java`](file://finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java)（補齊單元測試）
+2. **Spring Boot 設定檔**：
+   - [`application.properties`](file://finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)（新增 `finance.hub.system-config.retirement-month-mode=DECEMBER`）
+   - [`application-h2.properties`](file://finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)（新增 `finance.hub.system-config.retirement-month-mode=DECEMBER`）
+3. **環境變數檔**：
+   - [`.env.base`](file://finance-hub/.env.base)（新增 `FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"`）
+   - [`.env.dev`](file://finance-hub/.env.dev)（新增 `FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"`）
+   - [`.env.prod`](file://finance-hub/.env.prod)（新增 `FINANCE_HUB_SYSTEM_CONFIG_RETIREMENT_MONTH_MODE="DECEMBER"`）
+
+#### 前端（Frontend / hub-ui）：
+1. **Schema 與 Form 型別定義**：
+   - [`hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts`](file://hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts)：
+     - 在 `systemConfigSchema` 中新增 `retirementMonthMode: z.enum(['DECEMBER', 'BIRTH_MONTH']).default('DECEMBER')`。
+     - `SystemConfigFormValues` 型別自動推導支援。
+2. **設定介面元件**：
+   - [`hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx`](file://hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx)：
+     - 在「Simulation Settings」區塊（緊鄰 `simulationEarlyRepaymentMonth` 旁）新增「Retirement Month Mode」的 `<Select>` 或 Radio 元件（選項：`DECEMBER`「12月（年底退職）」與 `BIRTH_MONTH`「生日當月退職」），並設定預設值與說明文字。
+
+
+---
+
+## 實作完成狀態報告
+- **實作狀態**：✅ 已完成（方向一：退休月判斷機制與系統配置生態系）
+- **範圍確認**：嚴守 191-02 邊界，未越界至 191-03（管線計算分流）與 191-04（房貸扣除自動復活）。
+- **完成項目**：
+  1. 新建 [`RetirementMonthMode.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/RetirementMonthMode.java)。
+  2. 修改 [`FinanceHubConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java) 新增欄位與常數。
+  3. 配置生態系補齊：[`FinanceHubDefaultProperties.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java)、[`DefaultConfigFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java)、[`FinanceHubConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java)、[`CreateFinanceHubConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java)、[`UpdateFinanceHubConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java)、[`FinanceHubConfigServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java)。
+  4. Web 層與環境配置補齊：[`FinanceHubConfigController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java)、[`FinanceHubConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java)、[`CreateFinanceHubConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java)、[`UpdateFinanceHubConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java)、[`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)、[`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)、[`.env.base`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)、[`.env.dev`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)、[`.env.prod`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)。
+  5. 新建無狀態工具類別 [`RetirementMonthResolver.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java)。
+  6. 新建與更新單元測試：[`RetirementMonthResolverTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolverTest.java)、[`FinanceHubConfigServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java)、[`FinanceHubConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java)。
+  7. 前端 UI 模組更新：[`use-finance-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts) 與 [`system-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx)。
diff --git a/finance-hub-backend/todo/pending fix/question-191-03.md b/finance-hub-backend/todo/pending fix/question-191-03.md
index 21a63945..55be2d62 100644
--- a/finance-hub-backend/todo/pending fix/question-191-03.md
+++ b/finance-hub-backend/todo/pending fix/question-191-03.md
@@ -272,3 +272,146 @@ if (prevTax != null && prevTax.pendingWorkingSegmentTaxAdjustment() != 0) {
   4. 退休年，`retirementMonth=1`（整年退休）：驗證行為跟修改前完全一致（回歸測試，確保這份文件的改動沒有波及既有的「整年退休」情境）。
   5. 非退休年：驗證行為跟修改前完全一致（回歸測試）。
   6. `birthDate`為null的退休年：驗證退回情境2（整年視為退休），不拋NullPointerException。
+
+
+---
+
+## 與使用者（PM）討論後的最終架構規格與實作定案（對齊 191-02、191-04 與 DDD 領域邊界）
+
+本節為經過領域分析與架構收斂後的最終設計規範，供 Reviewer 與後續維護者作為系統實作標準。若與上方原提議有差異，以本節內容為準。
+
+### 1. 退休月份與在職/退休月數定義（延續 191-02）
+* **商務原則與真實制度背景（做滿退職原則）**：
+  * 日本企業人事與就業規則壓倒性標準皆為「満65歳に達した日の属する月の末日をもって退職」（以年滿 65 歲當月之最後一天為退職日）。
+  * 日本公的年金法第 18 條明訂年金自退職事由發生之「次月」開始起算給付。
+  * 因此，生日當月必定做滿至該月底（領滿該月全額薪資），次月起無縫銜接領取年金；絕非「生日當月離職、整月沒薪水」（推翻原文 `retirementMonth - 1` 的假定）。
+* **月數計算公式**：
+  [`RetirementMonthResolver.resolveRetirementMonth(...)`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java) 回傳之月份 `M` 代表「最後在職月份」：
+  * **在職月數**：`workingMonths = M`（1 ~ M 月在職，領取 M 個月薪資）
+  * **年金月數**：`pensionMonths = 12 - M`（M+1 ~ 12 月退休，領取 12 - M 個月年金）
+  * **模式分支**：
+    * **DECEMBER 模式**（回傳 12）：全年在職 12 個月，年金月數 0 個月。視同全年在職，不進入退休年金段計算。若 `birthDate` 缺失，依現行規範亦回傳 12。
+    * **BIRTH_MONTH 模式**（做滿生日月 M 退職）：在職 M 個月，年金 12 - M 個月。
+      * 生日月為 1 月：在職 1 個月，年金 11 個月。
+      * 生日月為 6 月：在職 6 個月，年金 6 個月。
+      * 生日月為 12 月：在職 12 個月，年金 0 個月。
+    * **BIRTH_MONTH 模式且 `birthDate == null`**：Resolver 回傳 `null`，呼叫端因無法推算生日月，退回系統既有預設行為（整年視為退休，在職 0 個月、年金 12 個月）。
+* **薪資計算防衛放寬**：
+  [`IncomeExpenseCalculator.java:66`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java) 原先若偵測到 `context.isRetired()` 會強制將薪資歸零。為了支援退休年度計算指定月數（`monthsToAdd > 0`）之在職薪資，防衛條件放寬為：
+  ```java
+  if (context.isRetired() && (monthsToAdd == null || monthsToAdd == 0)) {
+      context.setCurrentAnnualSalary(0L);
+      return 0L;
+  }
+  ```
+
+---
+
+### 2. 兩段稅額相加與方法簽名架構定案（為何否定原文提議之「改簽名回傳物件」）
+
+#### (1) 原文提議及其架構缺陷
+原文提議：將 [`YearlySimulationPipeline`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java) 中的 `applyWorkingTaxes` 與 `applyRetireeTaxes` 改為回傳 [`TaxCalculationResult`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/TaxCalculationResult.java)。情境 1（全年在職）與情境 2（全年退休）維持原本直接修改 `state`，只有情境 3（年中退休）在外部呼叫兩次、拿到兩個回傳物件後，手動在 Pipeline 內進行四項數值（所得稅、住民稅、社保費、淨所得）的加總再寫入 `state`，並在 Pipeline 層手動組裝 [`PreviousYearTaxRecord`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)。
+
+此提議存在以下顯著缺陷：
+1. **破壞領域邊界（DDD Boundary Violation）與職責外洩**：
+   `YearlySimulationPipeline` 的單一職責是「協調年度模擬各階段生命週期（現金流、資產更新、呼叫稅務、事件推進）」，而非「稅務規則運算與跨年記錄打包者」。稅務計算、免稅額扣抵、跨段額度封頂皆屬於領域服務 [`ActiveTaxCalculationEngine`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 的內聚職責。
+2. **無法自然銜接 [question-191-04（房貸確定申告復活，選項 B）](question-191-04.md)**：
+   在 191-04 中，退休年度若有房貸扣除額（住宅ローン控除），在職段所得稅扣抵後「剩餘未用完的房貸扣除額」必須無縫傳遞給退休年金段繼續扣抵；年金段扣抵後仍剩餘的額度，才能轉入住民稅或打包為隔年退稅。這套「跨段接力扣抵與額度封頂」必須由 `taxEngine` 內部嚴密掌握。如果 Pipeline 自己在外面把稅額加總，房貸扣除與稅務上限邏輯將嚴重外洩至 Pipeline。
+3. **方法契約不一致與冗餘分支**：
+   情境 1、2 在方法內部 set state，情境 3 卻在方法外部手動加總 set state，形成雙軌制，製造不必要的膠水代碼（glue code）。
+
+#### (2) 最終採納架構：維持 `void` 簽名 + 自然累加模式（Accumulation Pattern）
+1. **維持純粹的 `void` 方法契約**：
+   `applyWorkingTaxes(...)` 與 `applyRetireeTaxes(...)` 保持 `void` 回傳型別。
+2. **狀態累加模式（State Accumulation）**：
+   方法內部對 `state` 的賦值一律改為「增量累加」：
+   ```java
+   state.setEstimatedIncomeTax(state.getEstimatedIncomeTax() + taxResult.incomeTax());
+   state.setEstimatedResidentTax(state.getEstimatedResidentTax() + taxResult.inhabitantTax());
+   state.setEstimatedSocialInsurance(state.getEstimatedSocialInsurance() + taxResult.socialInsurance());
+   state.setNetAnnualIncome(state.getNetAnnualIncome() + netIncome);
+   ```
+   * **情境 1（全年在職）**：`state` 初始為 0，呼叫一次 `applyWorkingTaxes`，累加後即為全年在職數值。
+   * **情境 2（全年退休）**：`state` 初始為 0，呼叫一次 `applyRetireeTaxes`，累加後即為全年退休數值。
+   * **情境 3（年中退休）**：依序呼叫 `applyWorkingTaxes`（從 0 累加在職段）與 `applyRetireeTaxes`（在此基礎上累加年金段）。
+   **所有情境共享同一套乾淨的累加邏輯，Pipeline 零分支膠水代碼，完全符合高內聚低耦合原則。**
+3. **在職差額富化於稅務內部完成**：
+   在職段 YTD 差額在 `applyWorkingTaxes` 內部產出 `taxResult` 後，由領域引擎建立的 `PreviousYearTaxRecord` 直接富化（Enrich）其 `pendingWorkingSegmentTaxAdjustment` 欄位，維持稅務記錄組裝的高內聚性。
+
+---
+
+### 3. 跨年度在職段差額 `pendingWorkingSegmentTaxAdjustment` 規格與正負號統一定義
+
+#### (1) 為何必須新增此欄位（必要性說明）
+1. **真實世界制度背景（源泉徵收 vs 年末調整 vs 確定申告）**：
+   - 受薪階級每月的薪水單皆由公司代扣「源泉徵收所得稅（Withholding Tax）」。此代扣稅率是依照「全年 12 個月皆有此月薪」的源泉徵收稅額表計算。
+   - 若員工在**年中退休**（例如 6 月底離職），其全年在職薪資僅有 6 個月，年收入大幅縮水，原本每月按「年薪 12 個月」扣繳的稅額明顯**過度扣繳（超額預扣）**。
+   - 在正常年底在職員工身上，公司會在 12 月進行「年末調整（Year-End Adjustment）」將超額扣繳之稅款直接退還於當月薪資。**但年中退休者在 12 月時已無雇主，雇主無法也不會替其辦理年末調整！**
+   - 該退休者必須在**次年 2 ~ 3 月自行前往稅務署提出「確定申告（確定申告，Kakutei Shinkoku）」**，才能向國稅局取回在職段超額預扣的稅款。
+2. **模擬器年步進金流機制**：
+   - 財務模擬器是以「年（Year）」為步進單位。起算年中，使用者的銀行流動現金已預先扣除了已預扣金額（YTD Income Tax, YTD Resident Tax, YTD Social Insurance）。
+   - 模擬器在年中退休年度算出在職段的真實理論應納稅額（True Tax）會遠低於 YTD。這筆「超額預扣應退回之差額」，在真實世界是隔年春季才會匯入銀行帳戶。
+   - 若不透過跨年度稅務記錄（[`PreviousYearTaxRecord`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)）將該差額傳遞至隔年，並於隔年年初注入現金池，這筆退稅將憑空消失，導致模擬現金流失真。
+3. **為何不能複用現有 `pendingKakuteiShinkokuRefund`**：
+   - `pendingKakuteiShinkokuRefund` 在系統中具有非常嚴格且專屬的語意：它是「住宅貸款特別扣除（住宅ローン控除）第一年的確定申告退稅款」。
+   - 房貸退稅具有法定的額度上限（房貸年末餘額 × 0.7%），且所得稅扣除不完的剩餘部分會轉移至次年度住民稅扣抵（`unusedMortgageDeductionForInhabitantTax`）。
+   - 若將在職段薪資稅差額混入 `pendingKakuteiShinkokuRefund`，會污染房貸退稅計算，並導致房貸上限與住民稅轉移額度的判定完全失真。因此，獨立出 `pendingWorkingSegmentTaxAdjustment` 專款專用，是架構上最乾淨且最安全的設計。
+
+#### (2) 正負號統一定義與公式修正（修正原文數學公式矛盾）
+* **原文提議之矛盾**：
+  * 原文提議 L220 使用公式：`totalAdjustment = (TrueTax - YTD)`（應納 - 已扣）。
+    若已預扣 100 萬，理論應納只需 60 萬（應退稅 40 萬），公式計算結果為：`60 - 100 = -40`（**負數**）。
+  * 然而，原文在隔年發放時（L256）卻寫道：`currentLiquidCash = cash + adjustment`，並備註「正數代表退稅加進現金」。
+  * 這造成嚴重的數學符號矛盾：若退稅為負數，加進現金反而會導致退稅被誤扣現金！
+* **統一修正後之正式公式**：
+  為了與現金流完全對齊（**正數 = 退稅進帳 / Cash Inflow，負數 = 補繳出帳 / Cash Outflow**），公式嚴格定義為 **`YTD - TrueTax`（已預扣 - 理論應納）**：
+  ```java
+  long totalAdjustment = (context.getYtdIncomeTax() - trueWorkingSegmentIncomeTax)
+          + (context.getYtdResidentTax() - trueWorkingSegmentInhabitantTax)
+          + (ytdSocialInsurance - trueWorkingSegmentSocialInsurance);
+  ```
+  * **退稅（已預扣 > 理論應納）**：`totalAdjustment > 0`（正數）。隔年年初執行 `cash + totalAdjustment`，流動現金實質增加（退稅款入袋）。
+  * **補繳（已預扣 < 理論應納）**：`totalAdjustment < 0`（負數）。隔年年初執行 `cash + totalAdjustment`，流動現金實質扣除（補繳稅款出帳）。
+
+---
+
+### 4. 年末調整與起算年現金流去重防衛
+
+1. **年末調整執行時機（`applyYearEndAdjustment`）**：
+   * **年中退休（`workingMonths < 12`）**：年末已非在職員工，跳過年末調整；其在職段稅差一律打包入 `pendingWorkingSegmentTaxAdjustment` 移至次年春季發放。
+   * **年底退休（`workingMonths == 12`）**：全年在職至 12 月底，由雇主於 12 月正常執行年末調整，不產生在職段跨年差額（`pendingWorkingSegmentTaxAdjustment = 0L`）。
+2. **起算年現金流去重防衛（`YearlySimulationPipeline:413`）**：
+   * 邏輯防衛：
+     ```java
+     if (context.getYear() == context.getCurrentYear() && (!context.isRetired() || effectiveAnnualSalary > 0)) {
+         // Deduct base YTD net salary already reflected in initial cash pool
+     }
+     ```
+   * **意義**：若為起算年且有在職薪資，扣除 YTD 淨所得基線避免期初現金重複計入；若起算年初已是純退休狀態（無薪資），則不扣除，防止幽靈扣款。
+
+---
+
+### 5. 實作完成檔案與單元測試清單
+
+所有程式碼與單元測試均已實作就緒並編譯通過：
+1. [`PreviousYearTaxRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)：
+   新增 `long pendingWorkingSegmentTaxAdjustment` 欄位（純粹 6 欄位 Record，無過載 constructor）。
+2. [`IncomeExpenseCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)：
+   放寬退休判斷防衛，允許退休年傳入 `monthsToAdd > 0` 計算在職薪資。
+3. [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
+   打包 `PreviousYearTaxRecord` 時傳入 6 參數（預設差額為 `0L`）。
+4. [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)：
+   * 年初發放 `pendingWorkingSegmentTaxAdjustment` 並清零（L88-103）。
+   * `RetirementMonthResolver` 做滿退職月數推算與薪資設定（L118-164）。
+   * 分流計算：`applyWorkingTaxes` 與 `applyRetireeTaxes` 維持 `void`，增量累加寫入 `state`（L193-247）。
+   * 起算年中退休之 YTD 差額富化至 `PreviousYearTaxRecord`（L216-235）。
+   * 起算年現金流去重防衛（L413）。
+5. [`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java)：
+   新增 5 個單元測試，全面覆蓋：
+   - 未來年退休分段加總（`run_retirementYear_calculatesBothWorkingAndRetireeTaxes_andSumsThem`）
+   - 起算年 YTD 差額打包（`run_initialYear_midYearRetirement_calculatesWorkingSegmentAdjustmentAndDefersToNextYear`）
+   - 次年現金發放與清零（`run_initialYearAdjustment_disbursedInNextYearAndCleared`）
+   - DECEMBER 模式全年在職無年金（`run_retirementYear_decemberMode_treatedAsFullWorkingYear`）
+   - BIRTH_MONTH 模式無生日 fallback 全年退休（`run_retirementYear_birthMonthMode_nullBirthDate_fallbacksToFullRetiree`）
+6. [`AssetSimulationEngineImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)：
+   更新 `shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement` 中的手算期望值（L542-552），在退休年（Age 60）現金流正確納入 `DECEMBER` 模式 12 個月之在職淨薪資 `trueAnnualNetIncome`（480 萬），使全系統 914 個測試全數綠燈通過。
diff --git a/finance-hub-backend/todo/pending fix/question-191-04.md b/finance-hub-backend/todo/pending fix/question-191-04.md
index eadbc53a..fd4d87e1 100644
--- a/finance-hub-backend/todo/pending fix/question-191-04.md
+++ b/finance-hub-backend/todo/pending fix/question-191-04.md
@@ -5,6 +5,9 @@
 
 依賴[question-191-02](question-191-02.md)（退休月判斷）跟[question-191-03](question-191-03.md)（在職段/退休段分流、`PreviousYearTaxRecord`新增欄位）——這份文件新增的邏輯，最終也要寫進191-03已經在改的`PreviousYearTaxRecord`建構點，兩份文件的改動會在同一段程式碼交會，交會點在下面「修改3」特別標注。
 
+> [!NOTE]
+> **閱讀指引**：本文件上半部為初期分析與推導草案。最終定案架構（包括選項 B 定案、零死碼原則、跨段接力抵扣機制與防呆指引）請直接參見下方 [補充規格與定案](#與使用者pm討論後的補充規格與定案2026-11-03)。
+
 ## 現狀（修改前，確認過的事實）
 
 ### 1. `MortgageCalculator`的分類判斷，只看入住年份，不知道退休狀態
@@ -146,3 +149,155 @@ if (prevTax != null && prevTax.unusedMortgageDeductionForInhabitantTax() > 0) {
   4. 退休後某一年（不是退休當年），房貸仍在控除窗口內：驗證這一年`PreviousYearTaxRecord`的`pendingKakuteiShinkokuRefund`跟/或`unusedMortgageDeductionForInhabitantTax`被正確算出（取決於封頂結果），且用的是**這一年的年金`baseIncomeTax`**封頂，不是在職時期的稅額。
   5. 延續情境4，驗證隔年`currentLiquidCash`正確收到這筆退稅。
   6. 房貸控除窗口已經到期（`year > endYear`）：驗證不管退休與否都不會產生任何控除額（既有行為，回歸測試，確認這份文件的改動沒有影響控除窗口到期判斷本身）。
+
+
+## 與使用者（PM）討論後的補充規格與定案（2026-11-03）
+
+> [!IMPORTANT]
+> **本章為全案實作的唯一基準（Single Source of Truth）**  
+> 本文件上半部（第 1～149 行）為歷史探討與推導過程，包含過渡時期考慮之選項 A（不改簽名、外層封頂）等思路。  
+> 經與 PM 討論後，**所有架構與實作嚴格以本章「補充規格與定案」為準**（上半部未更新之分析僅作背景保留，不再生效）。
+
+### 1. 介面設計與領域封裝定案：採用「選項 B」（對稱傳入 `mortgageResult`）
+
+* **設計演進脈絡**：
+  * 本文件上半部（L1～149）初期分析曾探討「選項 A（年金不傳房貸，在外面 Pipeline 算）」，其考量點為「直覺認為年金計算與房貸無關，傳進年金方法好像違反 DDD」。
+  * 但重新審視領域劃分後確認：[`ActiveTaxCalculationEngine`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 是**稅務領域專家服務（Tax Domain Service）**，而非單純的年金計算器（那是 `PensionCalculator`）。在稅法中，房貸控除屬於「稅額扣抵（Tax Credit）」，稅額扣抵本來就是稅務引擎在算出各類所得稅（薪資或年金）後的核心職責。
+  * 若採「選項 A」，會導致稅法封頂公式（`Math.min`、`calculateLimit`、打包 `PreviousYearTaxRecord`）外洩到 `YearlySimulationPipeline`，形成職責破碎。
+* **最終定案**：
+  確定採用 **【選項 B】**——讓 `calculatePensionTax` 額外接收 `mortgageResult`：
+  ```java
+  public TaxCalculationResult calculatePensionTax(
+          SimulationContext context,
+          long pensionIncome,
+          int age,
+          MortgageYearlyResult mortgageResult)
+  ```
+* **架構原則與規格細節**：
+  1. **領域高度內聚**：所有稅額計算、房貸封頂與 `PreviousYearTaxRecord` 打包邏輯 100% 留在 `ActiveTaxCalculationEngine` 內部，Pipeline 保持純粹編排。
+  2. **鏡像對稱**：與在職段 `calculate(..., mortgageResult)` 保持完全對稱。
+  3. **資料結構零污染**：[`TaxCalculationResult`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/TaxCalculationResult.java) 保持既有 4 個欄位（`incomeTax`, `inhabitantTax`, `socialInsurance`, `netIncome`）即可，完全無需擴充中間變數。退稅屬於跨年現金流狀態，全權由 `PreviousYearTaxRecord` 承載。
+  4. **零死碼原則（不保留向下相容多載）**：
+     * 本專案尚未正式 Release，沒有向下相容包袱。
+     * **直接修改 `calculatePensionTax` 方法簽名為上述 4 個參數**，徹底移除舊的 3 參數簽名，嚴禁保留舊方法作為死碼（Dead Code）。
+     * 所有既有呼叫點（Pipeline 及既有單元測試）一律同步更新為傳入 4 個參數（無房貸時傳入 `null`）。
+
+### 2. 跨段接力抵扣與防線機制（解決 1 月退休極端場景與重複封頂）
+
+#### (1) 業務場景與法定防線（Why）
+* **上限防線（Cap）永遠有效**：
+  確定申告退稅款本質為預扣稅款之退還，法定上限為該納稅義務人該年度實際應納所得稅額（`Math.min(baseIncomeTax, kakuteiDeduction)`）。若該段所得稅為 0，退稅額即為 0，絕對不可能發生「沒繳稅卻退還全額房貸控除額」之情況。
+* **分流過渡年（191-03 情境 3）的接力痛點**：
+  在退休當年（例如 1 月退休，只工作 1 個月），在職段所得極低、應納所得稅為 0，在職段僅能抵扣 0 元房貸額度，尚餘全額房貸控除額；若下半年開始領取年金並繳納年金所得稅，依真實稅法確定申告之規定，未用完之房貸額度應當由同年度的年金所得稅繼續扣抵，否則納稅義務人的合法抵稅權益將被無端剝奪。
+
+#### (2) 領域狀態流轉與接力運算機制（How）
+在系統設計上，在職段與退休段在同一個年度循環（`YearlySimulationPipeline`）中依序執行，並共享同一個 `SimulationContext`。因此**接力抵扣完全透過 `context.getPreviousYearTaxRecord()` 自然流轉，方法簽名嚴格保持 4 個參數，零多餘旗標、零外洩邏輯**：
+
+1. **在職段優先抵扣（若有工作段）**：
+   * 工作段呼叫 `calculate(...)` 時，以在職所得稅 `baseIncomeTax` 封頂抵扣，計算出在職抵扣額：
+     `usedByWorking = Math.min(workingBaseTax, kakuteiDeduction);`
+   * `calculate(...)` 內部將 `usedByWorking` 寫入 `context.getPreviousYearTaxRecord().pendingKakuteiShinkokuRefund()`。
+   * （若為 191-03 之過渡年，Pipeline 隨後計算 YTD 差額並記入同一個 record 的 `pendingWorkingSegmentTaxAdjustment()`）。
+
+2. **退休段餘額接力抵扣（`calculatePensionTax` 內部）**：
+   * 當執行到 `calculatePensionTax(context, pensionIncome, age, mortgageResult)` 時：
+   * 引擎檢查 `context.getPreviousYearTaxRecord()`：
+     ```java
+     PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
+     long usedByWorking = (prevTax != null) ? prevTax.pendingKakuteiShinkokuRefund() : 0L;
+     long remainingKakuteiDeduction = Math.max(0, kakuteiDeduction - usedByWorking);
+     ```
+   * **年金所得稅接力封頂**：
+     `long usedByPension = Math.min(baseIncomeTax, remainingKakuteiDeduction);`
+   * **總退稅款為雙段之合（不超過兩段所得稅總和）**：
+     `long totalRefund = usedByWorking + usedByPension;`
+   * **未用完餘額結轉住民稅**：
+     `long remainingDeduction = remainingKakuteiDeduction - usedByPension;`
+     若 `remainingDeduction > 0`，依限額策略計算結轉上限 `unusedForInhabitantTax`。
+   * **原子打包更新 `PreviousYearTaxRecord`**：
+     完整保留在職段已記入的薪資資料與過渡調整款，杜絕覆蓋：
+     ```java
+     long grossSalary = (prevTax != null) ? prevTax.grossSalary() : 0L;
+     long workingTaxable = (prevTax != null) ? prevTax.taxableIncomeForIncomeTax() : 0L;
+     long workingBaseTax = (prevTax != null) ? prevTax.baseIncomeTax() : 0L;
+     long pendingAdjustment = (prevTax != null) ? prevTax.pendingWorkingSegmentTaxAdjustment() : 0L;
+
+     context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+             grossSalary,
+             workingTaxable + taxableIncomeForIncomeTax,
+             workingBaseTax + baseIncomeTax,
+             totalRefund,
+             unusedForInhabitantTax,
+             pendingAdjustment));
+     ```
+
+#### (3) 兩種年度情境的天然統一性（零特例分支）
+上述邏輯在兩種退休情境下具備 100% 的通用性與自洽性，**完全不需要寫任何 `if (isRelay)` 特例判斷**：
+* **情境 2：整年退休年（Age > TargetRetirementAge 或整年無在職）**：
+  * 在每年年初，Pipeline 的春季退稅撥款已將 `pendingKakuteiShinkokuRefund` 撥款並歸零（`0L`）。
+  * 因此當 `calculatePensionTax` 執行時，讀取到的 `usedByWorking` 天然就是 `0L`！
+  * `remainingKakuteiDeduction` 天然等於完整的 `kakuteiDeduction`。
+  * `totalRefund = 0L + usedByPension = usedByPension`。
+  * `grossSalary`、`workingTaxable` 等天然是 0。
+* **情境 3：退休分流過渡年（Age == TargetRetirementAge，有在職段 + 退休段）**：
+  * 在職段先執行，`usedByWorking` 記錄了在職段扣抵掉的金額。
+  * 年金段後執行，自動讀取 `usedByWorking`，接力扣抵剩餘額度，並將雙段退稅合併為 `totalRefund`。
+
+#### (4) 領域架構約束與反模式（Anti-Patterns）
+1. ❌ **嚴禁增加第 5 個旗標參數（如 `boolean isRelayFromWorkingSegment`）**：
+   簽名必須保持純粹的 4 個參數。是否處於接力狀態，完全由 `context` 內的 `pendingKakuteiShinkokuRefund()` 自然體現，方法不需要額外的布林旗標。
+2. ❌ **嚴禁在 Pipeline 外層手動計算剩餘額度或封頂**：
+   稅額扣抵與跨段結算一律封裝在 `ActiveTaxCalculationEngine` 內部，Pipeline 僅負責單純調度傳入 `mortgageResult`。
+3. ❌ **嚴禁粗暴覆蓋 `PreviousYearTaxRecord`**：
+   在年金段寫回 `PreviousYearTaxRecord` 時，必須將在職段已有的 `grossSalary`、`taxableIncome`、`baseIncomeTax` 與 191-03 的 `pendingWorkingSegmentTaxAdjustment` 一併傳承打包，絕不可直接丟棄。
+4. ❌ **嚴禁污染 `TaxCalculationResult`**：
+   當期損益物件維持 4 欄位，退稅款屬於跨年狀態，統一由 `PreviousYearTaxRecord` 處理。
+
+### 3. 關於「修改 3」住民稅結轉消費缺口之歸屬（對齊既有 Plan-21）
+
+* **範圍共識**：同意原作者於 L136-138 所述，退休段消費住民稅結轉額不在本次 191-04 規格核心範圍內，不作為阻擋項。
+* **對齊既有 Plan-21 列管（無需重複立案）**：
+  經查證，該議題本質屬於「住民稅跨年時差與前年所得課稅機制」。在 Issue 137 / 142 規格規劃時，即已刻意於 [`PreviousYearTaxRecord`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java#L28) 保留 `unusedMortgageDeductionForInhabitantTax` 與所得資訊，並已於 [`Plan-21（模擬引擎月級別時間精度）`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/planned/Plan-21-inhabitant-tax-prior-year-income-engine.md) 完整立案列管（亦呼應 [`question-191.md:62`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/question-191.md#L62) 之共識）。
+  因此，**此缺口直接由既有的 Plan-21 在未來評估雙引擎架構時統籌處理**，無需另開新 Issue 或重複造輪子。本次 191-04 專注落實所得稅確定申告退稅即可。
+
+### 4. 關於退休年房貸分類（年末調整 vs 確定申告）之商務定案
+
+* **背景與商務考量**：
+  1. **企業作業時程差異龐大**：各企業年末調整作業時程不一（11月至隔年1月皆有），模擬器為年單位模型，無需亦不應臆測個別公司內規。
+  2. **法定高所得排除事實**：依日本所得稅法第 190 條規定，年收入超過 2,000 萬日圓者依法強制排除於年末調整之外，企業 HR 系統直接終止年末調整，法定一律要求個人自行申報。
+  3. **確定申告為法定最終基準（Baseline）**：
+     納稅義務人依法本即可透過確定申告（還付申告）全額行使房貸抵稅權益。
+* **商務定案**：
+  「基本原則只需保持該繳的稅有繳、該退的扣除有完整退還即可」。
+  維持 191-04 現有規格：
+  ```java
+  boolean noYearEndAdjustmentAvailable = isOccupancyYear || context.isRetired();
+  ```
+  凡處於退休狀態（`context.isRetired()`），房貸控除統一歸入 `kakuteiDeduction` 走確定申告退稅管道，資產實質等價，且程式碼邏輯最為精簡穩健。
+
+---
+
+## 實作完成狀態報告（2026-11-04）
+
+本規格所有設計均已實作完成並補齊單元測試：
+
+### 1. 修改清單與程式碼對齊
+1. **[`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L355-L364)**：
+   * 加入 `boolean noYearEndAdjustmentAvailable = isOccupancyYear || context.isRetired();`。
+   * 退休狀態下第二年起房貸控除額自動路由至 `kakuteiDeduction`。
+2. **[`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L342-L425)**：
+   * 依零死碼原則直接修改 `calculatePensionTax(context, pensionIncome, age, mortgageResult)` 簽名為 4 參數，徹底移除舊簽名。
+   * 內部自動完成：
+     * 退休雜所得門檻檢核（`maxTotalIncomeForMortgageDeduction`）。
+     * 退休分流過渡年（191-03 情境 3）接力抵扣：由 `context.getPreviousYearTaxRecord()` 讀取在職段抵扣額 `usedByWorking`，以年金 `baseIncomeTax` 接力扣抵未用完餘額。
+     * 未用完房貸控除額依上限結轉住民稅（`unusedMortgageDeductionForInhabitantTax`）。
+     * 完整原子打包跨年稅務紀錄至 `context.setPreviousYearTaxRecord`，保留在職段薪資數據與過渡調整款 `pendingWorkingSegmentTaxAdjustment`。
+3. **[`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L322)**：
+   * 在呼叫 `calculatePensionTax` 時直接傳入 `mortgageResult`。
+4. **測試覆蓋**：
+   * **[`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java#L175)**：更新既有測試為傳入 4 參數。
+   * **[`MortgageCalculatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java#L453-L495)**：新增 `testSimulateMortgagesForYear_SecondEligibleYear_Retired_RoutesToKakuteiDeduction`。
+   * **[`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java#L635-L738)**：
+     * 新增 `testQuestion191_04_postRetirementYear_mortgageDeductionPackagesKakuteiRefund_andDisbursesNextYear`（退休整年退稅封頂與隔年春季撥款）。
+     * 新增 `testQuestion191_04_splitRetirementYear_mortgageDeductionRelaysAcrossWorkingAndPensionSegments`（退休過渡分流年在職段與年金段跨段接力抵扣）。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT                                                                 10012  11.98G   3.23    15:31:08  