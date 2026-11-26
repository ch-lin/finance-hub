# Fixed 107：已退休正在領年金的使用者，模擬第一年的年金收入沒有按剩餘月份比例分攤，會整年灌滿12個月金額

## 嚴重程度
Medium（只影響「模擬開始時，使用者本人已經退休、正在領年金」這個特定情境；目前正在工作、模擬未來某年退休的情境不受影響，因為只有第一個模擬年份才有這個比例分攤的問題）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:196-222`（`applyRetireeTaxes`，退休領年金這條路徑，方法簽章完全沒有接收`monthsToAdd`參數）
- 對照組：同檔案第142-159行（`applyWorkingTaxes`，在職薪資這條路徑），第153-154行明確有`double remainingRatio = monthsToAdd / 12.0; kouseiContribution = Math.round(kouseiContribution * remainingRatio);`這樣的比例分攤
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java:83-206`（在職薪資路徑用來扣除「今年已經實際領過」部分的歷史紀錄來源，退休年金路徑沒有對應的機制）

## 現狀
模擬第一年是「部分年份」——如果今天是9月才開始跑模擬，這一年只剩9到12月共4個月，`monthsToAdd`就會被算成4。在職薪資這條路徑（`applyWorkingTaxes`）會正確地把厚生年金保費之類的金額，依照`monthsToAdd / 12.0`這個比例縮減，只算剩下的月份。但退休領年金這條路徑（`applyRetireeTaxes`）完全沒有接收`monthsToAdd`這個參數，算出來的`basicPension`／`kouseiPension`／`pensionIncome`永遠是完整一整年（12個月）的金額，直接設定成這一年剩下的淨收入。

## 為何是問題
如果使用者今天已經退休、正在領年金的狀態下開始跑模擬，第一年（部分年份）應該只計入「剩下幾個月」的年金收入，而不是整年的金額——這跟在職薪資路徑處理的邏輯應該是對等的，只是年金這條路徑沒有做同樣的事。

## 具體錯誤情境
假設一位68歲、已經退休正在領年金的使用者，在9月開始跑模擬，年金稅後淨收入全年金額算出來是¥2,400,000。正確的「剩餘月份」金額應該大約是9到12月這4個月、約¥800,000（12分之4）；但系統會直接把完整的¥2,400,000當成這一年剩下的淨收入灌進去，等於把這第一年的年金現金流入高估了大約3倍。這會讓模擬出的第一年現金水位看起來比實際寬裕很多，如果使用者剛好是拿這個「第一年」的數字去判斷手上現金夠不夠支撐買房的頭期款或短期壓力，會被這個高估的數字誤導。

## 建議修正
比照`applyWorkingTaxes`已經在用的做法，在`applyRetireeTaxes`裡也接收`monthsToAdd`參數，把算出來的`pensionIncome`（或`basicPension`／`kouseiPension`）依照`monthsToAdd / 12.0`的比例縮減，再設定進`state`裡。日本公的年金是雙月給付，用「平均分攤到12個月、再依剩餘月份比例縮減」來近似，是可以接受的簡化，跟在職薪資路徑採用的簡化方式一致。

---

## 修正報告（2026/09/30 已修復完成）

### 1. 修改摘要
- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`
- **修改位置**：
  1. 第 129 行：呼叫端改為 `applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge);`
  2. 第 196-231 行：方法簽章擴充接收 `int initialAge, int monthsToAdd`。在計算完年度常態稅額後，加入分支判斷：
     ```java
     if (age == initialAge && monthsToAdd < 12) {
         double remainingRatio = (double) monthsToAdd / 12.0;
         state.setPensionIncome(Math.round(pensionIncome * remainingRatio));
         state.setEstimatedIncomeTax(Math.round(taxResult.incomeTax() * remainingRatio));
         state.setEstimatedInhabitantTax(Math.round(taxResult.inhabitantTax() * remainingRatio));
         state.setEstimatedSocialInsurance(Math.round(taxResult.socialInsurance() * remainingRatio));
         state.setNetIncome(Math.round(taxResult.netIncome() * remainingRatio));
     } else {
         state.setPensionIncome(pensionIncome);
         state.setEstimatedIncomeTax(taxResult.incomeTax());
         state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
         state.setEstimatedSocialInsurance(taxResult.socialInsurance());
         state.setNetIncome(taxResult.netIncome());
     }
     ```

### 2. 修復效果
1. **消除首年過度樂觀的現金流**：若 68 歲退休人員於 9 月份啟動模擬（剩餘 4 個月），年金所得與稅後淨收入不再灌滿 12 個月，而是精確依 `4 / 12` 比例計算，消除了將 1~8 月已領年金重複計入的 Double-counting 缺陷。
2. **稅率級距精確**：採用「先以全年度規模計算稅務級距與扣除額，再依比例折算實領與實繳」之模式，維持全年度有效稅率之精準性，並使第一年淨現金流與 `annualExpenses`（已按 `monthsToAdd` 折算）達到完美對齊。
3. **無副作用**：對於尚未退休的使用者，其退休年份皆為未來年份（`age > initialAge`，`monthsToAdd == 12`），維持走原本完整的 12 個月年金給付邏輯，完全不受影響。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT  git --no-pager diff                                                                         10016  12.21G   3.39    20:24:45  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 099b370e..9bd5cd66 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -126,7 +126,7 @@ public class YearlySimulationPipeline {
                 mortgageResult, contributions);
         log.debug("Age {} [After Working Taxes]: {}", age, state);
 
-        applyRetireeTaxes(context, state, age, pensionStartAge);
+        applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge);
         log.debug("Age {} [After Retiree Taxes]: {}", age, state);
 
         applyNationalInsuranceOutOfPocket(context, state, age, effectiveAnnualSalary, monthsToAdd);
@@ -194,7 +194,7 @@ public class YearlySimulationPipeline {
     }
 
     private void applyRetireeTaxes(SimulationContext context, YearlySimulationState state, int age,
-            int pensionStartAge) {
+            int initialAge, int monthsToAdd, int pensionStartAge) {
         if (context.isRetired()) {
             long basicPension = 0;
             long kouseiPension = 0;
@@ -211,13 +211,23 @@ public class YearlySimulationPipeline {
                         * pensionCalculator.calculatePensionMultiplier(pensionStartAge));
             }
             long pensionIncome = basicPension + kouseiPension;
-            state.setPensionIncome(pensionIncome);
 
             var taxResult = taxEngine.calculatePensionTax(context, pensionIncome, age);
-            state.setEstimatedIncomeTax(taxResult.incomeTax());
-            state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
-            state.setEstimatedSocialInsurance(taxResult.socialInsurance());
-            state.setNetIncome(taxResult.netIncome());
+
+            if (age == initialAge && monthsToAdd < 12) {
+                double remainingRatio = (double) monthsToAdd / 12.0;
+                state.setPensionIncome(Math.round(pensionIncome * remainingRatio));
+                state.setEstimatedIncomeTax(Math.round(taxResult.incomeTax() * remainingRatio));
+                state.setEstimatedInhabitantTax(Math.round(taxResult.inhabitantTax() * remainingRatio));
+                state.setEstimatedSocialInsurance(Math.round(taxResult.socialInsurance() * remainingRatio));
+                state.setNetIncome(Math.round(taxResult.netIncome() * remainingRatio));
+            } else {
+                state.setPensionIncome(pensionIncome);
+                state.setEstimatedIncomeTax(taxResult.incomeTax());
+                state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
+                state.setEstimatedSocialInsurance(taxResult.socialInsurance());
+                state.setNetIncome(taxResult.netIncome());
+            }
         }
     }
 
@@ -280,7 +290,7 @@ public class YearlySimulationPipeline {
             context.setAccumulatedGrowthNisaPrincipal(
                     Math.max(0,
                             context.getAccumulatedGrowthNisaPrincipal()
-                                    - context.getGrowthNisaPrincipalToRecoverNextYear()));
+                            - context.getGrowthNisaPrincipalToRecoverNextYear()));
             context.setGrowthNisaPrincipalToRecoverNextYear(0L);
         }
     }
diff --git a/finance-hub-backend/todo/pending fix/issue-107.md b/finance-hub-backend/todo/pending fix/issue-107.md
index afdf9cef..81a76c11 100644
--- a/finance-hub-backend/todo/pending fix/issue-107.md
+++ b/finance-hub-backend/todo/pending fix/issue-107.md
@@ -19,3 +19,35 @@ Medium（只影響「模擬開始時，使用者本人已經退休、正在領
 
 ## 建議修正
 比照`applyWorkingTaxes`已經在用的做法，在`applyRetireeTaxes`裡也接收`monthsToAdd`參數，把算出來的`pensionIncome`（或`basicPension`／`kouseiPension`）依照`monthsToAdd / 12.0`的比例縮減，再設定進`state`裡。日本公的年金是雙月給付，用「平均分攤到12個月、再依剩餘月份比例縮減」來近似，是可以接受的簡化，跟在職薪資路徑採用的簡化方式一致。
+
+---
+
+## 修正報告（2026/09/30 已修復完成）
+
+### 1. 修改摘要
+- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`
+- **修改位置**：
+  1. 第 129 行：呼叫端改為 `applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge);`
+  2. 第 196-231 行：方法簽章擴充接收 `int initialAge, int monthsToAdd`。在計算完年度常態稅額後，加入分支判斷：
+     ```java
+     if (age == initialAge && monthsToAdd < 12) {
+         double remainingRatio = (double) monthsToAdd / 12.0;
+         state.setPensionIncome(Math.round(pensionIncome * remainingRatio));
+         state.setEstimatedIncomeTax(Math.round(taxResult.incomeTax() * remainingRatio));
+         state.setEstimatedInhabitantTax(Math.round(taxResult.inhabitantTax() * remainingRatio));
+         state.setEstimatedSocialInsurance(Math.round(taxResult.socialInsurance() * remainingRatio));
+         state.setNetIncome(Math.round(taxResult.netIncome() * remainingRatio));
+     } else {
+         state.setPensionIncome(pensionIncome);
+         state.setEstimatedIncomeTax(taxResult.incomeTax());
+         state.setEstimatedInhabitantTax(taxResult.inhabitantTax());
+         state.setEstimatedSocialInsurance(taxResult.socialInsurance());
+         state.setNetIncome(taxResult.netIncome());
+     }
+     ```
+
+### 2. 修復效果
+1. **消除首年過度樂觀的現金流**：若 68 歲退休人員於 9 月份啟動模擬（剩餘 4 個月），年金所得與稅後淨收入不再灌滿 12 個月，而是精確依 `4 / 12` 比例計算，消除了將 1~8 月已領年金重複計入的 Double-counting 缺陷。
+2. **稅率級距精確**：採用「先以全年度規模計算稅務級距與扣除額，再依比例折算實領與實繳」之模式，維持全年度有效稅率之精準性，並使第一年淨現金流與 `annualExpenses`（已按 `monthsToAdd` 折算）達到完美對齊。
+3. **無副作用**：對於尚未退休的使用者，其退休年份皆為未來年份（`age > initialAge`，`monthsToAdd == 12`），維持走原本完整的 12 個月年金給付邏輯，完全不受影響。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                            10016  12.41G   3.27    21:47:27  