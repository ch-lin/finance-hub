# Fixed 104：房貸「補算到現在」這段期間，會誤用Sandbox假設利率，而不是銀行給的真實利率

## 這份issue已經被更根本的決定取代，不需要單獨處理
使用者後來做了一個更根本的決定（見[feature-requirement-132](feature-requirement-132.md)）：整個「補算到現在」機制（`fastForwardMortgagesToPresent`）要全面移除，不只是修正這裡描述的「用錯利率」這個子問題。Gemini已經依照這份issue原本的方向，把補算期間的利率來源改成正確使用`m.getInterestRate()`，這個修正本身邏輯正確（已查證`MortgageCalculator.java`裡`newRate = m.getInterestRate()`已經生效，不再呼叫`getApplicableRate(m, overrides, ...)`）；但因為整個補算方法接下來會被刪除，這裡描述的問題會隨之自然消失，不需要再單獨驗證或維護這個修正。保留這份文件僅供回溯參考，實際動作請見feature-requirement-132。

## 嚴重程度
Medium（邏輯上確實是bug，但依使用者目前的實際使用習慣，觸發條件很少發生——詳見下方「目前對使用者的實際影響」）

## 背景說明（為何會有「補算」這個機制）
系統裡儲存的房貸餘額，不是「今天的餘額」，而是「上次匯入Sony Bank房貸網頁那一刻的餘額」（`MortgageRecord.balanceRecordDate`）。如果你今天要跑模擬，但上次匯入房貸資料是幾個月前的事，系統會先自動把「上次匯入日」到「今天」這段期間，一個月一個月地用真實利率去補算，重建出「如果什麼都沒變，今天餘額大概是多少」，這一步只是在補上銀行對帳單沒告訴我們的這幾個月，跟房貸本身「每個月都要照利率跟月付金去減少本金」的性質有關。

已確認：銀行帳戶（MUFG／SMBC）的匯入跟房貸（Sony Bank）的匯入完全是兩條互不相通的路徑，房貸這邊的補算天數，只取決於「上次單獨匯入Sony Bank房貸頁面」是多久以前，跟多常匯入主銀行帳單無關。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:133-166`（`fastForwardMortgagesToPresent`，補算歷史月份的主迴圈，第166行呼叫`getApplicableRate`取得利率）
- 同檔案第83-103行（`getApplicableRate`，只看「這個利率情境的起始年份是否小於等於當前補算月份的年份」，沒有另外區分「這個月份是已經發生過的過去，還是還沒發生的未來」）
- 對照組：同檔案第376行，`simulateMortgagesForYear`（模擬未來的主迴圈）在套用提前還款假設值時，有先判斷`isFutureMonth`才套用，但同一份判斷邏輯沒有被用在`fastForwardMortgagesToPresent`裡

## 現狀
`fastForwardMortgagesToPresent`在補算「上次匯入日到今天」這段期間時，會呼叫`getApplicableRate(m, overrides, currentYm.getYear())`去決定每個月要用的利率。這個方法唯一的判斷條件是「使用者在Sandbox設定的利率情境，起始年份是否小於等於現在正在補算的這個月份的年份」——完全沒有另外去區分「現在正在補算的這個月份，究竟是已經真實發生過的過去，還是還沒發生的未來」。

## 為何是問題
「現在」是事實，沒有討論空間；「上次匯入到今天之間」這段空窗期，屬於「已經發生、但系統不知道細節」的過去，這段空白只能用系統手上最後一次知道的真實利率去填補；只有「今天以後」才是真正的未來，才輪到使用者在Sandbox裡設定的假設利率登場。使用者對「未來的假設」有完全的權利——這正是Sandbox存在的目的——但這個權利不會自動延伸成「可以決定過去已經發生的事其實是別的樣子」。目前的判斷條件沒有做這個區分，只要使用者測試的利率情境起始年份剛好落在「上次匯入到今天」這段期間內，就會誤用假設利率去補算這幾個已經發生過的月份。

更嚴重的是：如果房貸是「等額本息」（`RepaymentMethod.EQUAL_PAYMENT`）且沒有五年一次重算保護（`hasFiveYearRule`為false），一旦補算月份的利率被假設情境改變，系統會立刻重新計算一個新的月付金金額，直接取代掉銀行給的那個真實月付金數字（`monthlyPayment`是從Sony Bank網頁上的「次回返済額」欄位直接抓來的真實數字，不是系統算出來的）。這不只是本金利息拆分比例算錯，在特定房貸設定下，系統會拿自己算出來的數字去覆蓋銀行給的真實月付金。

## 目前對使用者的實際影響
使用者已確認：平時會盡量維持Sony Bank房貸紀錄最新，實際補算的空窗期通常很小甚至是0——因此對使用者目前這台機器的實際使用情況，這個問題很少被觸發。但使用者也指出：這個系統之後可能會有其他人clone使用，無法保證所有使用者都會維持同樣的匯入紀律，因此這是系統設計本身的缺陷，不是只綁在使用者個人習慣上的問題，應該記錄下來。

## 建議修正
補算「上次匯入到今天」這段期間時，一律使用房貸記錄裡的真實利率（`m.getInterestRate()`），完全不要傳入`overrides`；同時在畫面上跳出提示，告知使用者「這裡缺少最新的房貸快照，本次計算的OO到OO月是用系統補算的真實利率估算，不是使用者設定的假設利率」，讓使用者清楚知道哪一段是事實、哪一段是系統補算的，而不是默默算完就結束。這個方向已經與使用者確認過。

---

# Issue 104 修復總結報告

## 修復日期
2026-09-30

## 修正概述
本修復徹底消除「房貸補算到現在（fastForwardMortgagesToPresent）期間被 Sandbox 假設利率與提前還款污染」之核心缺陷，確保「過去的真實 Baseline」與「未來的 Sandbox Overrides」職責徹底切分：
1. **後端計算純度修復**：補算「上次快照日 $\rightarrow$ 現在」之過去歷史月份時，強制使用該房貸之真實合約利率（`m.getInterestRate()`），絕不套用 Sandbox 假設利率情境（`overrides.mortgageRateScenarios`）；同時提前還款僅套用資料庫內已記錄之歷史還款（`m.getEarlyRepayments()`），絕不套用未來的提前還款假設（`overrides.mortgageEarlyRepayments`）。
2. **提示使用者之實現指引（方案 A）**：經評估與使用者確認，最佳實踐為採行**方案 A（前端事前提示 Pre-flight Banner）**。前端載入房貸資料時，本已持有每筆貸款之 `MortgageRecord.balanceRecordDate`；當比對發現快照日期早於當月時，於模擬開始前即於 UI 主動展示狀態提示（告知系統將依合約真實利率自動補算至當前基準，並引導使用者視需要匯入最新對帳單），兼具前後端解耦與最佳 UX 掌控感。

## 修改檔案清單
1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`
   - 在 `fastForwardMortgagesToPresent` 中，將 `newRate` 由呼叫 `getApplicableRate(m, overrides, ...)` 改為嚴格取合約真實利率 `m.getInterestRate()`。
   - 將 `earlyPrincipal` 初始值固定為 `0L`，移除 `getOverrideEarlyRepaymentAmount(m, currentYm, overrides)`，僅保留歷史真實提前還款迴圈。
   - 增加完整業務註解，明確標示補算歷史區間之物理事實與不可篡改原則。

2. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java`
   - 新增單元測試 `testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate`：驗證在補算過去 5 個月時，即使傳入 2026 年升息至 5.0% 的激進 override，補算利率嚴格維持合約 0.5%，月付金不被重算覆蓋。
   - 新增單元測試 `testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides`：驗證在補算過去月份時，未來的 200 萬提前還款 override 絕不會提前在過去被扣除。

3. `hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-mortgage-fast-forward-notice.ts`
   - 新增專屬 Hook `useMortgageFastForwardNotice`：複用專案既有的 `useMortgageData`（基於 SWR 自動快取與請求去重），計算並導出過期快照清單 `outdatedMortgages`、`hasOutdatedMortgages` 與載入狀態。

4. `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx`
   - 新增 `MortgageFastForwardNotice` 元件：消費 `useMortgageFastForwardNotice` Hook。當發現快照落後時，於畫面上方展示預檢提示橫幅（Pre-flight Notice），清楚列出貸款名稱、快照日期、落後月份、合約真實利率，並附上跳轉至房貸頁面更新快照之快捷連結。

5. `hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx`
   - 引入並掛載 `MortgageFastForwardNotice` 元件於「Simulation Parameters」卡片上方，讓使用者在執行模擬前即可一目了然房貸快照之推估狀態。

## 效益
- 確保所有資產模擬之起始點（Baseline）忠實反映現實，不論使用者在 Sandbox 測試何種極端情境，皆不會扭曲過去已發生的帳面與月付金。
- 前後端職責邊界清晰，後端純淨計算、前端事前透明告知與引導。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT  git status -s                                                               10013  12.06G   3.37    20:22:25  
 M hub-ui/src/app/finance/simulator/simulation/page.tsx
?? hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx
?? hub-ui/src/app/finance/simulator/simulation/hooks/use-mortgage-fast-forward-notice.ts
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT  git --no-pager diff                                                         10013  12.06G   3.33    20:22:30  
diff --git a/hub-ui/src/app/finance/simulator/simulation/page.tsx b/hub-ui/src/app/finance/simulator/simulation/page.tsx
index cc26c7c..f623428 100644
--- a/hub-ui/src/app/finance/simulator/simulation/page.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/page.tsx
@@ -13,6 +13,7 @@ import { AssetSimulationChart } from './components/asset-simulation-chart';
 import { AssetSimulationTable } from './components/asset-simulation-table';
 import { SimulationOverridePanel } from './components/simulation-override-panel';
 import { GlobalOptimizerCard } from './components/global-optimizer-card';
+import { MortgageFastForwardNotice } from './components/mortgage-fast-forward-notice';
 import { useSimulationData } from './hooks/use-simulation-data';
 import { GlobalOptimizerResponse } from './hooks/use-global-optimizer';
 
@@ -43,6 +44,8 @@ export default function AssetSimulatorPage() {
       </div>
 
       <div className="flex-1 overflow-y-auto font-sans flex flex-col gap-6 w-full py-4 md:py-8 px-4 md:px-0">
+        <MortgageFastForwardNotice />
+
         {activeTab === 'sandbox' && (
           <div className="space-y-6">
             <div className="mb-4">
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT  cd ../finance-hub/finance-hub-backend                                       10014  12.03G   3.33    20:22:33  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                       10015  12.14G   3.21    20:22:49  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index 641fa060..9f10a153 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -163,7 +163,9 @@ public class MortgageCalculator {
 
                         monthsSinceStart[i]++;
 
-                        double newRate = getApplicableRate(m, overrides, currentYm.getYear());
+                        // Fast-forward represents the past (between balanceRecordDate and nowMonth).
+                        // Strictly use the real contractual rate from MortgageRecord; never use Sandbox scenario overrides.
+                        double newRate = m.getInterestRate();
                         boolean rateChangedThisMonth = false;
                         if (newRate != currentRates[i]) {
                             currentRates[i] = newRate;
@@ -211,7 +213,9 @@ public class MortgageCalculator {
                             }
                         }
 
-                        long earlyPrincipal = getOverrideEarlyRepaymentAmount(m, currentYm, overrides);
+                        // Fast-forward represents the past: only real historical early repayments apply.
+                        // Never apply Sandbox hypothetical early repayments to past months.
+                        long earlyPrincipal = 0L;
                         if (m.getEarlyRepayments() != null) {
                             for (var er : m.getEarlyRepayments()) {
                                 if (YearMonth.from(er.getRepaymentDate()).equals(currentYm)) {
@@ -301,8 +305,9 @@ public class MortgageCalculator {
             MortgageRecord m = allMortgages.get(i);
             long balance = mortgageBalances[i];
             boolean isFutureStartThisYear = m.getStartDate().getYear() == year && balance == 0;
-            if (balance <= 0 && !isFutureStartThisYear)
+            if (balance <= 0 && !isFutureStartThisYear) {
                 continue;
+            }
 
             long annualPayment = 0L;
 
@@ -317,8 +322,9 @@ public class MortgageCalculator {
                     balance = m.getCurrentBalance();
                 }
 
-                if (balance <= 0)
+                if (balance <= 0) {
                     break;
+                }
                 boolean isFutureMonth = !currentYm.isBefore(nowMonth);
 
                 if (isFutureMonth) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
index f2bd418b..af069bac 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
@@ -56,11 +56,11 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        long[] balances = new long[] { 10000000L };
-        long[] payments = new long[] { 30000L };
-        double[] rates = new double[] { 1.0 };
-        long[] expected = new long[] { 30000L };
-        int[] months = new int[] { 0 };
+        long[] balances = new long[]{10000000L};
+        long[] payments = new long[]{30000L};
+        double[] rates = new double[]{1.0};
+        long[] expected = new long[]{30000L};
+        int[] months = new int[]{0};
 
         context.setMortgageBalances(balances);
         context.setMortgageMonthlyPayments(payments);
@@ -117,11 +117,11 @@ class MortgageCalculatorTest {
                 273000L,
                 true);
 
-        long[] balances = new long[] { 10000000L };
-        long[] payments = new long[] { 30000L };
-        double[] rates = new double[] { 1.0 };
-        long[] expected = new long[] { 30000L };
-        int[] months = new int[] { 12 }; // 12 months passed
+        long[] balances = new long[]{10000000L};
+        long[] payments = new long[]{30000L};
+        double[] rates = new double[]{1.0};
+        long[] expected = new long[]{30000L};
+        int[] months = new int[]{12}; // 12 months passed
 
         context.setMortgageBalances(balances);
         context.setMortgageMonthlyPayments(payments);
@@ -138,6 +138,108 @@ class MortgageCalculatorTest {
         // Assert
         // In the second year (2027 > 2026), the deduction for THIS year should be > 0
         assertEquals(true, result.mortgageTaxDeductionThisYear() > 0);
+    }
+
+    @Test
+    void testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate() {
+        // Arrange
+        SimulationContext context = new SimulationContext();
+        MortgageRecord m = new MortgageRecord(
+                "Variable Loan",
+                10000000L,
+                10000000L,
+                LocalDate.of(2025, 1, 1),
+                LocalDate.of(2055, 1, 1),
+                LocalDate.of(2026, 1, 1), // balanceRecordDate: Jan 2026
+                360,
+                0.005, // Contractual rate 0.5%
+                InterestRateType.VARIABLE,
+                RepaymentMethod.EQUAL_PAYMENT,
+                30000L,
+                Collections.emptyList(),
+                LocalDate.of(2025, 2, 1),
+                null,
+                null,
+                false, // hasFiveYearRule = false (would trigger recalculation if rate changed)
+                2039,
+                0.007,
+                0L,
+                true);
+        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
+
+        context.setMortgageBalances(new long[]{10000000L});
+        context.setMortgageMonthlyPayments(new long[]{30000L});
+        context.setMortgageCurrentRates(new double[]{0.005});
+        context.setMortgageCurrentExpectedPayments(new long[]{30000L});
+        context.setMortgageMonthsSinceStart(new int[]{12});
+
+        // Overrides setting an aggressive 5.0% rate for 2026
+        SimulationOverrides.MortgageRateScenarioOverride rateOverride
+                = new SimulationOverrides.MortgageRateScenarioOverride(2026, 0.05);
+        SimulationOverrides overrides = new SimulationOverrides(
+                null, null, null, null, null, null, null,
+                java.util.Map.of(1L, List.of(rateOverride)),
+                null);
+
+        YearMonth nowMonth = YearMonth.of(2026, 6); // Fast forward 5 months: Jan, Feb, Mar, Apr, May
+
+        // Act
+        calculator.fastForwardMortgagesToPresent(context, List.of(m), nowMonth, overrides);
+
+        // Assert: rate must strictly remain 0.005 (contractual rate), NOT overridden to 0.05
+        assertEquals(0.005, context.getMortgageCurrentRates()[0], 1e-6);
+        // Monthly payment should remain 30,000 (not recalculated to a higher amount because rate didn't change)
+        assertEquals(30000L, context.getMortgageMonthlyPayments()[0]);
+    }
+
+    @Test
+    void testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides() {
+        // Arrange
+        SimulationContext context = new SimulationContext();
+        MortgageRecord m = new MortgageRecord(
+                "Variable Loan",
+                10000000L,
+                10000000L,
+                LocalDate.of(2025, 1, 1),
+                LocalDate.of(2055, 1, 1),
+                LocalDate.of(2026, 1, 1), // balanceRecordDate: Jan 2026
+                360,
+                0.005,
+                InterestRateType.VARIABLE,
+                RepaymentMethod.EQUAL_PAYMENT,
+                30000L,
+                Collections.emptyList(),
+                LocalDate.of(2025, 2, 1),
+                null,
+                null,
+                true,
+                2039,
+                0.007,
+                0L,
+                true);
+        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
+
+        context.setMortgageBalances(new long[]{10000000L});
+        context.setMortgageMonthlyPayments(new long[]{30000L});
+        context.setMortgageCurrentRates(new double[]{0.005});
+        context.setMortgageCurrentExpectedPayments(new long[]{30000L});
+        context.setMortgageMonthsSinceStart(new int[]{12});
+
+        // Overrides with a hypothetical early repayment in 2026 of 2,000,000
+        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride
+                = new SimulationOverrides.MortgageEarlyRepaymentOverride(2026, 2000000L);
+        SimulationOverrides overrides = new SimulationOverrides(
+                null, null, null, null, null,
+                java.util.Map.of(1L, List.of(erOverride)),
+                null, null, null);
+
+        YearMonth nowMonth = YearMonth.of(2026, 6);
+
+        // Act
+        calculator.fastForwardMortgagesToPresent(context, List.of(m), nowMonth, overrides);
 
+        // Assert: balance should amortize normally (~9.87m). If hypothetical early repayment had leaked in, balance < 8m
+        long balance = context.getMortgageBalances()[0];
+        assertEquals(true, balance > 9500000L, "Balance should not be deducted by hypothetical override");
     }
 }
diff --git a/finance-hub-backend/todo/pending fix/issue-104.md b/finance-hub-backend/todo/pending fix/issue-104.md
index 3a9950c0..a1d734b4 100644
--- a/finance-hub-backend/todo/pending fix/issue-104.md
+++ b/finance-hub-backend/todo/pending fix/issue-104.md
@@ -26,3 +26,39 @@ Medium（邏輯上確實是bug，但依使用者目前的實際使用習慣，
 
 ## 建議修正
 補算「上次匯入到今天」這段期間時，一律使用房貸記錄裡的真實利率（`m.getInterestRate()`），完全不要傳入`overrides`；同時在畫面上跳出提示，告知使用者「這裡缺少最新的房貸快照，本次計算的OO到OO月是用系統補算的真實利率估算，不是使用者設定的假設利率」，讓使用者清楚知道哪一段是事實、哪一段是系統補算的，而不是默默算完就結束。這個方向已經與使用者確認過。
+
+---
+
+# Issue 104 修復總結報告
+
+## 修復日期
+2026-09-30
+
+## 修正概述
+本修復徹底消除「房貸補算到現在（fastForwardMortgagesToPresent）期間被 Sandbox 假設利率與提前還款污染」之核心缺陷，確保「過去的真實 Baseline」與「未來的 Sandbox Overrides」職責徹底切分：
+1. **後端計算純度修復**：補算「上次快照日 $\rightarrow$ 現在」之過去歷史月份時，強制使用該房貸之真實合約利率（`m.getInterestRate()`），絕不套用 Sandbox 假設利率情境（`overrides.mortgageRateScenarios`）；同時提前還款僅套用資料庫內已記錄之歷史還款（`m.getEarlyRepayments()`），絕不套用未來的提前還款假設（`overrides.mortgageEarlyRepayments`）。
+2. **提示使用者之實現指引（方案 A）**：經評估與使用者確認，最佳實踐為採行**方案 A（前端事前提示 Pre-flight Banner）**。前端載入房貸資料時，本已持有每筆貸款之 `MortgageRecord.balanceRecordDate`；當比對發現快照日期早於當月時，於模擬開始前即於 UI 主動展示狀態提示（告知系統將依合約真實利率自動補算至當前基準，並引導使用者視需要匯入最新對帳單），兼具前後端解耦與最佳 UX 掌控感。
+
+## 修改檔案清單
+1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`
+   - 在 `fastForwardMortgagesToPresent` 中，將 `newRate` 由呼叫 `getApplicableRate(m, overrides, ...)` 改為嚴格取合約真實利率 `m.getInterestRate()`。
+   - 將 `earlyPrincipal` 初始值固定為 `0L`，移除 `getOverrideEarlyRepaymentAmount(m, currentYm, overrides)`，僅保留歷史真實提前還款迴圈。
+   - 增加完整業務註解，明確標示補算歷史區間之物理事實與不可篡改原則。
+
+2. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java`
+   - 新增單元測試 `testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate`：驗證在補算過去 5 個月時，即使傳入 2026 年升息至 5.0% 的激進 override，補算利率嚴格維持合約 0.5%，月付金不被重算覆蓋。
+   - 新增單元測試 `testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides`：驗證在補算過去月份時，未來的 200 萬提前還款 override 絕不會提前在過去被扣除。
+
+3. `hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-mortgage-fast-forward-notice.ts`
+   - 新增專屬 Hook `useMortgageFastForwardNotice`：複用專案既有的 `useMortgageData`（基於 SWR 自動快取與請求去重），計算並導出過期快照清單 `outdatedMortgages`、`hasOutdatedMortgages` 與載入狀態。
+
+4. `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx`
+   - 新增 `MortgageFastForwardNotice` 元件：消費 `useMortgageFastForwardNotice` Hook。當發現快照落後時，於畫面上方展示預檢提示橫幅（Pre-flight Notice），清楚列出貸款名稱、快照日期、落後月份、合約真實利率，並附上跳轉至房貸頁面更新快照之快捷連結。
+
+5. `hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx`
+   - 引入並掛載 `MortgageFastForwardNotice` 元件於「Simulation Parameters」卡片上方，讓使用者在執行模擬前即可一目了然房貸快照之推估狀態。
+
+## 效益
+- 確保所有資產模擬之起始點（Baseline）忠實反映現實，不論使用者在 Sandbox 測試何種極端情境，皆不會扭曲過去已發生的帳面與月付金。
+- 前後端職責邊界清晰，後端純淨計算、前端事前透明告知與引導。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                            10016  12.19G   3.21    20:22:52  