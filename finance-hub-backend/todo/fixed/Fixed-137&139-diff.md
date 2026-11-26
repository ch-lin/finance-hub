 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT  git --no-pager show 2bac66b8                                   10012  11.98G   3.23    15:31:08  
commit 2bac66b8073f74ccd37b0486e69a745530a502b0
Author: Che-Hung Lin <37680512+ch-lin@users.noreply.github.com>
Date:   Sun Oct 11 13:22:54 2026 +0900

    137 & 139

diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index 3ae2745f..21c106b8 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -144,7 +144,8 @@ public class MortgageCalculator {
         int year = context.getYear();
 
         long totalMortgagePaymentsThisYear = 0L;
-        long mortgageTaxDeductionThisYear = 0L;
+        long yearEndDeduction = 0L;
+        long kakuteiDeduction = 0L;
         long maxTotalIncomeForMortgageDeduction = 0L;
         InhabitantTaxLimitStrategy bestStrategy = new ZeroInhabitantTaxLimitStrategy();
 
@@ -350,18 +351,16 @@ public class MortgageCalculator {
                     }
 
                     LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
-                    boolean isFirstEligibleYear = (refDate != null && year == refDate.getYear());
-
-                    if (isFirstEligibleYear) {
-                        // For the first year, deduction requires Kakutei Shinkoku (tax return) next
-                        // year.
-                        // Do not apply it to this year's income tax via year-end adjustment.
-                        context.setPendingNextYearTaxRefund(context.getPendingNextYearTaxRefund() + deduction);
-                        deduction = 0L;
+                    boolean isOccupancyYear = (refDate != null && year == refDate.getYear());
+
+                    if (isOccupancyYear) {
+                        kakuteiDeduction = deduction;
+                        yearEndDeduction = 0L;
+                    } else {
+                        yearEndDeduction = deduction;
+                        kakuteiDeduction = 0L;
                     }
 
-                    mortgageTaxDeductionThisYear = deduction;
-
                     MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(refDate);
                     if (deductionStrategy != null) {
                         maxTotalIncomeForMortgageDeduction = deductionStrategy.getMaxEligibleTotalIncome();
@@ -380,7 +379,8 @@ public class MortgageCalculator {
 
         return new MortgageYearlyResult(
                 totalMortgagePaymentsThisYear,
-                mortgageTaxDeductionThisYear,
+                yearEndDeduction,
+                kakuteiDeduction,
                 maxTotalIncomeForMortgageDeduction,
                 bestStrategy);
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
index 4b91ae8f..169b0624 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
@@ -30,6 +30,7 @@ import java.util.List;
 import java.util.Map;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
@@ -92,7 +93,7 @@ public class SimulationContext implements Cloneable {
 
     private long companyStockBalance;
     private long currentLiquidCash;
-    private long pendingNextYearTaxRefund;
+    private PreviousYearTaxRecord previousYearTaxRecord;
 
     // Pension tracking
     private long accumulatedKousei;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
new file mode 100644
index 00000000..cceb5020
--- /dev/null
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
@@ -0,0 +1,36 @@
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
+package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;
+
+/**
+ * 通用跨年稅務結算紀錄。
+ * 記錄前一年度真實的所得與稅務抵扣數據，供次年度標準計算流程使用（支援退稅、住民稅轉扣及 Plan-21）。
+ */
+public record PreviousYearTaxRecord(
+        long grossSalary,
+        long taxableIncomeForIncomeTax,
+        long baseIncomeTax,
+        long pendingKakuteiShinkokuRefund,
+        long unusedMortgageDeductionForInhabitantTax
+) {}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 9bd5cd66..247b30f4 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -37,6 +37,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.Pen
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
@@ -74,11 +75,21 @@ public class YearlySimulationPipeline {
         // Restore NISA principal from previous year's liquidation
         restoreNisaPrincipal(context);
 
-        // Process pending tax refunds from Kakutei Shinkoku (e.g. 1st year mortgage
-        // deduction)
-        if (context.getPendingNextYearTaxRefund() > 0) {
-            context.setCurrentLiquidCash(context.getCurrentLiquidCash() + context.getPendingNextYearTaxRefund());
-            context.setPendingNextYearTaxRefund(0L);
+        // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and inhabitant tax rollover credit)
+        PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
+        if (prevTax != null && prevTax.pendingKakuteiShinkokuRefund() > 0) {
+            context.setCurrentLiquidCash(context.getCurrentLiquidCash() + prevTax.pendingKakuteiShinkokuRefund());
+            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) upon cash disbursement to prevent duplicate payout.
+            // DO NOT set context.setPreviousYearTaxRecord(null) here:
+            // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in context so that
+            // ActiveTaxCalculationEngine can consume them later in the annual pipeline for inhabitant tax
+            // deduction and Plan-21 prior-year income assessment.
+            context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                    prevTax.grossSalary(),
+                    prevTax.taxableIncomeForIncomeTax(),
+                    prevTax.baseIncomeTax(),
+                    0L, // Refund disbursed; zeroed to prevent double redemption
+                    prevTax.unusedMortgageDeductionForInhabitantTax()));
         }
 
         int monthsToAdd = 12;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
index a31dcee2..72ced38e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
@@ -27,7 +27,8 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.str
 
 public record MortgageYearlyResult(
         long totalMortgagePaymentsThisYear,
-        long mortgageTaxDeductionThisYear,
+        long yearEndDeduction,
+        long kakuteiDeduction,
         long maxTotalIncomeForMortgageDeduction,
         InhabitantTaxLimitStrategy inhabitantTaxLimitStrategy) {
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index 4829d697..04c902d2 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -35,6 +35,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.str
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
@@ -72,7 +73,8 @@ public class ActiveTaxCalculationEngine {
             int bonusPaymentCount,
             long annualIdecoContribution, long annualDcMatchingContribution,
             MortgageYearlyResult mortgageResult) {
-        long mortgageTaxDeduction = mortgageResult.mortgageTaxDeductionThisYear();
+        long yearEndDeduction = mortgageResult.yearEndDeduction();
+        long kakuteiDeduction = mortgageResult.kakuteiDeduction();
 
         double healthInsuranceRate = context.getHealthInsuranceRate();
         double pensionRate = context.getPensionRate();
@@ -217,12 +219,16 @@ public class ActiveTaxCalculationEngine {
         // mortgage tax deduction is strictly disqualified (reduced to 0) if the taxpayer's
         // total employment income exceeds the statutory threshold for the occupancy year
         // (e.g., 20,000,000 JPY for 2022 onwards, 30,000,000 JPY prior to 2022).
-        if (mortgageTaxDeduction > 0 && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
-            mortgageTaxDeduction = 0L;
+        if ((yearEndDeduction > 0 || kakuteiDeduction > 0)
+                && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
+            yearEndDeduction = 0L;
+            kakuteiDeduction = 0L;
         }
 
-        long actualMortgageDeduction = Math.min(baseIncomeTax, mortgageTaxDeduction);
-        baseIncomeTax -= actualMortgageDeduction;
+        long actualYearEndDeduction = Math.min(baseIncomeTax, yearEndDeduction);
+        baseIncomeTax -= actualYearEndDeduction;
+
+        long actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction);
 
         // Reconstruction special income tax (2.1% of income tax)
         long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);
@@ -231,17 +237,30 @@ public class ActiveTaxCalculationEngine {
         long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
                 .calculateInhabitantTax(taxableIncomeForInhabitantTax);
 
-        // If there's leftover mortgage deduction, it can offset inhabitant tax (up to a
-        // cap, e.g. 97,500 or 136,500 depending on rules)
-        long remainingMortgageDeduction = mortgageTaxDeduction - actualMortgageDeduction;
+        PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
+        if (prevTax != null && prevTax.unusedMortgageDeductionForInhabitantTax() > 0) {
+            long inhabitantTaxCredit = Math.min(inhabitantTax, prevTax.unusedMortgageDeductionForInhabitantTax());
+            inhabitantTax -= inhabitantTaxCredit;
+        }
+
+        long totalDeduction = yearEndDeduction + kakuteiDeduction;
+        long totalUsed = actualYearEndDeduction + actualKakuteiRefund;
+        long remainingMortgageDeduction = totalDeduction - totalUsed;
+        long unusedForInhabitantTax = 0L;
         if (remainingMortgageDeduction > 0) {
             long maxInhabitantTaxDeduction = mortgageResult.inhabitantTaxLimitStrategy()
                     .calculateLimit(taxableIncomeForIncomeTax);
-            long inhabitantTaxDeduction = Math.min(inhabitantTax,
-                    Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction));
-            inhabitantTax -= inhabitantTaxDeduction;
+            unusedForInhabitantTax = Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction);
         }
 
+        // Package cross-year tax record for the next year
+        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                grossTotalSalary,
+                taxableIncomeForIncomeTax,
+                baseIncomeTax,
+                actualKakuteiRefund,
+                unusedForInhabitantTax));
+
         // 7. Net Income
         long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
index 20612790..e1cf08ae 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
@@ -44,7 +44,6 @@ class MortgageCalculatorTest {
         // Arrange
         SimulationContext context = new SimulationContext();
         context.setYear(2026);
-        context.setPendingNextYearTaxRefund(0L);
 
         // Current balance 10,000,000, 0.7% rate -> deduction 70,000
         MortgageRecord m = new MortgageRecord(
@@ -88,16 +87,9 @@ class MortgageCalculatorTest {
         MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);
 
         // Assert
-        // In the first year (2026 == occupancy year), the deduction for THIS year
-        // should be 0
-        assertEquals(0L, result.mortgageTaxDeductionThisYear());
-
-        // The actual calculated deduction (balance * 0.007) should be deferred to
-        // pendingNextYearTaxRefund
-        // Balance after 12 payments of 30000 is ~9.7m, 0.7% of that is around 67000.
-        // We just assert that it's greater than 0
-        long deferredDeduction = context.getPendingNextYearTaxRefund();
-        assertEquals(true, deferredDeduction > 0);
+        // In the first year (2026 == occupancy year), the deduction goes to kakuteiDeduction channel
+        assertEquals(0L, result.yearEndDeduction());
+        assertTrue(result.kakuteiDeduction() > 0);
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
     }
 
@@ -106,7 +98,6 @@ class MortgageCalculatorTest {
         // Arrange
         SimulationContext context = new SimulationContext();
         context.setYear(2027);
-        context.setPendingNextYearTaxRefund(0L);
 
         // Occupancy in 2026, so 2027 is the second year
         MortgageRecord m = new MortgageRecord(
@@ -150,8 +141,9 @@ class MortgageCalculatorTest {
         MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);
 
         // Assert
-        // In the second year (2027 > 2026), the deduction for THIS year should be > 0
-        assertEquals(true, result.mortgageTaxDeductionThisYear() > 0);
+        // In the second year (2027 > 2026), the deduction for THIS year should be in yearEndDeduction channel
+        assertTrue(result.yearEndDeduction() > 0);
+        assertEquals(0L, result.kakuteiDeduction());
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
     }
 
@@ -160,7 +152,6 @@ class MortgageCalculatorTest {
         // Arrange
         SimulationContext context = new SimulationContext();
         context.setYear(2026);
-        context.setPendingNextYearTaxRefund(0L);
 
         MortgageRecord m = new MortgageRecord(
                 "Loan 1",
@@ -220,7 +211,6 @@ class MortgageCalculatorTest {
         // Arrange
         SimulationContext context = new SimulationContext();
         context.setYear(2026);
-        context.setPendingNextYearTaxRefund(0L);
 
         MortgageRecord m = new MortgageRecord(
                 "Loan 1",
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
new file mode 100644
index 00000000..eb1e0c54
--- /dev/null
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -0,0 +1,344 @@
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
+package ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline;
+
+import static org.assertj.core.api.Assertions.assertThat;
+import static org.mockito.ArgumentMatchers.any;
+import static org.mockito.ArgumentMatchers.anyInt;
+import static org.mockito.ArgumentMatchers.anyLong;
+import static org.mockito.Mockito.lenient;
+import static org.mockito.Mockito.when;
+
+import java.math.BigDecimal;
+import java.time.YearMonth;
+import java.util.Collections;
+
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.extension.ExtendWith;
+import org.mockito.Mock;
+import org.mockito.junit.jupiter.MockitoExtension;
+
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.DependentDeductionEngine;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.SpouseDeductionEngine;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
+import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
+
+@ExtendWith(MockitoExtension.class)
+class YearlySimulationPipelineTest {
+
+    @Mock
+    private IncomeExpenseCalculator incomeExpenseCalculator;
+
+    @Mock
+    private MortgageCalculator mortgageSimulationService;
+
+    @Mock
+    private TaxAdvantagedAccountProjector taxAdvantagedAccountProjector;
+
+    private YearlySimulationPipeline pipeline;
+
+    @BeforeEach
+    void setUp() {
+        SpouseDeductionEngine spouseEngine = new SpouseDeductionEngine();
+        DependentDeductionEngine dependentEngine = new DependentDeductionEngine();
+        BasicDeductionStrategyFactory basicDeductionStrategyFactory = new BasicDeductionStrategyFactory();
+        RetirementTaxStrategyFactory retirementTaxStrategyFactory = new RetirementTaxStrategyFactory();
+        EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory = new EmploymentIncomeDeductionStrategyFactory();
+        IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory = new IncomeTaxRateStrategyFactory();
+        InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory = new InhabitantTaxRateStrategyFactory();
+        ActiveTaxCalculationEngine taxEngine = new ActiveTaxCalculationEngine(
+                spouseEngine, dependentEngine, basicDeductionStrategyFactory, retirementTaxStrategyFactory,
+                employmentIncomeDeductionStrategyFactory, incomeTaxRateStrategyFactory,
+                inhabitantTaxRateStrategyFactory);
+
+        PensionCalculator pensionCalculator = new PensionCalculator();
+        CashflowLiquidationCalculator liquidationSimulationService = new CashflowLiquidationCalculator();
+        YearEndAdjustmentEngine yearEndAdjustmentEngine = new YearEndAdjustmentEngine();
+
+        pipeline = new YearlySimulationPipeline(
+                incomeExpenseCalculator,
+                mortgageSimulationService,
+                liquidationSimulationService,
+                pensionCalculator,
+                taxAdvantagedAccountProjector,
+                taxEngine,
+                yearEndAdjustmentEngine);
+
+        lenient().when(taxAdvantagedAccountProjector.processAccountsForYear(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
+                .thenReturn(new InvestmentContributions(0L, 0L, 0L, 0L));
+        lenient().when(incomeExpenseCalculator.calculateExpensesForYear(any(), any(), anyInt(), anyInt()))
+                .thenReturn(0L);
+    }
+
+    private SimulationContext createContext(int year, int age, long salary, long initialCash) {
+        SimulationContext context = new SimulationContext();
+        context.setCurrentYear(year);
+        context.setYear(year);
+        context.setCurrentAge(age);
+        context.setAge(age);
+        context.setCurrentAnnualSalary(salary);
+        context.setCurrentLiquidCash(initialCash);
+        context.setProfile(new UserProfile("test", null, null, null));
+        context.setPensionConfig(new PensionConfigDto(
+                816_000L,
+                480L,
+                new BigDecimal("5.481"),
+                680_000L,
+                1_500_000L));
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+        return context;
+    }
+
+    /**
+     * Scenario A: Standard full deduction across multiple years.
+     * - Year 1 (Occupancy): 210,000 JPY deduction routed via Kakutei Shinkoku; actual paid income tax > 250,000 JPY.
+     * - Year 2: Spring cash strictly disburses 210,000 JPY refund; Year 2 Year-End Adjustment applies 210,000 JPY deduction.
+     * - Year 3: Spring cash refund is 0 JPY (prevents duplicate refund).
+     */
+    @Test
+    void testScenarioA_standardFullDeduction_multiYearCycle() {
+        int initialAge = 35;
+        long salary = 8_000_000L;
+        SimulationContext context = createContext(2026, initialAge, salary, 1_000_000L);
+        YearMonth nowMonth = YearMonth.of(2026, 1);
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(salary);
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
+
+        // --- Year 1 (2026): Occupancy year ---
+        // Mortgage deduction is 210,000 JPY, routed via kakuteiDeduction channel
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);
+
+        assertThat(resultYear1.getYear()).isEqualTo(2026);
+        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear1).isNotNull();
+        // With 8M salary, baseIncomeTax is > 250,000 JPY, so 210,000 JPY is fully refundable
+        assertThat(recordAfterYear1.baseIncomeTax()).isGreaterThan(250_000L);
+        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(210_000L);
+        assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
+
+        long cashAtEndOfYear1 = context.getCurrentLiquidCash();
+
+        // --- Year 2 (2027): Second year ---
+        // Mortgage deduction is 210,000 JPY, routed via yearEndDeduction channel
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 210_000L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
+
+        assertThat(resultYear2.getYear()).isEqualTo(2027);
+
+        // Year-end adjustment directly reduced Year 2 estimated income tax by 210,000 JPY (plus 2.1% reconstruction tax)
+        long expectedIncomeTaxReduction = 210_000L + (long) (210_000L * 0.021);
+        assertThat(resultYear1.getEstimatedIncomeTax() - resultYear2.getEstimatedIncomeTax())
+                .isEqualTo(expectedIncomeTaxReduction);
+
+        // Spring cash received the 210,000 JPY refund from Year 1
+        // Net savings of Year 2 is higher by expectedIncomeTaxReduction compared to Year 1, plus 210,000 JPY spring cash refund
+        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
+        long expectedCashDelta = resultYear2.getNetSavings() + 210_000L;
+        assertThat(cashDelta).isEqualTo(expectedCashDelta);
+
+        // Record for Year 3: pendingKakuteiShinkokuRefund must be 0
+        PreviousYearTaxRecord recordAfterYear2 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear2).isNotNull();
+        assertThat(recordAfterYear2.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
+
+        long cashAtEndOfYear2 = context.getCurrentLiquidCash();
+
+        // --- Year 3 (2028): Third year ---
+        AssetSimulationYearlyResult resultYear3 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 37);
+
+        assertThat(resultYear3.getYear()).isEqualTo(2028);
+
+        // Verify NO duplicate spring cash refund in Year 3:
+        // Cash change from Year 2 to Year 3 is purely netSavings (0 refund added)
+        long cashDeltaYear3 = resultYear3.getProjectedLiquidCash() - cashAtEndOfYear2;
+        assertThat(cashDeltaYear3).isEqualTo(resultYear3.getNetSavings());
+    }
+
+    /**
+     * Scenario B: Low-income with inhabitant tax rollover (Core Issue 137 fix verification).
+     * - Year 1: Mortgage deduction is 210,000 JPY, but baseIncomeTax is capped (~103,200 JPY < 210,000 JPY).
+     * - Year 2: Spring cash increases by the capped paid tax (NOT 210,000 JPY, solves Issue 137 Problem 2).
+     * - Year 2: Inhabitant tax deducts exactly 97,500 JPY (statutory rollover cap, solves Issue 137 Problem 1).
+     */
+    @Test
+    void testScenarioB_lowIncomeWithInhabitantRollover_cappedRefundAndCredit() {
+        int initialAge = 35;
+        long salary = 4_500_000L;
+        SimulationContext context = createContext(2026, initialAge, salary, 1_000_000L);
+        YearMonth nowMonth = YearMonth.of(2026, 1);
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(salary);
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
+
+        // --- Year 1 (2026): First year Kakutei Shinkoku ---
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);
+
+        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear1).isNotNull();
+
+        // Income tax is capped below 210,000 JPY
+        long cappedRefund = recordAfterYear1.pendingKakuteiShinkokuRefund();
+        assertThat(cappedRefund).isEqualTo(recordAfterYear1.baseIncomeTax());
+        assertThat(cappedRefund).isLessThan(210_000L);
+
+        // Leftover mortgage deduction rollover is capped at statutory inhabitant tax cap (<= 97,500 JPY)
+        long rollover = recordAfterYear1.unusedMortgageDeductionForInhabitantTax();
+        assertThat(rollover).isGreaterThan(0L);
+        assertThat(rollover).isLessThanOrEqualTo(97_500L);
+
+        long cashAtEndOfYear1 = context.getCurrentLiquidCash();
+
+        // --- Year 2 (2027): Mortgage deduction ends (0L) to isolate rollover effect ---
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
+
+        // Problem 1 Verification: Year 2 inhabitant tax is credited by exactly the rollover amount
+        assertThat(resultYear1.getEstimatedInhabitantTax() - resultYear2.getEstimatedInhabitantTax())
+                .isEqualTo(rollover);
+
+        // Problem 2 Verification: Year 2 spring cash refund received EXACTLY the capped amount, NOT 210,000 JPY
+        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
+        long expectedCashDelta = resultYear2.getNetSavings() + cappedRefund;
+        assertThat(cashDelta).isEqualTo(expectedCashDelta);
+    }
+
+    /**
+     * Scenario B (Direct): Verifies the exact statutory illustration:
+     * With 50,000 JPY paid income tax and 97,500 JPY inhabitant tax rollover from previous year,
+     * Year 2 spring cash increases by exactly 50,000 JPY, and inhabitant tax reduces by exactly 97,500 JPY.
+     */
+    @Test
+    void testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500() {
+        int initialAge = 35;
+        long salary = 4_500_000L;
+        long initialCash = 1_000_000L;
+        SimulationContext context = createContext(2027, 36, salary, initialCash);
+        YearMonth nowMonth = YearMonth.of(2027, 1);
+
+        // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY inhabitant rollover
+        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
+                salary, 2_000_000L, 50_000L, 50_000L, 97_500L));
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
+
+        // Inhabitant tax credited by 97,500 JPY (206,600 uncredited base - 97,500 = 109,100 JPY)
+        assertThat(result.getEstimatedInhabitantTax()).isEqualTo(109_100L);
+
+        // Spring cash refunded exactly 50,000 JPY
+        long cashDelta = result.getProjectedLiquidCash() - initialCash;
+        long expectedCashDelta = result.getNetSavings() + 50_000L;
+        assertThat(cashDelta).isEqualTo(expectedCashDelta);
+    }
+
+    /**
+     * Scenario C: High income disqualification phase-out (Issue 139 verification).
+     * - Year 1: Gross salary is 25,000,000 JPY (employment income > 20,000,000 JPY statutory threshold).
+     * - Year 2: Spring cash refund is 0 JPY; Inhabitant tax credit is 0 JPY.
+     */
+    @Test
+    void testScenarioC_highIncomeDisqualification_phaseOut() {
+        int initialAge = 35;
+        long highSalary = 25_000_000L;
+        SimulationContext context = createContext(2026, initialAge, highSalary, 1_000_000L);
+        YearMonth nowMonth = YearMonth.of(2026, 1);
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(highSalary);
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(highSalary);
+
+        // High earner enters mortgage in year 1
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);
+
+        assertThat(resultYear1.getYear()).isEqualTo(2026);
+
+        // Phase-out check in Year 1: both refund and inhabitant rollover must be strictly 0
+        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
+        assertThat(recordAfterYear1).isNotNull();
+        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
+        assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
+
+        long cashAtEndOfYear1 = context.getCurrentLiquidCash();
+
+        // --- Year 2 (2027) ---
+        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
+
+        // Zero spring cash refund received in Year 2 (cash change is strictly net savings)
+        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
+        assertThat(cashDelta).isEqualTo(resultYear2.getNetSavings());
+    }
+}
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index 58da5e04..14c4c187 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -26,7 +26,9 @@ package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;
 import static org.assertj.core.api.Assertions.assertThat;
 import org.junit.jupiter.api.Test;
 
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
@@ -69,7 +71,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
         // monthly = 666666
@@ -112,7 +114,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
         // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 670,000 (basic deduction)
@@ -146,7 +148,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
         // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 620,000 (basic deduction)
@@ -213,7 +215,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Social insurance:
         // monthlySalary = 2,166,666 -> healthBase = 1,390,000, pensionBase = 680,000
@@ -253,7 +255,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Social insurance:
         // health = 834,000, pension = 746,640, emp = 27,000,000 * 0.006 = 162,000
@@ -292,12 +294,12 @@ class ActiveTaxCalculationEngineTest {
         // Control result without mortgage deduction
         TaxCalculationResult controlResult = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // Assert: 20,000,000 <= 20,000,000 -> eligible, deduction applied
         // Saving = 200,000 * 1.021 (reconstruction tax) = 204,200 JPY
@@ -328,12 +330,12 @@ class ActiveTaxCalculationEngineTest {
         // Control result without mortgage deduction
         TaxCalculationResult controlResult = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // Assert: 20,000,001 > 20,000,000 -> disqualified, mortgage deduction forced to 0
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
@@ -363,12 +365,12 @@ class ActiveTaxCalculationEngineTest {
         // Control result without mortgage deduction
         TaxCalculationResult controlResult = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // Assert: 30,000,000 <= 30,000,000 -> eligible under pre-2022 rules, deduction applied
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax() - 204_200L);
@@ -398,12 +400,12 @@ class ActiveTaxCalculationEngineTest {
         // Control result without mortgage deduction
         TaxCalculationResult controlResult = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
 
         // Assert: 30,000,001 > 30,000,000 -> disqualified, mortgage deduction forced to 0
         assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
@@ -433,10 +435,10 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult control2021 = engine.calculate(
                 context2021, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
         TaxCalculationResult result2021 = engine.calculate(
                 context2021, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Under 2021 rules (cap = 30M): 23.05M <= 30M -> eligible!
         assertThat(result2021.incomeTax()).isEqualTo(control2021.incomeTax() - 204_200L);
@@ -452,10 +454,10 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult control2026 = engine.calculate(
                 context2026, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
         TaxCalculationResult result2026 = engine.calculate(
                 context2026, grossSalary, 0L, 0,
-                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
+                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Under 2026 rules (cap = 20M): 23.05M > 20M -> disqualified!
         assertThat(result2026.incomeTax()).isEqualTo(control2026.incomeTax());
@@ -558,7 +560,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2024 = engine.calculate(
                 context2024, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 620,000 = 1,180,000
         // Basic deduction 2024: 480,000 (income tax), 430,000 (inhabitant tax)
         // Taxable income tax = 1,180,000 - 480,000 = 700,000 -> 700,000 * 5% = 35,000 -> recon: 35,735
@@ -577,7 +579,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2025 = engine.calculate(
                 context2025, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 650,000 = 1,150,000
         // Basic deduction 2025 (Reiwa 7, income <= 1.32M): 950,000 (income tax)
         // Taxable income tax = 1,150,000 - 950,000 = 200,000 -> 200,000 * 5% = 10,000 -> recon: 10,210
@@ -596,7 +598,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2026 = engine.calculate(
                 context2026, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 740,000 = 1,060,000
         // Basic deduction 2026 (Reiwa 8, income <= 4.89M): 1,040,000 (income tax)
         // Taxable income tax = 1,060,000 - 1,040,000 = 20,000 -> 20,000 * 5% = 1,000 -> recon: 1,021
@@ -615,7 +617,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2028 = engine.calculate(
                 context2028, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
         // Employment income = 1,800,000 - 690,000 = 1,110,000 (50k higher than 2026!)
         // Basic deduction 2028 (Reiwa 10, income <= 1.32M): 990,000 (income tax)
         // Taxable income tax = 1,110,000 - 990,000 = 120,000 -> 120,000 * 5% = 6,000 -> recon: 6,126 (5,105 higher than 2026!)
@@ -642,7 +644,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2014 = engine.calculate(
                 context2014, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Context 2015: Standard 2015+ era with 45% top bracket
         SimulationContext context2015 = new SimulationContext();
@@ -655,7 +657,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2015 = engine.calculate(
                 context2015, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Income tax for 2015 should be strictly higher than 2014 due to the 45% statutory bracket
         assertThat(res2015.incomeTax()).isGreaterThan(res2014.incomeTax());
@@ -679,7 +681,7 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2006 = engine.calculate(
                 context2006, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Context 2007: Post-reform flat 10% era
         SimulationContext context2007 = new SimulationContext();
@@ -692,10 +694,122 @@ class ActiveTaxCalculationEngineTest {
 
         TaxCalculationResult res2007 = engine.calculate(
                 context2007, grossSalary, 0L, 0, 0, 0,
-                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Under 2006 tiers, taxable income around 1.5M was taxed at 5%, whereas in 2007 it was taxed at flat 10%
         assertThat(res2007.inhabitantTax()).isGreaterThan(res2006.inhabitantTax());
     }
+
+    @Test
+    void calculate_firstYearMortgage_exceedsCap_recordRefundAndInhabitantCreditAreZero() {
+        // Issue 139: Gross salary 25,000,000 JPY (employment income > 20,000,000 JPY) in 2026
+        // Mortgage in occupancy year: kakuteiDeduction = 200,000 JPY, yearEndDeduction = 0L
+        long grossSalary = 25_000_000L;
+        long kakuteiDeduction = 200_000L;
+        long statutoryIncomeCap = 20_000_000L;
+
+        UserProfile profile = new UserProfile("test", null, null, null);
+        SimulationContext context = new SimulationContext();
+        context.setProfile(profile);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
+        context.setPensionConfig(pensionConfig);
+        context.setYear(2026);
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+
+        // When
+        TaxCalculationResult result = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+
+        // Assert: high income earner is disqualified in first year; packaged record must be 0 for refund and inhabitant rollover
+        assertThat(result).isNotNull();
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        assertThat(record).isNotNull();
+        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
+        assertThat(record.grossSalary()).isEqualTo(grossSalary);
+    }
+
+    @Test
+    void calculate_firstYearMortgage_underCap_withholdingPreserved_kakuteiRefundRecorded() {
+        // Gross salary 8,000,000 JPY in 2026 (below 20M cap)
+        // First year mortgage: yearEndDeduction = 0L, kakuteiDeduction = 200,000 JPY
+        long grossSalary = 8_000_000L;
+        long kakuteiDeduction = 200_000L;
+        long statutoryIncomeCap = 20_000_000L;
+
+        UserProfile profile = new UserProfile("test", null, null, null);
+        SimulationContext context = new SimulationContext();
+        context.setProfile(profile);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+        context.setPensionConfig(pensionConfig);
+        context.setYear(2026);
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+
+        // Control result without mortgage deduction
+        TaxCalculationResult controlResult = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));
+
+        // First year mortgage run
+        TaxCalculationResult result = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+
+        // In first year, withholding tax is preserved (current year income tax is NOT reduced)
+        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
+        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
+
+        // PreviousYearTaxRecord packages the Kakutei Shinkoku refund capped by base income tax
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        assertThat(record).isNotNull();
+        // Since baseIncomeTax (426,100) > kakuteiDeduction (200,000), refund is full 200,000
+        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(200_000L);
+        // All kakutei deduction was absorbed by income tax, so no leftover for inhabitant tax
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
+    }
+
+    @Test
+    void calculate_firstYearMortgage_lowIncome_cappedAtBaseIncomeTax_andRolloverToInhabitantTax() {
+        // Low income where baseIncomeTax < kakuteiDeduction
+        // Gross salary 3,000,000 JPY in 2026
+        // kakuteiDeduction = 200,000 JPY
+        long grossSalary = 3_000_000L;
+        long kakuteiDeduction = 200_000L;
+        long statutoryIncomeCap = 20_000_000L;
+
+        UserProfile profile = new UserProfile("test", null, null, null);
+        SimulationContext context = new SimulationContext();
+        context.setProfile(profile);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+        context.setPensionConfig(pensionConfig);
+        context.setYear(2026);
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+
+        TaxCalculationResult result = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap, new Post2022InhabitantTaxLimitStrategy()));
+
+        assertThat(result).isNotNull();
+        assertThat(result.incomeTax()).isGreaterThan(0L);
+
+        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
+        assertThat(record).isNotNull();
+        // Refund must be capped at baseIncomeTax
+        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(record.baseIncomeTax());
+        assertThat(record.pendingKakuteiShinkokuRefund()).isLessThan(kakuteiDeduction);
+
+        // Remaining deduction rolls over to inhabitant tax, capped at statutory inhabitant tax limit (<= 97,500)
+        long remaining = kakuteiDeduction - record.pendingKakuteiShinkokuRefund();
+        long expectedRollover = Math.min(remaining, Math.min(97_500L, (long) (record.taxableIncomeForIncomeTax() * 0.05)));
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(expectedRollover);
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isGreaterThan(0L);
+    }
 }
 
diff --git a/finance-hub-backend/todo/pending fix/issue-137.md b/finance-hub-backend/todo/pending fix/issue-137.md
index 2a2f50f7..7983e3ef 100644
--- a/finance-hub-backend/todo/pending fix/issue-137.md
+++ b/finance-hub-backend/todo/pending fix/issue-137.md
@@ -438,3 +438,67 @@
 > 2. **邊界控制（聚焦核心問題，拒絕範疇蔓延）**：本 Issue 專注於根治 Fixed-45-01 遺留之「首年確定申告退稅卡實繳所得稅上限」與「次年住民稅轉扣被實質沒收」兩大核心問題。復興特別所得稅（2.1%）並非 Claude 與本 Issue 界定之業務缺陷，不納入本期改動，以防範 Scope Creep。
 > 3. **商務邏輯與代碼細節分離**：虛擬代碼範例中若有參數簽名差異，屬於實作與編譯層級之工程細節，以現行介面定義（如 `InhabitantTaxLimitStrategy.calculateLimit(taxableIncomeForIncomeTax)`）為準，不影響核心商務狀態機與金額通道之有效性。
 > 4. **既有舊房貸之時序與歷史邊界（Zero Historical Overhead）**：既有舊房貸於模擬起始年早已渡過入居首年，所得稅依法直接走公司年末調整（`yearEndDeduction`），完全沒有確定申告退稅款問題，亦無歷史退稅可言。在無前一年歷史源泉徵收票與薪資單之客觀邊界下，方案採用「跨年狀態機自然向前遞延」：模擬首年年末調整未抵完額度自然於次年住民稅抵扣，並一路自然順延至法定減稅年限屆滿之次年，在整個模擬生命週期中所享受之住民稅扣除總年數與總額度完全不變（分文不少），徹底免除無中生有推估歷史所得或強求使用者輸入歷史稅單之負擔，維持無 Flag 狀態機自然演進即為產品與工程之最佳解。
+
+---
+
+## 實作完成報告（Implementation & Verification Report）
+
+### 1. 執行概述
+本 Issue 所規劃之「跨年狀態傳遞機制（Cross-Year Tax State Machine）」與「純金額雙通道驅動架構（Amount-Driven Architecture）」已全數實作完成並驗證通過。
+徹底根治了 Fixed-45-01 遺留的四大技術債與業務缺陷：
+1. **[Issue 137 問題一] 住民稅轉扣被實質沒收**：首年所得稅未抵扣完之理論額度，精準封裝並於次年住民稅繳納時依租稅特別措置法法定限額（9.75 萬 / 13.65 萬円）如實折抵。
+2. **[Issue 137 問題二] 次年春季退稅盲目超發**：次年確定申告退稅款嚴格受限於首年實繳基準所得稅（`baseIncomeTax`），低所得者不再虛增流動性資產。
+3. **[Issue 139] 首年高所得排富穿透漏洞**：稅務引擎在當年度即依入居年份適用門檻（2,000 萬或 3,000 萬円）審查，超標者雙金額通道當場歸零，次年退稅與轉扣全數歸零。
+4. **雙重退稅與越權污染**：`MortgageCalculator` 完全移除對 `SimulationContext` 的直接越權寫入與 `deduction = 0L` 粗暴歸零；第 2 年起走年末調整，確定申告通道天然為 `0L`，跨年退稅金額自然歸零，杜絕雙重發放。
+
+---
+
+### 2. 改動檔案清單
+
+#### 新建檔案
+1. [`PreviousYearTaxRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)
+   - 定義不可變跨年稅務結算 Record：持有 `grossSalary`、`taxableIncomeForIncomeTax`、`baseIncomeTax`、`pendingKakuteiShinkokuRefund`、`unusedMortgageDeductionForInhabitantTax`。
+2. [`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java)
+   - 新建端到端跨年整合測試矩陣（涵蓋情境 A、情境 B、情境 B 法定圖解、情境 C 排富）。
+
+#### 修改檔案
+1. [`MortgageYearlyResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java)
+   - 重構為不可變雙金額通道（`yearEndDeduction`, `kakuteiDeduction`）。
+2. [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
+   - 移除 `setPendingNextYearTaxRefund` 與 `deduction = 0L` 污染；依不可變實體入居年份自動將理論額度分流至申報通道。
+3. [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
+   - 掛載 `PreviousYearTaxRecord previousYearTaxRecord`；徹底移除廢棄欄位 `pendingNextYearTaxRefund`。
+4. [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
+   - 實作所得超標即時排富閘門（兩通道歸零）。
+   - 純算術執行 `baseIncomeTax -= Math.min(baseIncomeTax, yearEndDeduction)`。
+   - 計算 `actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction)`。
+   - 自前年紀錄讀取 `unusedMortgageDeductionForInhabitantTax` 折抵當期住民稅。
+   - 當期未扣完額度算定法定上限後封裝入 `PreviousYearTaxRecord` 傳遞至次年（當期絕不扣當期）。
+5. [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
+   - 年度開頭自 `PreviousYearTaxRecord` 兌現春季退稅款至流動現金，並當場在 Record 中將該項歸零（防杜雙重發放）。
+6. [`MortgageCalculatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)
+   - 配合雙通道重構測試，清理已廢除的 `pendingNextYearTaxRefund` 呼叫。
+7. [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)
+   - 更新建構子參數，新增首年排富、源泉徵收維持、低所得限額轉扣等單元測試。
+
+---
+
+### 3. 測試驗收矩陣與成果
+
+| 測試案例 | 驗證項目 | 預期結果 |
+|---|---|---|
+| `testScenarioA_standardFullDeduction_multiYearCycle` | 常態足額跨年循環 | 第 2 年春季準確發放 21 萬；第 2 年年末調整正常扣除當期所得稅；第 3 年退稅精確為 0（杜絕雙重發放） |
+| `testScenarioB_lowIncomeWithInhabitantRollover_cappedRefundAndCredit` | 低所得跨年轉扣（Issue 137 核心） | 首年退稅精確卡在實繳所得稅上限（103,200 JPY < 210,000 JPY）；次年住民稅精確折抵法定上限 97,500 JPY |
+| `testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500` | 法定說明數據端到端驗證 | 前年 5 萬實繳稅與 9.75 萬轉扣：第 2 年春季現金增加 5 萬，住民稅減少 9.75 萬 |
+| `testScenarioC_highIncomeDisqualification_phaseOut` | 高所得排富穿透（Issue 139 核心） | 年薪 2,500 萬超標者，第 1 年記錄退稅與轉扣全為 0；第 2 年現金與住民稅無任何抵扣 |
+| `MortgageCalculatorTest` (全組) | 領域時序純金額通道分流 | 首年全入 `kakuteiDeduction`，次年全入 `yearEndDeduction`，零 Context 污染 |
+| `ActiveTaxCalculationEngineTest` (全組) | 稅務引擎排富與限額封裝 | 排富即時歸零、純算術扣抵、單向封裝跨年紀錄 |
+
+---
+
+### 4. 驗收標準達成核對
+- [x] **現金流高估清零**：在第 1 年實繳所得稅低於抵稅額時，退稅款精準受限，絕不超發。
+- [x] **住民稅權益復活與單向流動**：首年未抵完額度在次年夏季住民稅 100% 如實折抵（法定上限內）；當期絕不扣當期，次年絕不重複扣。
+- [x] **排富機制端到端貫通**：首年年收超標者次年退稅與抵扣全額歸零，無任何漏洞。
+- [x] **零雙重退稅**：第 2 年年末調整扣減後，第 3 年起春季退稅天然為 0。
+- [x] **狀態機閉環運作**：純金額通道驅動，全專案零 boolean flag、零 dirty flag、零 mutable 狀態洩漏，全流程自然平滑演進。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT                                                                 10013  12.00G   2.78    18:02:39  