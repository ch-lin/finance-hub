# Fixed 93：房貸減稅轉扣住民稅的上限，漏了「跟課稅所得比例取小」這一半規則，只用固定金額（原question-07）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`（第480-486行：算出這年適用的「固定金額上限」`currentLine`／`maxInhabitantTaxDeductionThisYear`的地方；第467行：`refDate`，也就是各筆房貸各自的入住年份）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java`（第206-211行：實際把固定金額上限套用到住民稅扣抵金額上的地方；第176-178行：`taxableIncomeForIncomeTax`，即所得稅課稅所得金額，本問題建議修正要用到的計算基礎已經在這裡算出來了）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java`（第26-29行：把固定金額上限從`MortgageCalculator`傳到`ActiveTaxCalculationEngine`用的資料結構）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`（第181-189行：確認`MortgageCalculator`的計算會先跑完，才把結果傳給`ActiveTaxCalculationEngine.calculate()`）

## 現狀
使用者的房貸稅額扣抵，如果扣完所得稅還有剩餘額度沒用完，日本制度允許把剩下的餘額拿去扣住民稅，但住民稅這邊的扣抵有上限。目前程式碼裡，這個上限只用了一個固定金額：

```java
// MortgageCalculator.java 第480-486行
long currentLimit = 136_500L;
if (refDate != null && refDate.getYear() >= 2022) {
    currentLimit = 97_500L;
}
if (currentLimit > maxInhabitantTaxDeductionThisYear) {
    maxInhabitantTaxDeductionThisYear = currentLimit;
}
```

```java
// ActiveTaxCalculationEngine.java 第206-211行
long remainingMortgageDeduction = mortgageTaxDeduction - actualMortgageDeduction;
if (remainingMortgageDeduction > 0) {
    long inhabitantTaxDeduction = Math.min(inhabitantTax,
            Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction));
    inhabitantTax -= inhabitantTaxDeduction;
}
```

## 原因
經查證確認，日本稅法規定這個上限實際上要取「課稅總所得金額的一定比例」跟「固定金額」兩者中較小的一個：
- 2021年以前入住：課稅總所得金額的**7%**，最高不超過136,500元。
- 2022年以後入住：課稅總所得金額的**5%**，最高不超過97,500元。

目前程式碼只實作了固定金額這一半，完全沒有計算百分比。對所得比較低的使用者，這會導致系統高估可以扣抵的住民稅金額，讓模擬算出來的稅金比實際上該繳的更低。

**技術上為什麼不能直接在MortgageCalculator裡面把百分比一起算好**：百分比的計算基礎「課稅總所得金額」（`taxableIncomeForIncomeTax`）要等到`ActiveTaxCalculationEngine.calculate()`跑到第176-178行才會算出來，而`MortgageCalculator.simulateMortgagesForYear()`是在這之前就先執行完、把結果傳給`ActiveTaxCalculationEngine`的（呼叫順序見`YearlySimulationPipeline.java`第181-189行）。所以百分比沒辦法在`MortgageCalculator`那邊直接算出最終金額，只能把「這年該用7%還是5%」這個比率也一起傳過去，讓`ActiveTaxCalculationEngine`用它已經算好的`taxableIncomeForIncomeTax`去乘出百分比上限。

## 建議修正
在`MortgageYearlyResult`這個record裡新增一個欄位存「這年適用的百分比上限比率」（例如`maxInhabitantTaxDeductionRate`），`MortgageCalculator.java`在算`currentLimit`的同一段邏輯（第480-486行附近）依`refDate.getYear()`跟2022年的關係，一併決定這個比率該用7%還是5%；然後在`ActiveTaxCalculationEngine.calculate()`裡面，把第206-211行的`Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction)`，改成同時考慮「`taxableIncomeForIncomeTax` × 這個比率」算出來的百分比上限，三者（住民稅本身、剩餘扣抵額度、固定金額上限、百分比上限）一起取最小值。

補充一點：如果同時有多筆房貸，目前程式碼對固定金額上限的處理方式是取多筆中最大的那個（`MortgageCalculator.java`第484-486行的`if (currentLimit > maxInhabitantTaxDeductionThisYear)`），這是既有的簡化寫法，不是本問題要修的範圍；修這個問題時，百分比上限的計算也建議維持跟這個既有簡化寫法一致的精神（取多筆中最大的百分比上限），不需要另外處理多筆房貸各自獨立限制的複雜狀況。

## 最終架構決策 (Strategy Pattern)
原本的建議修正會導致 `ActiveTaxCalculationEngine` 參數膨脹。
經過討論，決定改採 Strategy Pattern，將計算邏輯封裝在 Mortgage 領域：
1. 新增 `InhabitantTaxLimitStrategy` 介面與對應年份的實作類別（如 `Pre2022InhabitantTaxLimitStrategy` 等）。
2. 由 `MortgageCalculator` 根據年份產生 Strategy，並找出最有利（上限最大）的策略。
3. 將選出的 Strategy 封裝進 `MortgageYearlyResult` 回傳。
4. `ActiveTaxCalculationEngine` 直接呼叫 `mortgageResult.inhabitantTaxLimitStrategy().calculateLimit(taxableIncome)` 即可，徹底解耦法規細節。

---

## 修正報告 (Fix Report)

### 1. 實作摘要
我們已將「住民稅可扣抵之房貸上限」的計算邏輯由原本的寫死數字，重構成基於 Strategy Pattern 的架構，徹底解耦法規計算與稅務引擎。

### 2. 核心變更範圍
- **新增 Strategy 介面與實作** (`.../core/calculator/strategy/mortgage/`)
  - **`InhabitantTaxLimitStrategy`**：定義 `calculateLimit(taxableIncomeForIncomeTax)` 與 `getFixedLimit()`。
  - **`Pre2022InhabitantTaxLimitStrategy`**：適用 7% 比例與 136,500 固定上限。
  - **`Post2022InhabitantTaxLimitStrategy`**：適用 5% 比例與 97,500 固定上限。
  - **`ZeroInhabitantTaxLimitStrategy`**：無房貸或是非適用對象的回退策略。
- **重構 `MortgageYearlyResult`**
  - 移除原有的 `maxInhabitantTaxDeductionThisYear` (long) 欄位。
  - 改為封裝選定出來的 `inhabitantTaxLimitStrategy` (InhabitantTaxLimitStrategy) 物件。
- **解耦計算流程**
  - **`MortgageCalculator`**：在計算迴圈中依年份動態生成對應的 Strategy，透過 `getFixedLimit()` 比較並保留對使用者最有利（上限最大）的策略，封裝進結果。
  - **`ActiveTaxCalculationEngine`**：參數簽章改為接收整個 `MortgageYearlyResult`。計算上限時直接呼叫 `mortgageResult.inhabitantTaxLimitStrategy().calculateLimit(taxableIncomeForIncomeTax)`。

### 3. Review 與後續待辦事項
- 程式碼架構與邏輯已修改完成。
- **注意**：因 `MortgageYearlyResult` 建構子參數與 `ActiveTaxCalculationEngine.calculate` 參數簽章已變更，原有針對這些類別的 Unit Tests (包含 Mock/Stub) 必定會發生編譯或執行錯誤，需要更新測試程式碼。


## Diff (不包含新增檔案)

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff     10022  12.78G   8.02    09:56:19  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index a9e7f018..2890ddb5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -31,6 +31,10 @@ import org.springframework.stereotype.Component;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils.MortgageCalculatorUtil;
 import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.InhabitantTaxLimitStrategy;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Pre2022InhabitantTaxLimitStrategy;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
@@ -290,7 +294,7 @@ public class MortgageCalculator {
 
         long totalMortgagePaymentsThisYear = 0L;
         long mortgageTaxDeductionThisYear = 0L;
-        long maxInhabitantTaxDeductionThisYear = 0L;
+        InhabitantTaxLimitStrategy bestStrategy = new ZeroInhabitantTaxLimitStrategy();
 
         for (int i = 0; i < allMortgages.size(); i++) {
             MortgageRecord m = allMortgages.get(i);
@@ -477,19 +481,18 @@ public class MortgageCalculator {
 
                     mortgageTaxDeductionThisYear += deduction;
 
-                    long currentLimit = 136_500L;
+                    InhabitantTaxLimitStrategy currentStrategy = new Pre2022InhabitantTaxLimitStrategy();
                     if (refDate != null && refDate.getYear() >= 2022) {
-                        currentLimit = 97_500L;
+                        currentStrategy = new Post2022InhabitantTaxLimitStrategy();
                     }
-                    if (currentLimit > maxInhabitantTaxDeductionThisYear) {
-                        maxInhabitantTaxDeductionThisYear = currentLimit;
+                    if (currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()) {
+                        bestStrategy = currentStrategy;
                     }
                 }
             }
         }
 
-        return new MortgageYearlyResult(totalMortgagePaymentsThisYear, mortgageTaxDeductionThisYear,
-                maxInhabitantTaxDeductionThisYear);
+        return new MortgageYearlyResult(totalMortgagePaymentsThisYear, mortgageTaxDeductionThisYear, bestStrategy);
     }
 
     private int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index f944f4aa..099b370e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -185,8 +185,7 @@ public class YearlySimulationPipeline {
                     context.getCurrentBonusPaymentCount(),
                     contributions.annualIdecoContribution(),
                     contributions.annualDcMatchingContribution(),
-                    mortgageResult.mortgageTaxDeductionThisYear(),
-                    mortgageResult.maxInhabitantTaxDeductionThisYear());
+                    mortgageResult);
             state.setEstimatedIncomeTax(taxResult.incomeTax());
             state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
             state.setEstimatedSocialInsurance(taxResult.socialInsurance());
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
index 90488534..6e51d2ec 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java
@@ -23,8 +23,10 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.simulation.core.result;
 
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.InhabitantTaxLimitStrategy;
+
 public record MortgageYearlyResult(
         long totalMortgagePaymentsThisYear,
         long mortgageTaxDeductionThisYear,
-        long maxInhabitantTaxDeductionThisYear) {
+        InhabitantTaxLimitStrategy inhabitantTaxLimitStrategy) {
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index 830cca05..ea8c76a2 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -31,6 +31,7 @@ import org.springframework.stereotype.Service;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
@@ -46,7 +47,8 @@ public class ActiveTaxCalculationEngine {
     public TaxCalculationResult calculate(SimulationContext context, long grossBaseSalary, long annualBonus,
             int bonusPaymentCount,
             long annualIdecoContribution, long annualDcMatchingContribution,
-            long mortgageTaxDeduction, long maxInhabitantTaxDeduction) {
+            MortgageYearlyResult mortgageResult) {
+        long mortgageTaxDeduction = mortgageResult.mortgageTaxDeductionThisYear();
 
         double healthInsuranceRate = context.getHealthInsuranceRate();
         double pensionRate = context.getPensionRate();
@@ -201,6 +203,8 @@ public class ActiveTaxCalculationEngine {
         // cap, e.g. 97,500 or 136,500 depending on rules)
         long remainingMortgageDeduction = mortgageTaxDeduction - actualMortgageDeduction;
         if (remainingMortgageDeduction > 0) {
+            long maxInhabitantTaxDeduction = mortgageResult.inhabitantTaxLimitStrategy()
+                    .calculateLimit(taxableIncomeForIncomeTax);
             long inhabitantTaxDeduction = Math.min(inhabitantTax,
                     Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction));
             inhabitantTax -= inhabitantTaxDeduction;
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index 76ea81bf..2a0f9e8b 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -309,7 +309,7 @@ class AssetSimulationEngineImplTest {
         // salary
         lenient()
                 .when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
-                        anyLong(), anyLong(), anyLong(), anyLong()))
+                        anyLong(), anyLong(), any()))
                 .thenAnswer(invocation -> {
                     long salary = invocation.getArgument(1);
                     return new TaxCalculationResult(0L, 0L, 0L, salary);
@@ -479,7 +479,7 @@ class AssetSimulationEngineImplTest {
 
         // Override global mock to return realistic taxes for this test only
         when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
-                anyLong(), anyLong(), anyLong(), anyLong()))
+                anyLong(), anyLong(), any()))
                 .thenAnswer(invocation -> {
                     long salary = invocation.getArgument(1);
                     long incomeTax = salary * 10 / 100;
@@ -777,7 +777,7 @@ class AssetSimulationEngineImplTest {
 
         // Stub the tax engine to return a non-zero tax result for the full year
         when(taxEngine.calculate(any(), anyLong(), anyLong(), anyInt(),
-                anyLong(), anyLong(), anyLong(), anyLong()))
+                anyLong(), anyLong(), any()))
                 .thenAnswer(invocation -> {
                     long salary = invocation.getArgument(1);
                     // Let's pretend taxes are roughly 20% of salary
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index 11b33035..d797fbd1 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -29,10 +29,12 @@ import java.util.Collections;
 import static org.assertj.core.api.Assertions.assertThat;
 import org.junit.jupiter.api.Test;
 
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxConfigDto;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 
 class ActiveTaxCalculationEngineTest {
@@ -64,7 +66,7 @@ class ActiveTaxCalculationEngineTest {
         // When
         TaxCalculationResult result = engine.calculate(
                 context, grossSalary, 0L, 0,
-                0, 0, 0, 0);
+                0, 0, new MortgageYearlyResult(0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
 
         // Math:
         // monthly = 666666
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                          10023  12.44G   8.65    09:56:30  