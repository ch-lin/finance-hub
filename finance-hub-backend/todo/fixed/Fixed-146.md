# [FIXED] Issue 146：房貸減稅只檢查了「扣除期限的上限」，沒有檢查「入住年之前」這個下限，入住前的年度可能被誤算出一筆不該有的全額減稅

## 這份issue怎麼被發現的
這是這次Source Code Review針對房貸模擬邏輯進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:340-379`
- 第342行：`if (endYear != null && year <= endYear)`——只檢查了扣除期限的「上限」
- 第352-353行：`isFirstEligibleYear = (refDate != null && year == refDate.getYear())`——只用「完全相等」去判斷是不是入住第一年，沒有同時檢查「現在這年有沒有達到入住年」
- 對照：[Fixed-66](../fixed/Fixed-66%20支援未來房貸模擬實作計畫%20(Support%20Future%20Mortgages).md)（這個系統本來就支援設定未來才會動用的房貸，貸款執行日期`startDate`可以早於入住日期`occupancyDate`）

## 現狀
房貸減稅的程式碼，只檢查了「現在這年有沒有超過扣除期限」這個上限，完全沒有檢查「現在這年有沒有達到入住年」這個下限。對於「貸款已經動用、但還沒到入住年」的這段期間（例如年底簽約動用貸款、下一年才正式入住），程式碼既不符合「剛好是入住第一年」的判斷（因為年份還沒到），也沒有被排除在扣除期限之外（因為扣除期限是在未來、還沒到），結果就直接落入「正常年度」這個分支，被當成合法的減稅年度，整筆減稅直接套用到當年的所得稅上。

## 為何是問題
依照這個系統自己在[Fixed-45](../fixed/Fixed-45.md)/[Fixed-45-01](../fixed/Fixed-45-01-%20%E4%BD%8F%E5%AE%85%E3%83%AD%E3%83%BC%E3%83%B3%E6%8E%A7%E9%99%A4%E9%A6%96%E5%B9%B4%E6%99%82%E9%96%93%E5%B7%AE%E4%BF%AE%E5%BE%A9%E8%A8%88%E7%95%AB.md)已經查證過的法規基礎（國稅廳No.1211-1/No.1213），房貸減稅的資格起算點是入住年，入住之前的年度不應該有任何減稅（不管是直接扣抵或是遞延扣抵）。但系統目前的實作，對「貸款已動用、尚未入住」這段期間完全沒有攔截，會直接套用一筆本來不該存在的全額減稅。

具體情境：使用者設定一筆房貸，貸款動用日2027年12月（例如年底完成交屋付款），入住日2028年1月（常見於跨年完工的新成屋，動用跟入住本來就不是同一天）。模擬跑到2027年，餘額因為貸款已動用而變成非零，減稅邏輯接著執行：`year(2027) <= endYear(約2040)`成立，`isFirstEligibleYear = (2027 == 2028)`不成立——結果直接落入正常年度分支，2027年還沒入住就被套用一筆完整、沒有遞延的減稅。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：補上對稱的下限判斷**
- 做法：把判斷條件改成`year <= endYear && year >= refDate.getYear()`，只在這個範圍內才計算減稅。
- 優點：改動小，直接對稱補齊原本就該有的下限。
- 缺點：這段判斷只能包住「減稅」這部分的計算，不能連帶把「餘額攤還」也一起卡住——貸款動用之後，不管有沒有入住，利息本來就會開始累積，這部分邏輯必須維持正常運作，只是「減稅」這個結果不該在入住前出現。

**方向B：把「已經進入第一個合格年度」做成一個明確的狀態**
- 做法：另外用一個欄位或旗標記錄「這筆房貸的減稅資格是否已經生效」，之後任何計算都檢查這個旗標，不是每次都重新比對年份。
- 優點：讓這個不變條件之後不會因為年份判斷邏輯被改動而重新破功。
- 缺點：改動範圍比方向A大，需要新增持久化的狀態。

---

## 決策方案：徹底拔除不合理之 Fallback，以「入住日（occupancyDate）」為減稅唯一法定基準與下限

### 一、決策背景與核心理由

1. **法理唯一基準**：
   依據日本國稅廳（タックスアンサー No.1211-1）與《租稅特別措置法》第 41 條，住宅借入金等特別控除的法定資格起算點**唯有「居住の用に供した日（實際入住日）」**，貸款動用日（`startDate`）根本不是減稅資格要件。
2. **領域模型一致性（Fallback 毫無道理）**：
   前端 UI（[`mortgages/page.tsx:561`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L561)）在使用者勾選「Eligible for Mortgage Tax Deduction」時，**入住日期（Occupancy Date）本來就是必填欄位（有必填紅星 `*`，未填寫連儲存按鈕都無法按下）**；後端儲存時也是以 `occupancyDate` 計算截止年份 `endYear`。
   因此，原代碼中：
   ```java
   LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
   ```
   這個對 `startDate` 的 fallback 毫無業務合理性，純粹是早期容錯留下的殘留代碼，將借款與入住和稀泥混在一起。
3. **一石二鳥的架構純化**：
   徹底拔除 fallback，將減稅資格完全鎖定於 `occupancyDate`。減稅有效年度區間天然即為 `[occupancyDate.getYear(), endYear]`。尚未入住之年份（`year < occupancyDate.getYear()`）連減稅門檻都不符合，Issue 146 的偷跑減稅缺陷自然徹底根除。

---

### 二、具體實作規劃

在 [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 的稅務計算區塊中：

1. **拔除 `refDate` 對 `startDate` 的 fallback**，直接使用 `m.getOccupancyDate()`。若無入住日或未到期，減稅額穩固維持 `0L`。
2. **加上對稱下限門檻**：`year >= occupancyDate.getYear()`。
3. **對齊 Issue 137 的雙通道派發**：
   - 入住首年（`year == occupancyDate.getYear()`）：額度進入 `kakuteiDeduction` 通道（隔年春季退稅）。
   - 次年起（`year > occupancyDate.getYear()`）：額度進入 `yearEndDeduction` 通道（當年年末調整）。
   - 入住年之前（`year < occupancyDate.getYear()`）：不進入減稅計算，額度為 0。

#### 核心代碼調整示意：
```java
if (Boolean.TRUE.equals(m.getTaxDeductionEligible())) {
    LocalDate occupancyDate = m.getOccupancyDate();
    Integer endYear = m.getTaxDeductionEndYear();

    // 嚴格檢驗：必須有入住日、必須有截止年，且當前年度必須落在 [入住年, 截止年] 之法定合法區間內
    if (occupancyDate != null && endYear != null 
            && year >= occupancyDate.getYear() && year <= endYear) {

        double rate = m.getTaxDeductionRate();
        long deduction = (long) (balance * rate);
        long maxDeduction = m.getMaxTaxDeductionAmount();
        if (deduction > maxDeduction) {
            deduction = maxDeduction;
        }

        // 首年確定申告 vs 次年起年末調整
        if (year == occupancyDate.getYear()) {
            kakuteiDeduction = deduction;
            yearEndDeduction = 0L;
        } else {
            yearEndDeduction = deduction;
            kakuteiDeduction = 0L;
        }

        MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(occupancyDate);
        if (deductionStrategy != null) {
            maxTotalIncomeForMortgageDeduction = deductionStrategy.getMaxEligibleTotalIncome();
        }

        InhabitantTaxLimitStrategy currentStrategy = new Pre2022InhabitantTaxLimitStrategy();
        if (occupancyDate.getYear() >= 2022) {
            currentStrategy = new Post2022InhabitantTaxLimitStrategy();
        }
        if (currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()) {
            bestStrategy = currentStrategy;
        }
    }
}
```

---

## 修復總結報告 (Resolution Report)

### 1. 核心修復概述
- **拔除不合理之 Fallback**：廢除 `refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate()` 的舊有容錯寫法，確立住宅ローン控除 100% 嚴格以入住日（`occupancyDate`）為唯一法定基準。若未設定入住日，減稅資格不成立。
- **補齊對稱年份下限**：在 `MortgageCalculator.java` 中將減稅條件收斂為 `year >= occupancyDate.getYear() && year <= endYear`，嚴格限制扣除額僅於法定有效期間內產生。
- **杜絕入住前偷跑減稅**：在貸款已動用但尚未入住之年度（`year < occupancyDate.getYear()`），完全不進入減稅計算，`yearEndDeduction` 與 `kakuteiDeduction` 穩固維持 `0L`，徹底防止因 `else` 誤判導致在入住前預先領取年末調整減稅、進而造成總減稅年數超額膨脹之缺陷。
- **首年與次年雙通道派發**：
  - 首年入居（`year == occupancyDate.getYear()`）：扣除額全數派發至 `kakuteiDeduction` 通道（依 Issue 137 機制於隔年春季退稅）。
  - 次年起（`year > occupancyDate.getYear()`）：扣除額派發至 `yearEndDeduction` 通道（當年年末調整直接折抵）。

### 2. 修改檔案清單
1. [`finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
   - 移除 `refDate` 對 `startDate` 的 fallback，改為直接提取 `m.getOccupancyDate()`。
   - 增加 `year >= occupancyDate.getYear()` 下限門檻，確保只有在 `[occupancyDate.getYear(), endYear]` 區間內才執行減稅計算。
   - 稅務策略派發 `taxStrategyFactory.getStrategy(...)` 與住民稅限額判斷改為直接傳入 `occupancyDate`。
2. [`finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)
   - 新增單元測試 `testSimulateMortgagesForYear_PriorToOccupancyYear_NoTaxDeduction`：
     - 測試情境：房貸於 2027 年 11 月啟動並還款，但入住日為 2028 年 2 月。
     - 斷言 1：2027 年模擬（未入住）時，`yearEndDeduction` 與 `kakuteiDeduction` 嚴格為 `0L`。
     - 斷言 2：2028 年模擬（入住首年）時，`kakuteiDeduction` 正確大於 0，`yearEndDeduction` 為 `0L`。

---

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                10036  12.97G   13.17    15:42:35  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index 21c106b8..fd8f01a1 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -339,8 +339,10 @@ public class MortgageCalculator {
             totalMortgagePaymentsThisYear += annualPayment;
 
             if (Boolean.TRUE.equals(m.getTaxDeductionEligible())) {
+                LocalDate occupancyDate = m.getOccupancyDate();
                 Integer endYear = m.getTaxDeductionEndYear();
-                if (endYear != null && year <= endYear) {
+                if (occupancyDate != null && endYear != null
+                        && year >= occupancyDate.getYear() && year <= endYear) {
                     double rate = m.getTaxDeductionRate();
                     long deduction = (long) (balance * rate);
 
@@ -350,8 +352,7 @@ public class MortgageCalculator {
                         deduction = maxDeduction;
                     }
 
-                    LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
-                    boolean isOccupancyYear = (refDate != null && year == refDate.getYear());
+                    boolean isOccupancyYear = (year == occupancyDate.getYear());
 
                     if (isOccupancyYear) {
                         kakuteiDeduction = deduction;
@@ -361,13 +362,13 @@ public class MortgageCalculator {
                         kakuteiDeduction = 0L;
                     }
 
-                    MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(refDate);
+                    MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(occupancyDate);
                     if (deductionStrategy != null) {
                         maxTotalIncomeForMortgageDeduction = deductionStrategy.getMaxEligibleTotalIncome();
                     }
 
                     InhabitantTaxLimitStrategy currentStrategy = new Pre2022InhabitantTaxLimitStrategy();
-                    if (refDate != null && refDate.getYear() >= 2022) {
+                    if (occupancyDate.getYear() >= 2022) {
                         currentStrategy = new Post2022InhabitantTaxLimitStrategy();
                     }
                     if (currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
index e1cf08ae..d46b7210 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
@@ -147,6 +147,65 @@ class MortgageCalculatorTest {
         assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
     }
 
+    @Test
+    void testSimulateMortgagesForYear_PriorToOccupancyYear_NoTaxDeduction() {
+        // Arrange: Loan executed in Nov 2027, but occupancy in Feb 2028
+        SimulationContext context2027 = new SimulationContext();
+        context2027.setYear(2027);
+
+        MortgageRecord m = new MortgageRecord(
+                "Future Handover Loan",
+                10000000L,
+                10000000L, // Starting balance upon disbursement
+                LocalDate.of(2027, 11, 1),
+                LocalDate.of(2057, 11, 1),
+                LocalDate.of(2027, 11, 1),
+                360,
+                0.01,
+                InterestRateType.FIXED,
+                RepaymentMethod.EQUAL_PAYMENT,
+                30000L,
+                Collections.emptyList(),
+                LocalDate.of(2028, 2, 1), // Occupancy in 2028!
+                null,
+                null,
+                true,
+                2040,
+                0.007,
+                273000L,
+                true);
+
+        context2027.setMortgageBalances(new long[]{0L});
+        context2027.setMortgageMonthlyPayments(new long[]{30000L});
+        context2027.setMortgageCurrentRates(new double[]{0.01});
+        context2027.setMortgageCurrentExpectedPayments(new long[]{30000L});
+        context2027.setMortgageMonthsSinceStart(new int[]{0});
+
+        YearMonth nowMonth = YearMonth.of(2027, 1);
+
+        // Act 1: Simulate Year 2027 (loan starts in Nov 2027, but not yet occupied in 2027)
+        MortgageYearlyResult result2027 = calculator.simulateMortgagesForYear(context2027, List.of(m), nowMonth, null);
+
+        // Assert 1: In 2027 (prior to occupancy year 2028), tax deductions MUST strictly be 0
+        assertEquals(0L, result2027.yearEndDeduction(), "No year-end tax deduction before occupancy year");
+        assertEquals(0L, result2027.kakuteiDeduction(), "No kakutei tax deduction before occupancy year");
+
+        // Act 2: Simulate Year 2028 (occupancy year)
+        SimulationContext context2028 = new SimulationContext();
+        context2028.setYear(2028);
+        context2028.setMortgageBalances(context2027.getMortgageBalances());
+        context2028.setMortgageMonthlyPayments(context2027.getMortgageMonthlyPayments());
+        context2028.setMortgageCurrentRates(context2027.getMortgageCurrentRates());
+        context2028.setMortgageCurrentExpectedPayments(context2027.getMortgageCurrentExpectedPayments());
+        context2028.setMortgageMonthsSinceStart(context2027.getMortgageMonthsSinceStart());
+
+        MortgageYearlyResult result2028 = calculator.simulateMortgagesForYear(context2028, List.of(m), nowMonth, null);
+
+        // Assert 2: In 2028 (occupancy year), kakuteiDeduction is awarded, yearEndDeduction is 0
+        assertTrue(result2028.kakuteiDeduction() > 0, "Kakutei deduction applies in occupancy year");
+        assertEquals(0L, result2028.yearEndDeduction(), "No year-end deduction in first occupancy year");
+    }
+
     @Test
     void testSimulateMortgagesForYear_ReducePaymentOverride_RecalculatesAndReducesMonthlyPayment() {
         // Arrange
diff --git a/finance-hub-backend/todo/pending fix/issue-146.md b/finance-hub-backend/todo/pending fix/issue-146.md
index 72627972..33247ba9 100644
--- a/finance-hub-backend/todo/pending fix/issue-146.md
+++ b/finance-hub-backend/todo/pending fix/issue-146.md
@@ -29,3 +29,106 @@
 - 做法：另外用一個欄位或旗標記錄「這筆房貸的減稅資格是否已經生效」，之後任何計算都檢查這個旗標，不是每次都重新比對年份。
 - 優點：讓這個不變條件之後不會因為年份判斷邏輯被改動而重新破功。
 - 缺點：改動範圍比方向A大，需要新增持久化的狀態。
+
+---
+
+## 決策方案：徹底拔除不合理之 Fallback，以「入住日（occupancyDate）」為減稅唯一法定基準與下限
+
+### 一、決策背景與核心理由
+
+1. **法理唯一基準**：
+   依據日本國稅廳（タックスアンサー No.1211-1）與《租稅特別措置法》第 41 條，住宅借入金等特別控除的法定資格起算點**唯有「居住の用に供した日（實際入住日）」**，貸款動用日（`startDate`）根本不是減稅資格要件。
+2. **領域模型一致性（Fallback 毫無道理）**：
+   前端 UI（[`mortgages/page.tsx:561`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L561)）在使用者勾選「Eligible for Mortgage Tax Deduction」時，**入住日期（Occupancy Date）本來就是必填欄位（有必填紅星 `*`，未填寫連儲存按鈕都無法按下）**；後端儲存時也是以 `occupancyDate` 計算截止年份 `endYear`。
+   因此，原代碼中：
+   ```java
+   LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
+   ```
+   這個對 `startDate` 的 fallback 毫無業務合理性，純粹是早期容錯留下的殘留代碼，將借款與入住和稀泥混在一起。
+3. **一石二鳥的架構純化**：
+   徹底拔除 fallback，將減稅資格完全鎖定於 `occupancyDate`。減稅有效年度區間天然即為 `[occupancyDate.getYear(), endYear]`。尚未入住之年份（`year < occupancyDate.getYear()`）連減稅門檻都不符合，Issue 146 的偷跑減稅缺陷自然徹底根除。
+
+---
+
+### 二、具體實作規劃
+
+在 [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 的稅務計算區塊中：
+
+1. **拔除 `refDate` 對 `startDate` 的 fallback**，直接使用 `m.getOccupancyDate()`。若無入住日或未到期，減稅額穩固維持 `0L`。
+2. **加上對稱下限門檻**：`year >= occupancyDate.getYear()`。
+3. **對齊 Issue 137 的雙通道派發**：
+   - 入住首年（`year == occupancyDate.getYear()`）：額度進入 `kakuteiDeduction` 通道（隔年春季退稅）。
+   - 次年起（`year > occupancyDate.getYear()`）：額度進入 `yearEndDeduction` 通道（當年年末調整）。
+   - 入住年之前（`year < occupancyDate.getYear()`）：不進入減稅計算，額度為 0。
+
+#### 核心代碼調整示意：
+```java
+if (Boolean.TRUE.equals(m.getTaxDeductionEligible())) {
+    LocalDate occupancyDate = m.getOccupancyDate();
+    Integer endYear = m.getTaxDeductionEndYear();
+
+    // 嚴格檢驗：必須有入住日、必須有截止年，且當前年度必須落在 [入住年, 截止年] 之法定合法區間內
+    if (occupancyDate != null && endYear != null 
+            && year >= occupancyDate.getYear() && year <= endYear) {
+
+        double rate = m.getTaxDeductionRate();
+        long deduction = (long) (balance * rate);
+        long maxDeduction = m.getMaxTaxDeductionAmount();
+        if (deduction > maxDeduction) {
+            deduction = maxDeduction;
+        }
+
+        // 首年確定申告 vs 次年起年末調整
+        if (year == occupancyDate.getYear()) {
+            kakuteiDeduction = deduction;
+            yearEndDeduction = 0L;
+        } else {
+            yearEndDeduction = deduction;
+            kakuteiDeduction = 0L;
+        }
+
+        MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(occupancyDate);
+        if (deductionStrategy != null) {
+            maxTotalIncomeForMortgageDeduction = deductionStrategy.getMaxEligibleTotalIncome();
+        }
+
+        InhabitantTaxLimitStrategy currentStrategy = new Pre2022InhabitantTaxLimitStrategy();
+        if (occupancyDate.getYear() >= 2022) {
+            currentStrategy = new Post2022InhabitantTaxLimitStrategy();
+        }
+        if (currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()) {
+            bestStrategy = currentStrategy;
+        }
+    }
+}
+```
+
+---
+
+## 修復總結報告 (Resolution Report)
+
+### 1. 核心修復概述
+- **拔除不合理之 Fallback**：廢除 `refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate()` 的舊有容錯寫法，確立住宅ローン控除 100% 嚴格以入住日（`occupancyDate`）為唯一法定基準。若未設定入住日，減稅資格不成立。
+- **補齊對稱年份下限**：在 `MortgageCalculator.java` 中將減稅條件收斂為 `year >= occupancyDate.getYear() && year <= endYear`，嚴格限制扣除額僅於法定有效期間內產生。
+- **杜絕入住前偷跑減稅**：在貸款已動用但尚未入住之年度（`year < occupancyDate.getYear()`），完全不進入減稅計算，`yearEndDeduction` 與 `kakuteiDeduction` 穩固維持 `0L`，徹底防止因 `else` 誤判導致在入住前預先領取年末調整減稅、進而造成總減稅年數超額膨脹之缺陷。
+- **首年與次年雙通道派發**：
+  - 首年入居（`year == occupancyDate.getYear()`）：扣除額全數派發至 `kakuteiDeduction` 通道（依 Issue 137 機制於隔年春季退稅）。
+  - 次年起（`year > occupancyDate.getYear()`）：扣除額派發至 `yearEndDeduction` 通道（當年年末調整直接折抵）。
+
+### 2. 修改檔案清單
+1. [`finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
+   - 移除 `refDate` 對 `startDate` 的 fallback，改為直接提取 `m.getOccupancyDate()`。
+   - 增加 `year >= occupancyDate.getYear()` 下限門檻，確保只有在 `[occupancyDate.getYear(), endYear]` 區間內才執行減稅計算。
+   - 稅務策略派發 `taxStrategyFactory.getStrategy(...)` 與住民稅限額判斷改為直接傳入 `occupancyDate`。
+2. [`finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)
+   - 新增單元測試 `testSimulateMortgagesForYear_PriorToOccupancyYear_NoTaxDeduction`：
+     - 測試情境：房貸於 2027 年 11 月啟動並還款，但入住日為 2028 年 2 月。
+     - 斷言 1：2027 年模擬（未入住）時，`yearEndDeduction` 與 `kakuteiDeduction` 嚴格為 `0L`。
+     - 斷言 2：2028 年模擬（入住首年）時，`kakuteiDeduction` 正確大於 0，`yearEndDeduction` 為 `0L`。
+
+---
+
+## Diff
+
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                     10037  12.56G   12.69    15:42:57  