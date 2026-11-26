# Fixed 109：AI優化演算法突變機制，房貸提前還款「年份」這個基因沒有夾回合理範圍，可能隨機漂移到不合理的年份

## 嚴重程度
Medium（不是每次都會發生，需要跑多代且運氣不好才會真正漂移出合理範圍，但一旦發生，結果會直接出現在給使用者看的最終建議裡，沒有任何檢查機制會攔下來）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:112-175`（`mutate`方法），特別是第164行（房貸提前還款年份的微調，沒有夾回範圍）對照第115-134行（退休年齡、年金起始年齡、iDeCo起始年齡、NISA成長/定期定額目標，這幾個基因微調後都有立即夾回合理範圍）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:46-56`（把基因值原封不動、沒有做任何邊界檢查地放進`SimulationOverrides.MortgageEarlyRepaymentOverride`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`（`getOverrideEarlyRepaymentAmount`，只用`.equals()`直接比對年份，沒有做任何邊界驗證）

## 現狀
AI優化演算法（GA）每一代都會對候選方案做隨機微調（突變）。清單裡每一種可以被微調的數值——退休年齡、年金起始年齡、iDeCo起始年齡、NISA成長/定期定額目標——微調完之後，程式都會立刻把結果夾回合理範圍內（例如用`Math.min(MAX, Math.max(MIN, ...))`）。但房貸提前還款的「年份」這個基因，微調時（第164行，`gene.setYear(gene.getYear() + (random.nextInt(3) - 1))`）完全沒有做這個夾回範圍的動作——這個基因剛被建立時確實是落在合理範圍內（`minRepayYear`到`maxRepayYear`之間），但之後每一代大約有一半機率被隨機加減1年，且沒有任何上下限檔住。

## 為何是問題
這是一個沒有邊界的隨機漫步。同一個方案會經過演算法內部很多代的演化、交叉、突變，這個年份數值理論上可以被連續往同一個方向推很多次，最終有可能漂移到「早於現在」（等於在模擬開始之前就要還款，沒有意義）或「晚於使用者預期壽命」（超出模擬範圍）這種不合理的年份。這個數值一旦離開合理範圍，後續完全沒有任何地方會攔下來或做修正——它會被原封不動放進最終回傳給使用者的建議方案裡，使用者可能會看到一個「建議在過去某年還清房貸」這種沒有意義的結果。

## 建議修正
在第164行對房貸年份做微調時，比照其他基因已經在用的做法，微調完立刻用`Math.min(maxRepayYear, Math.max(minRepayYear, ...))`夾回合理範圍（`minRepayYear`／`maxRepayYear`這兩個變數在這個方法裡已經算好、可以直接使用）。這是一個局部、低風險的修正，只是把這個基因的處理方式，補齊成跟同一個方法裡其他基因一致的既有寫法。

## 補充：另外兩個較小、待確認的相關備註
1. `idecoStartAge`這個基因名稱容易讓人誤解——它實際控制的是「iDeCo幾歲可以合法領取」，不是「幾歲開始提繳」。目前程式碼運作上是自洽、正確的，只是這個命名未來如果有人要新增「提繳起始年齡」這種真正的新功能，可能會被這個名字誤導接錯欄位。建議之後有機會調整這塊時，一併把名稱改成更明確的`idecoWithdrawalAge`。
2. Grid Search計算房貸年份搜尋範圍時，使用的「已累積縮短月數」計算基準點是「今年12月底」，而不是「現在」——這個只影響搜尋範圍大小的估算，不影響實際房貸攤還計算本身。不確定這是刻意的設計（假設今年剩下的還款排程都會照常發生），還是應該改用「現在」為基準，這裡列為待確認的細節，不斷言為bug。

---

## 修正完成紀錄 (Resolution Report)

### 1. 房貸還款年份突變邊界夾回（Clamping）
- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:163-166`
- **修正內容**：
  在 GA 演算法對房貸提前還款年份進行微調（突變）時，補上邊界夾回保護：
  ```java
  // Mutate year by +/- 1 year
  if (random.nextDouble() < 0.5) {
      int mutatedYear = gene.getYear() + (random.nextInt(3) - 1);
      gene.setYear(Math.min(maxRepayYear, Math.max(minRepayYear, mutatedYear)));
  }
  ```
  確保演化過程中即使連續微調，還款年份也絕不會漂移至小於 `minRepayYear`（今年之後）或大於 `maxRepayYear`（預期壽命邊界）。

### 2. 關於補充備註「`idecoStartAge` 命名」的評估與處理
- **評估結果**：
  經查，`idecoStartAge` 與 `idecoStartAgeOverride` 不僅是後端內部的染色體變數，更已作為 API JSON 契約貫穿到前端 `hub-ui`（如 `use-global-optimizer.ts`、`use-simulation-data.ts`、`global-optimizer-card.tsx`、`page.tsx`）。若僅在 Java 進行單鍵重構會造成跨語言 API 契約破壞；若前後端大規模連動更名則風險偏高，非本次 Issue 109 範圍。
- **補強措施**：
  在 `Chromosome.java` 與 `SimulationOverrides.java` 相關欄位上方加入明確 Javadoc 註解，清楚指引各個欄位的真實語意與對應的 Profile 屬性：
  1. `Chromosome.java`:
     - `pensionStartAge`: 公的年金受給開始年齡（對應 `UserProfile.pensionStartAge`）
     - `idecoStartAge`: 稅制優惠帳戶（iDeCo / DC）提領起始年齡（對應 `UserProfile.taxAdvantagedWithdrawalAge`）
  2. `SimulationOverrides.java`:
     - `pensionStartAgeOverride`: 覆寫公的年金受給開始年齡
     - `idecoStartAgeOverride`: 覆寫 iDeCo/DC 提領起始年齡（非提繳年齡）
     - `ProfileBaselineUsageDetails.idecoStartAge`: 記錄套用自 Profile 的提領起始基準值

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                       10018  12.14G   5.36    21:52:43  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
index c51c7417..fd58eb06 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
@@ -28,15 +28,23 @@ import java.util.Map;
 
 /**
  * Holds parameters used by external Solvers to override the baseline simulation
- * behavior.
- * This allows the pure simulation engine to test different "parallel universe"
- * scenarios.
+ * behavior. This allows the pure simulation engine to test different "parallel
+ * universe" scenarios.
  */
 public record SimulationOverrides(
         Long testNisaGrowthMonthlyContribution,
         Long testNisaTsumitateMonthlyContribution,
         Integer targetRetirementAgeOverride,
+        /**
+         * Overrides the public pension start age (公的年金受給開始年齢). Corresponds to
+         * UserProfile.pensionStartAge.
+         */
         Integer pensionStartAgeOverride,
+        /**
+         * Overrides the target age to start withdrawing tax-advantaged accounts
+         * (iDeCo / DC). Corresponds to UserProfile.taxAdvantagedWithdrawalAge
+         * (not contribution start age).
+         */
         Integer idecoStartAgeOverride,
         Map<Long, List<MortgageEarlyRepaymentOverride>> mortgageEarlyRepayments,
         List<FundOverride> fundOverrides,
@@ -45,10 +53,19 @@ public record SimulationOverrides(
 
     public record ProfileBaselineUsageDetails(
             Integer targetRetirementAge,
+            /**
+             * Baseline public pension start age applied from UserProfile.
+             * Corresponds to UserProfile.pensionStartAge.
+             */
             Integer pensionStartAge,
+            /**
+             * Baseline tax-advantaged withdrawal age applied from UserProfile.
+             * Corresponds to UserProfile.taxAdvantagedWithdrawalAge.
+             */
             Integer idecoStartAge,
             Long nisaGrowthMonthlyContribution,
             Long nisaTsumitateMonthlyContribution) {
+
         public boolean anyUsed() {
             return targetRetirementAge != null || pensionStartAge != null || idecoStartAge != null
                     || nisaGrowthMonthlyContribution != null
@@ -61,15 +78,18 @@ public record SimulationOverrides(
             Long fundId,
             Long contributionOverride,
             Double returnRateOverride) {
+
     }
 
     public record MortgageRateScenarioOverride(
             Integer startYear,
             Double rate) {
+
     }
 
     public record MortgageEarlyRepaymentOverride(
             Integer year,
             Long amount) {
+
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
index a6badbb5..74fab994 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
@@ -44,9 +44,17 @@ public class Chromosome {
     private Integer retirementAge;
 
     private boolean pensionStartAgeLocked;
+    /**
+     * Age to start receiving public pension (公的年金受給開始年齢). Corresponds to
+     * UserProfile.pensionStartAge.
+     */
     private Integer pensionStartAge;
 
     private boolean idecoStartAgeLocked;
+    /**
+     * Target age to start withdrawing tax-advantaged accounts (iDeCo / DC).
+     * Corresponds to UserProfile.taxAdvantagedWithdrawalAge.
+     */
     private Integer idecoStartAge;
 
     private boolean nisaGrowthTargetLocked;
@@ -65,6 +73,7 @@ public class Chromosome {
     @Data
     @NoArgsConstructor
     public static class MortgageRepaymentGene {
+
         private Integer year;
         private Long amount;
 
@@ -161,7 +170,8 @@ public class Chromosome {
                     if (gene.getYear() != null && gene.getAmount() != null) {
                         // Mutate year by +/- 1 year
                         if (random.nextDouble() < 0.5) {
-                            gene.setYear(gene.getYear() + (random.nextInt(3) - 1));
+                            int mutatedYear = gene.getYear() + (random.nextInt(3) - 1);
+                            gene.setYear(Math.min(maxRepayYear, Math.max(minRepayYear, mutatedYear)));
                         }
                         // Mutate amount by +/- 10% up to 1,000,000
                         if (random.nextDouble() < 0.5) {
diff --git a/finance-hub-backend/todo/pending fix/issue-109.md b/finance-hub-backend/todo/pending fix/issue-109.md
index e6ed8e00..536c6d4a 100644
--- a/finance-hub-backend/todo/pending fix/issue-109.md
+++ b/finance-hub-backend/todo/pending fix/issue-109.md
@@ -20,3 +20,35 @@ AI優化演算法（GA）每一代都會對候選方案做隨機微調（突變
 ## 補充：另外兩個較小、待確認的相關備註
 1. `idecoStartAge`這個基因名稱容易讓人誤解——它實際控制的是「iDeCo幾歲可以合法領取」，不是「幾歲開始提繳」。目前程式碼運作上是自洽、正確的，只是這個命名未來如果有人要新增「提繳起始年齡」這種真正的新功能，可能會被這個名字誤導接錯欄位。建議之後有機會調整這塊時，一併把名稱改成更明確的`idecoWithdrawalAge`。
 2. Grid Search計算房貸年份搜尋範圍時，使用的「已累積縮短月數」計算基準點是「今年12月底」，而不是「現在」——這個只影響搜尋範圍大小的估算，不影響實際房貸攤還計算本身。不確定這是刻意的設計（假設今年剩下的還款排程都會照常發生），還是應該改用「現在」為基準，這裡列為待確認的細節，不斷言為bug。
+
+---
+
+## 修正完成紀錄 (Resolution Report)
+
+### 1. 房貸還款年份突變邊界夾回（Clamping）
+- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:163-166`
+- **修正內容**：
+  在 GA 演算法對房貸提前還款年份進行微調（突變）時，補上邊界夾回保護：
+  ```java
+  // Mutate year by +/- 1 year
+  if (random.nextDouble() < 0.5) {
+      int mutatedYear = gene.getYear() + (random.nextInt(3) - 1);
+      gene.setYear(Math.min(maxRepayYear, Math.max(minRepayYear, mutatedYear)));
+  }
+  ```
+  確保演化過程中即使連續微調，還款年份也絕不會漂移至小於 `minRepayYear`（今年之後）或大於 `maxRepayYear`（預期壽命邊界）。
+
+### 2. 關於補充備註「`idecoStartAge` 命名」的評估與處理
+- **評估結果**：
+  經查，`idecoStartAge` 與 `idecoStartAgeOverride` 不僅是後端內部的染色體變數，更已作為 API JSON 契約貫穿到前端 `hub-ui`（如 `use-global-optimizer.ts`、`use-simulation-data.ts`、`global-optimizer-card.tsx`、`page.tsx`）。若僅在 Java 進行單鍵重構會造成跨語言 API 契約破壞；若前後端大規模連動更名則風險偏高，非本次 Issue 109 範圍。
+- **補強措施**：
+  在 `Chromosome.java` 與 `SimulationOverrides.java` 相關欄位上方加入明確 Javadoc 註解，清楚指引各個欄位的真實語意與對應的 Profile 屬性：
+  1. `Chromosome.java`:
+     - `pensionStartAge`: 公的年金受給開始年齡（對應 `UserProfile.pensionStartAge`）
+     - `idecoStartAge`: 稅制優惠帳戶（iDeCo / DC）提領起始年齡（對應 `UserProfile.taxAdvantagedWithdrawalAge`）
+  2. `SimulationOverrides.java`:
+     - `pensionStartAgeOverride`: 覆寫公的年金受給開始年齡
+     - `idecoStartAgeOverride`: 覆寫 iDeCo/DC 提領起始年齡（非提繳年齡）
+     - `ProfileBaselineUsageDetails.idecoStartAge`: 記錄套用自 Profile 的提領起始基準值
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                            10018  12.40G   4.74    22:03:06  