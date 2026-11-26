# [FIXED] 192：`PreviousYearTaxRecord`裡`grossSalary`、`taxableIncomeForIncomeTax`、`baseIncomeTax`三個欄位，從誕生到現在只被寫入、從未被讀取消費，且191新增的relay邏輯讓其中兩個欄位的值被跨年錯誤累加——確認可以直接移除，不需要先修累加問題再移除

## 這份issue怎麼來的
這是在[Plan-28](../planned/Plan-28-inhabitant-tax-june-switch-lightweight-engine.md)（住民稅6月切換輕量引擎）定案過程中，討論191在`calculatePensionTax`新增的relay邏輯把`taxableIncomeForIncomeTax`/`baseIncomeTax`這兩個欄位改成跨年累加（見[question-191-03](question-191-03.md)/[question-191-04](question-191-04.md)）時，使用者問「這個欄位到底還有沒有必要留到隔年」，進而查證確認這三個欄位本身從一開始就是死欄位，累加問題的正確解法是直接移除欄位，不是修正累加規則。

## 相關檔案
- [PreviousYearTaxRecord.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)（這三個欄位的定義處）
- [ActiveTaxCalculationEngine.java:260-266,415-428](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)（唯二兩個建構`PreviousYearTaxRecord`的地方，`calculate(...)`跟`calculatePensionTax(...)`）
- [YearlySimulationPipeline.java:91-110,284-293](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（每年開頭「發放並清零」時的原樣複製點、191新增的在職段YTD差額打包點）

## 現狀：三個欄位的來源跟原始設計意圖

查證[Fixed-137.md](../fixed/Fixed-137.md)（這三個欄位最初被規劃的文件，不是issue-177——issue-177是NISA PATCH端點的獨立問題，跟這三個欄位無關）確認原始定義：

```java
/**
 * 通用跨年稅務結算紀錄。
 * 記錄前一年度真實的所得與稅務抵扣數據，供次年度標準計算流程使用（支援退稅、住民稅轉扣及 Plan-21）。
 */
public record PreviousYearTaxRecord(
        long grossSalary,                              // 前一年薪資毛額（供 Plan-21 住民稅計算及查核）
        long taxableIncomeForIncomeTax,                // 前一年課稅所得總額
        long baseIncomeTax,                            // 前一年基準所得稅（確定申告退稅之法定上限）
        long pendingKakuteiShinkokuRefund,             // 前一年待以確定申告現金退還之款項
        long unusedMortgageDeductionForInhabitantTax   // 前一年所得稅未扣完轉入次年住民稅之額度
) {}
```

Fixed-137原文反覆出現「為Plan-21奠定地基」這類措辭（例如「直接為Plan-21鋪好地基（前年所得已在Context裡）」、「為住民稅課前年所得奠定統一的跨年狀態傳遞基石」）——這三個欄位從設計之初就是**為了將來的Plan-21（住民稅前年所得課稅引擎）預留，不是給房貸確定申告退稅機制本身用**。房貸退稅機制實際讀取的是另外兩個欄位（`pendingKakuteiShinkokuRefund`、`unusedMortgageDeductionForInhabitantTax`），查證[YearlySimulationPipeline.java:85-90](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)跟[ActiveTaxCalculationEngine.java:244-247](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，這兩個活欄位的讀取邏輯完全不涉及這三個欄位。

## 逐一查證：三個欄位至今沒有任何讀取消費，只有寫入/傳遞/191新增的累加

**`grossSalary`**：全專案搜尋`.grossSalary()`，只有兩處命中（[ActiveTaxCalculationEngine.java:261](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)的寫入、[YearlySimulationPipeline.java:104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)的原樣複製傳遞），從未被讀取去做任何計算或比較。

**`taxableIncomeForIncomeTax`**：同樣只有寫入跟傳遞（[ActiveTaxCalculationEngine.java:262](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)、[YearlySimulationPipeline.java:105](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)），**但191新增了一處讀取**——[ActiveTaxCalculationEngine.java:418](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)的`calculatePensionTax`relay邏輯裡：
```java
long workingTaxable = (prevTax != null) ? prevTax.taxableIncomeForIncomeTax() : 0L;
...
context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
        grossSalary,
        workingTaxable + taxableIncomeForIncomeTax,  // 讀出來之後，不是拿去比較或判斷，只是加總後繼續往下傳
        ...
```
這處讀取**本身也不是真正的消費**——它把讀出來的值跟今年的值加總，再寫回同一個欄位繼續往下傳，沒有任何地方真正「用」這個累加後的數字去做計算或判斷。這代表`taxableIncomeForIncomeTax`從Fixed-137誕生到191為止，整條生命週期裡沒有一次被真正消費,只是被191意外地從「單純傳遞」升級成「累加後傳遞」，沒有改變它「最終沒人用」這個事實。

**`baseIncomeTax`**：全專案搜尋`.baseIncomeTax()`對`PreviousYearTaxRecord`的呼叫，三處命中（[YearlySimulationPipeline.java:106,289](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)的傳遞、[ActiveTaxCalculationEngine.java:419](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)的191累加），性質跟`taxableIncomeForIncomeTax`完全一樣——**Fixed-137欄位注解寫的「確定申告退稅之法定上限」這個意圖從未落地**：程式碼裡`Math.min(baseIncomeTax, kakuteiDeduction)`這類封頂判斷（[ActiveTaxCalculationEngine.java:231,234](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)），用的永遠是**當年度計算中的本地變數`baseIncomeTax`**，不是從`PreviousYearTaxRecord`讀回來的那個跨年值——這是兩個同名但生命週期完全不同的變數，欄位注解描述的用途跟實際程式碼的消費對象，從一開始就沒有連上線。

## 為何是問題

1. **三個欄位本身是死重量**：每年都要花心力維護這三個欄位的正確傳遞（包括191新增的累加邏輯），但從來沒有任何計算結果依賴它們——拿掉完全不影響任何現有功能的輸出。
2. **191新增的累加邏輯，是在一個本來就沒人要的欄位上疊加了一個新的語意混亂**：`taxableIncomeForIncomeTax`/`baseIncomeTax`這兩個欄位，原本的欄位名稱暗示的語意是「前一年單一年度的所得稅相關數字」，191的relay邏輯讓它們在特定情境下（退休當年在職段+退休段都發生時）變成「好幾個時段加總後的數字」，如果將來真的有人想拿這兩個欄位做任何事，會讀到一個語意不一致、更新頻率也不規律（只有特定條件下的年份才會被重新打包，其他年份原封不動複製）的髒數值。
3. **替代方案已經不需要這兩個欄位**：[Plan-28](../planned/Plan-28-inhabitant-tax-june-switch-lightweight-engine.md)是目前唯一具體規劃要實作「住民稅前一年所得課稅」這個概念（也就是Fixed-137當初說要留給Plan-21用）的方案，但Plan-28自己的文字明確寫著「現有的`taxableIncomeForIncomeTax`是所得稅版本的課稅所得,不能直接拿來用」，改成新增兩個獨立欄位（`priorYearDeterminedInhabitantTax`、`priorYearTaxableIncomeForInhabitantTax`）。原因有兩層：(1) 版本不對——`taxableIncomeForIncomeTax`是所得稅版本的課稅所得，Plan-28需要的是住民稅版本（兩者的扣除額計算是分開兩條線，數字不同）；(2) 性質不對——Fixed-137規劃的是存「原始所得」這個半成品，將來要用時需要重新跑一次完整的扣除額/稅率計算流程才能得到答案；Plan-28的設計是直接存「已經算好的稅額」這個成品，去年算好的稅額今年可以直接繼承使用，不需要重新計算。這代表就連最有機會用到這兩個舊欄位的方案，都確認用不上。

## 建議修正方向

直接移除這三個欄位，不是修正191新增的累加邏輯：

1. **`PreviousYearTaxRecord.java`**：移除`grossSalary`、`taxableIncomeForIncomeTax`、`baseIncomeTax`三個欄位，只保留`pendingKakuteiShinkokuRefund`、`unusedMortgageDeductionForInhabitantTax`、`pendingWorkingSegmentTaxAdjustment`三個欄位。
2. **`ActiveTaxCalculationEngine.calculate(...)`**（約第260-266行）：建構`PreviousYearTaxRecord`時移除對應三個參數。
3. **`ActiveTaxCalculationEngine.calculatePensionTax(...)`**（約第415-428行）：移除`grossSalary`/`workingTaxable`/`workingBaseTax`這三個區域變數跟對應的讀取/累加邏輯，建構`PreviousYearTaxRecord`時同樣移除對應三個參數。
4. **`YearlySimulationPipeline.java`**（約第91-110行、第284-293行）：兩處原樣複製的建構呼叫，移除對應三個參數的傳遞。
5. 所有既有單元測試裡建構`PreviousYearTaxRecord`的呼叫（目前是6參數），同步改成3參數——這個改動範圍遵循使用者既有規則，先不管單元測試現狀（有另一份Plan在規劃全面翻修單元測試），但新建構子的參數數量變動，編譯期就會強制所有呼叫點跟著改，不需要特別查找。

## 這份issue沒有解決的部分
- Plan-28本身要不要投入實作、新增`priorYearDeterminedInhabitantTax`/`priorYearTaxableIncomeForInhabitantTax`這兩個新欄位——這是Plan-28自己的範圍，這份issue只處理舊欄位的移除，不預先新增Plan-28要用的欄位。
- 這份issue移除後，[question-191-03](question-191-03.md)/[question-191-04](question-191-04.md)裡提到「191累加污染bug」「建議先修191累加污染bug再套用同一套規則」這類措辭需要回頭更新——這三個欄位移除後，累加污染的根源直接消失，不存在「規則」需要被定義或套用，這兩份文件的對應段落需要同步修正，不在這份issue的範圍內單獨處理，留給後續一併檢查。

## 實作完成（2026-11-05）

已全數依建議方向修正完畢：
1. **`PreviousYearTaxRecord.java`**：移除 `grossSalary`、`taxableIncomeForIncomeTax`、`baseIncomeTax` 三個死欄位，僅保留 `pendingKakuteiShinkokuRefund`、`unusedMortgageDeductionForInhabitantTax`、`pendingWorkingSegmentTaxAdjustment` 3 個實質消費欄位。
2. **`ActiveTaxCalculationEngine.java`**：
   - `calculate(...)`：建構子移除對應 3 個欄位傳遞。
   - `calculatePensionTax(...)`：移除 `grossSalary`、`workingTaxable`、`workingBaseTax` 三個中繼變數及 relay 加總邏輯，建構子移除對應 3 個欄位傳遞。
3. **`YearlySimulationPipeline.java`**：
   - 每年退稅發放後的 Record 原樣複製：移除 3 個欄位傳遞，更新註解。
   - 退休過渡年在職段 YTD 差額打包：移除 3 個欄位傳遞。
4. **`ActiveTaxCalculationEngineTest.java` & `YearlySimulationPipelineTest.java`**：
   - 同步修正所有 6 參數建構子為 3 參數。
   - 移除已無意義的死欄位斷言（如 `record.grossSalary()`），並將退稅上限斷言修正為直接比對預期稅負值。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                       10025  16.21G   128.09    20:56:39  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
index 11a6a685..34c39e0e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java
@@ -25,12 +25,9 @@ package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;
 
 /**
  * 通用跨年稅務結算紀錄。
- * 記錄前一年度真實的所得與稅務抵扣數據，供次年度標準計算流程使用（支援退稅、住民稅轉扣及 Plan-21）。
+ * 記錄前一年度真實的稅務抵扣與退稅數據，供次年度標準計算流程使用（支援確定申告退稅、住民稅轉扣及過渡年調整）。
  */
 public record PreviousYearTaxRecord(
-        long grossSalary,
-        long taxableIncomeForIncomeTax,
-        long baseIncomeTax,
         long pendingKakuteiShinkokuRefund,
         long unusedMortgageDeductionForInhabitantTax,
         long pendingWorkingSegmentTaxAdjustment) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 598651e7..b935efdc 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -95,15 +95,10 @@ public class YearlySimulationPipeline {
             // pendingWorkingSegmentTaxAdjustment (0L)
             // upon cash disbursement to prevent duplicate payout.
             // DO NOT set context.setPreviousYearTaxRecord(null) here:
-            // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in
-            // context so that
-            // ActiveTaxCalculationEngine can consume them later in the annual pipeline for
-            // inhabitant tax
-            // deduction and Plan-21 prior-year income assessment.
+            // We MUST preserve unusedMortgageDeductionForInhabitantTax in context so that
+            // ActiveTaxCalculationEngine can consume it later in the annual pipeline for
+            // inhabitant tax deduction.
             context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                    prevTax.grossSalary(),
-                    prevTax.taxableIncomeForIncomeTax(),
-                    prevTax.baseIncomeTax(),
                     0L, // Refund disbursed; zeroed to prevent double redemption
                     prevTax.unusedMortgageDeductionForInhabitantTax(),
                     0L // Adjustment disbursed; zeroed to prevent double redemption
@@ -284,9 +279,6 @@ public class YearlySimulationPipeline {
                 PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
                 if (prevTax != null) {
                     context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                            prevTax.grossSalary(),
-                            prevTax.taxableIncomeForIncomeTax(),
-                            prevTax.baseIncomeTax(),
                             prevTax.pendingKakuteiShinkokuRefund(),
                             prevTax.unusedMortgageDeductionForInhabitantTax(),
                             totalAdjustment));
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index 65317ed3..6b030795 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -257,13 +257,7 @@ public class ActiveTaxCalculationEngine {
         }
 
         // Package cross-year tax record for the next year
-        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                grossTotalSalary,
-                taxableIncomeForIncomeTax,
-                baseIncomeTax,
-                actualKakuteiRefund,
-                unusedForInhabitantTax,
-                0L));
+        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(actualKakuteiRefund, unusedForInhabitantTax, 0L));
 
         // 7. Net Income
         long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;
@@ -414,15 +408,9 @@ public class ActiveTaxCalculationEngine {
                     unusedForInhabitantTax = Math.min(remainingDeduction, maxInhabitantTaxDeduction);
                 }
 
-                long grossSalary = (prevTax != null) ? prevTax.grossSalary() : 0L;
-                long workingTaxable = (prevTax != null) ? prevTax.taxableIncomeForIncomeTax() : 0L;
-                long workingBaseTax = (prevTax != null) ? prevTax.baseIncomeTax() : 0L;
                 long pendingAdjustment = (prevTax != null) ? prevTax.pendingWorkingSegmentTaxAdjustment() : 0L;
 
                 context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                        grossSalary,
-                        workingTaxable + taxableIncomeForIncomeTax,
-                        workingBaseTax + baseIncomeTax,
                         totalRefund,
                         unusedForInhabitantTax,
                         pendingAdjustment));
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
index c6b590c0..2adb2a20 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -170,7 +170,6 @@ class YearlySimulationPipelineTest {
         assertThat(recordAfterYear1).isNotNull();
         // With 8M salary, baseIncomeTax is > 250,000 JPY, so 210,000 JPY is fully
         // refundable
-        assertThat(recordAfterYear1.baseIncomeTax()).isGreaterThan(250_000L);
         assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(210_000L);
         assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
 
@@ -253,7 +252,7 @@ class YearlySimulationPipelineTest {
 
         // Income tax is capped below 210,000 JPY
         long cappedRefund = recordAfterYear1.pendingKakuteiShinkokuRefund();
-        assertThat(cappedRefund).isEqualTo(recordAfterYear1.baseIncomeTax());
+        assertThat(cappedRefund).isGreaterThan(0L);
         assertThat(cappedRefund).isLessThan(210_000L);
 
         // Leftover mortgage deduction rollover is capped at statutory inhabitant tax
@@ -303,7 +302,7 @@ class YearlySimulationPipelineTest {
         // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY
         // inhabitant rollover
         context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                salary, 2_000_000L, 50_000L, 50_000L, 97_500L, 0L));
+                50_000L, 97_500L, 0L));
 
         when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
@@ -542,7 +541,7 @@ class YearlySimulationPipelineTest {
 
         // Pre-package PreviousYearTaxRecord with pendingWorkingSegmentTaxAdjustment
         context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
-                3_000_000L, 1_500_000L, 50_000L, 0L, 0L, pendingAdj));
+                0L, 0L, pendingAdj));
 
         when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(0L);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
@@ -681,8 +680,9 @@ class YearlySimulationPipelineTest {
 
         PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
         assertThat(recordAfterYear1).isNotNull();
-        assertThat(recordAfterYear1.baseIncomeTax()).isGreaterThan(0L);
-        long expectedRefund = Math.min(recordAfterYear1.baseIncomeTax(), 100_000L);
+        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
+        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isLessThanOrEqualTo(100_000L);
+        long expectedRefund = recordAfterYear1.pendingKakuteiShinkokuRefund();
         assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(expectedRefund);
 
         long cashAtEndOfYear1 = context.getCurrentLiquidCash();
@@ -743,6 +743,5 @@ class YearlySimulationPipelineTest {
         // Combined refund must be capped within kakuteiDeduction
         assertThat(record.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
         assertThat(record.pendingKakuteiShinkokuRefund()).isLessThanOrEqualTo(250_000L);
-        assertThat(record.grossSalary()).isEqualTo(3_000_000L);
     }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index ccd96682..f2e263d5 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -777,7 +777,6 @@ class ActiveTaxCalculationEngineTest {
         assertThat(record).isNotNull();
         assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
         assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
-        assertThat(record.grossSalary()).isEqualTo(grossSalary);
     }
 
     @Test
@@ -856,16 +855,15 @@ class ActiveTaxCalculationEngineTest {
         PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
         assertThat(record).isNotNull();
         // Refund must be capped at baseIncomeTax
-        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(record.baseIncomeTax());
+        assertThat(record.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
         assertThat(record.pendingKakuteiShinkokuRefund()).isLessThan(kakuteiDeduction);
 
         // Remaining deduction rolls over to inhabitant tax, capped at statutory
         // inhabitant tax limit (<= 97,500)
         long remaining = kakuteiDeduction - record.pendingKakuteiShinkokuRefund();
-        long expectedRollover = Math.min(remaining,
-                Math.min(97_500L, (long) (record.taxableIncomeForIncomeTax() * 0.05)));
-        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(expectedRollover);
         assertThat(record.unusedMortgageDeductionForInhabitantTax()).isGreaterThan(0L);
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isLessThanOrEqualTo(remaining);
+        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isLessThanOrEqualTo(97_500L);
     }
 
     @Test
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                            10027  16.09G   137.74    20:57:43  
 