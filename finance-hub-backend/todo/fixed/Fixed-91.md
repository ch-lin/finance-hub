# Fixed 91：最佳化引擎估算「房貸最晚哪一年還完」時，沒扣掉「縮短年限」提前還款已經省下的月數

## 嚴重程度
Low。這個問題只會讓最佳化搜尋的年份範圍比實際需要的更寬（多搜尋幾個房貸其實已經還完的年份），不會讓搜尋漏掉真正的最佳解——因為真正最好的提前還款年份，一定還是落在這個偏寬的範圍裡面。這純粹是搜尋效率的問題，不影響搜尋結果對不對。

## 背景知識：房貸提前還款有兩種模式，「縮短年限」省下的月數是使用者自己輸入的歷史數字，不是系統算出來的
房貸提前還款在這個系統裡分兩種模式（`EarlyRepaymentType`）：
- **縮短年限（`REDUCE_TERM`）**：提前還一筆本金之後，每月要付的金額不變，但貸款會提早還完——也就是總還款期數（月數）變少了。
- **減少月付金額（`REDUCE_PAYMENT`）**：期數不變，但每月要付的金額變少。

每一筆「縮短年限」的提前還款，實際會讓還款期數減少幾個月，因為各家銀行計算方式細節不同，系統本身不會、也不應該自己去推算或猜測這個數字。做法是在畫面上開一個輸入框，讓使用者自己填入銀行實際通知的縮短月數（[mortgages/page.tsx:733-742](hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx:733-742)，選擇「縮短年限」時這個欄位是必填，見[同檔169-171行](hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx:169-171)），存進資料庫的`MortgageEarlyRepayment.reducedMonths`欄位（[MortgageEarlyRepayment.java:86-87](finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageEarlyRepayment.java:86-87)）。整個程式碼裡找不到任何地方是「系統自己算出`reducedMonths`」——這個數字從頭到尾都是使用者自己輸入、代表**已經發生過的實際歷史事件**的既有資料。

## 這個問題的修正，需不需要系統自己去「算」或「猜」未來的縮短月數？——不需要，理由如下
這點值得先講清楚，因為這正是這個問題最容易被誤解的地方。

`MortgageCalculator.java`裡的`calculateAccumulatedReducedMonths`方法（[第495-510行](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:495-510)），做的事情只是「把這筆房貸過去所有已經存在資料庫裡的`REDUCE_TERM`提前還款記錄，把它們的`reducedMonths`（使用者自己填的數字）加總起來」——純粹讀取、加總既有資料，沒有任何推算或猜測的成分：
```java
private int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
    if (m.getEarlyRepayments() == null) {
        return 0;
    }
    int total = 0;
    for (var er : m.getEarlyRepayments()) {
        if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM) {
            YearMonth erYm = YearMonth.from(er.getRepaymentDate());
            if (!erYm.isAfter(currentYm)) {
                Integer reduced = er.getReducedMonths();
                total += (reduced != null) ? reduced : 0;
            }
        }
    }
    return total;
}
```

那最佳化引擎（遺傳演算法、Grid Search）自己在搜尋過程中「假設」使用者未來某一年做一筆新的提前還款時，需不需要知道這筆「未來的」提前還款會縮短幾個月？答案是不需要，因為最佳化引擎模擬「未來的提前還款」用的資料結構，本身就沒有「縮短年限的月數」這個欄位：
```java
public record MortgageEarlyRepaymentOverride(
        Integer year,
        Long amount) {
}
```
（[SimulationOverrides.java:68-71](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:68-71)）

只有「哪一年」跟「還多少錢」兩個欄位，沒有`repaymentType`、也沒有`reducedMonths`。實際套用這筆模擬提前還款到餘額上的程式碼（`MortgageCalculator.getOverrideEarlyRepaymentAmount`，[MortgageCalculator.java:101-127](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:101-127)）也只是把這筆錢從餘額扣掉，完全不會呼叫`calculateAccumulatedReducedMonths`。換句話說，最佳化引擎模擬「未來提前還款」的效果，本來就是用「餘額變少、月付金額不變，貸款自然提早在餘額歸零那一刻結束」這種方式自然反映出「提早還完」的效果，根本不需要事先知道、也不需要去猜未來這筆提前還款的銀行會怎麼算縮短月數。

所以這個issue要修的東西，僅僅是：**讓`OptimizationUtils.calculateMortgageYearRange`也去讀取、加總這筆房貸過去已經存在資料庫裡的`reducedMonths`歷史資料**（跟`MortgageCalculator.java`現有邏輯讀的是同一批資料），不涉及任何對未來的推算或猜測，也不會讓系統多做任何一件「本來要求使用者自己輸入」的事。

## 現狀
房貸核心計算邏輯`MortgageCalculator.java`在計算「這筆房貸剩下還要繳幾個月」（`remainingMonths`）時，會呼叫上面提到的`calculateAccumulatedReducedMonths`，扣掉歷史上「縮短年限」已經省下的月數（呼叫處例如第176、196、219、334、354、383行），確保算出來的「剩餘還款月數」正確反映歷史事實。

但**最佳化引擎**——不論是遺傳演算法用的`GlobalOptimizerServiceImpl.java`（[第244行](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:244)），還是Grid Search用的`GridSearchEngine.java`（[第46行](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java:46)）——在估算「這幾筆房貸全部還完最晚是哪一年、搜尋提前還款年份要搜到哪一年為止」的時候，用的是另一個獨立的方法：`OptimizationUtils.calculateMortgageYearRange`（[OptimizationUtils.java:15-28](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java:15-28)），這個方法完整內容如下：
```java
public static MortgageYearRange calculateMortgageYearRange(List<MortgageRecord> mortgages, int currentYear) {
    if (mortgages == null || mortgages.isEmpty()) {
        return null;
    }
    int minYear = currentYear;
    int endYear = currentYear;
    for (MortgageRecord m : mortgages) {
        int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
        if (mEndYear > endYear) {
            endYear = mEndYear;
        }
    }
    return new MortgageYearRange(minYear, endYear + 1); // +1 allows paying off at the very end
}
```
第22行`m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear()`直接拿房貸「當初設定的總貸款期數」（`loanTermMonths`，貸款開始時就固定不變的數字）去推算最晚還完是哪一年，完全沒有讀取任何`reducedMonths`歷史資料。

結果就是：如果一筆房貸過去做過「縮短年限」的提前還款，`MortgageCalculator.java`算出來的「實際還要繳到幾年」會正確反映出來（比較短），但`OptimizationUtils.calculateMortgageYearRange`給最佳化引擎用的「搜尋範圍最晚到哪一年」，還是用沒扣掉歷史縮短月數的原始期數去算，會比實際情況多算幾年。

## 原因
`calculateAccumulatedReducedMonths`是後來加進`MortgageCalculator.java`裡的一個私有方法，只寫在這一個檔案裡，沒有被抽成一個共用的工具方法。`OptimizationUtils.java`是完全獨立的另一個類別，它的`calculateMortgageYearRange`方法從寫的時候就沒有把「縮短年限」這件事考慮進去，後來`MortgageCalculator.java`加上這個邏輯時，也沒有人同步把同樣的邏輯也加到`OptimizationUtils.java`這邊，導致兩個地方「房貸還要多久才會還完」的算法從此不一致。

## 建議修正
三種可行做法，都只是「讓`OptimizationUtils`也讀取、加總既有的`reducedMonths`歷史資料」，差別只在於程式碼要怎麼組織，列出Pros/Cons供選擇：

**方向A：把`calculateAccumulatedReducedMonths`從`MortgageCalculator.java`搬到共用的`MortgageCalculatorUtil`類別，變成一個公開的靜態方法，兩邊都改成呼叫這個共用方法**
```java
// MortgageCalculatorUtil.java 新增
public static int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
    if (m.getEarlyRepayments() == null) {
        return 0;
    }
    int total = 0;
    for (var er : m.getEarlyRepayments()) {
        if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM) {
            YearMonth erYm = YearMonth.from(er.getRepaymentDate());
            if (!erYm.isAfter(currentYm)) {
                Integer reduced = er.getReducedMonths();
                total += (reduced != null) ? reduced : 0;
            }
        }
    }
    return total;
}

// MortgageCalculator.java：原本的私有方法刪除，呼叫處改成呼叫 MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm)

// OptimizationUtils.java
public static MortgageYearRange calculateMortgageYearRange(List<MortgageRecord> mortgages, int currentYear) {
    if (mortgages == null || mortgages.isEmpty()) {
        return null;
    }
    int minYear = currentYear;
    int endYear = currentYear;
    for (MortgageRecord m : mortgages) {
        int reducedMonths = MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, YearMonth.of(currentYear, 12));
        int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths).getYear();
        if (mEndYear > endYear) {
            endYear = mEndYear;
        }
    }
    return new MortgageYearRange(minYear, endYear + 1);
}
```
- 優點：整個系統只有一份「累積縮短月數」的邏輯，以後這個算法如果需要修改（例如未來新增別的提前還款類型），只要改一個地方，`MortgageCalculator`跟`OptimizationUtils`永遠不會再不一致。
- 缺點：需要改動`MortgageCalculator.java`既有的程式碼（把private方法整個搬走），改動範圍比另外兩個方向大一些。

**方向B：不搬動`MortgageCalculator.java`裡的方法，只在`OptimizationUtils.calculateMortgageYearRange`裡另外寫一份幾乎一樣的加總邏輯**
```java
// OptimizationUtils.java
public static MortgageYearRange calculateMortgageYearRange(List<MortgageRecord> mortgages, int currentYear) {
    if (mortgages == null || mortgages.isEmpty()) {
        return null;
    }
    int minYear = currentYear;
    int endYear = currentYear;
    for (MortgageRecord m : mortgages) {
        int reducedMonths = 0;
        if (m.getEarlyRepayments() != null) {
            for (var er : m.getEarlyRepayments()) {
                if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM) {
                    Integer reduced = er.getReducedMonths();
                    reducedMonths += (reduced != null) ? reduced : 0;
                }
            }
        }
        int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths).getYear();
        if (mEndYear > endYear) {
            endYear = mEndYear;
        }
    }
    return new MortgageYearRange(minYear, endYear + 1);
}
```
- 優點：改動範圍最小，只動`OptimizationUtils.java`一個檔案，完全不碰`MortgageCalculator.java`既有邏輯跟其他呼叫處，風險最低。
- 缺點：系統裡會存在兩份幾乎一樣的加總邏輯，分別放在兩個檔案裡維護。如果之後這個算法本身需要修改，必須記得兩邊都要改，否則就會重演這次「兩邊各自為政、算出不同結果」的狀況。

**方向C：把`MortgageCalculator.java`裡的`calculateAccumulatedReducedMonths`從`private`改成`public static`（不搬移、不複製，只放寬存取範圍），讓`OptimizationUtils`直接呼叫這個既有方法**
```java
// MortgageCalculator.java：把 private 改成 public static（這個方法本身沒有用到任何MortgageCalculator的實例狀態，改成static沒有副作用）
public static int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) { ... }

// OptimizationUtils.java
int reducedMonths = MortgageCalculator.calculateAccumulatedReducedMonths(m, YearMonth.of(currentYear, 12));
int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths).getYear();
```
- 優點：跟方向A一樣只有一份實作（不會重複維護），但不用另外新增或搬動到共用utility類別，改動比方向A更小。
- 缺點：讓原本互相獨立、不相依的`OptimizationUtils`（最佳化工具類別）多了一個對`MortgageCalculator`（房貸核心計算類別）的相依關係——如果之後有人想重構或拆分`MortgageCalculator.java`，會多一個要考慮的呼叫端。

這項修正本身不急迫（只影響搜尋範圍的寬窄，不影響搜尋結果對不對），但建議選一個方向一併補上，讓最佳化引擎跟房貸核心計算邏輯用同一套「房貸剩餘時間」的基準。

---

## 修正報告 (已完成)

**採用的解決方案：方向 A (重構並共用邏輯)**
為了讓最佳化引擎跟房貸核心計算邏輯（`MortgageCalculator`）共用同一套標準，我們選擇了架構最乾淨的「方向 A」，將計算累積縮短月數的邏輯抽離為共用 Util。

**具體實作細節：**
1. **新增共用 Util：**
   在 `MortgageCalculatorUtil.java` 新增了公開的靜態方法 `calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm)`。
2. **核心模擬引擎重構：**
   將 `MortgageCalculator.java` 內原本寫死的 private 方法刪除，並將內部 6 處呼叫全面改為呼叫上述的共用 Util。
3. **修復 OptimizationUtils：**
   在 `OptimizationUtils.java` 計算 `mEndYear` 之前，先呼叫 Util 取得省下的月數（傳入的邊界條件為該年的 12 月：`YearMonth.of(currentYear, 12)`，確保該年內所有的縮短月數都被正確計算），然後將期數修正為 `m.getLoanTermMonths() - reducedMonths`，精準縮小搜尋區間。最後回傳時依舊保留 `endYear + 1` 作為演算法邊界測試的安全護城河。
4. **單元測試與驗證：**
   在 `MortgageCalculatorUtilTest.java` 中補上了對應的單元測試。由於 Entity（`MortgageRecord` 等）有 JPA 的 protected 建構子保護，我們透過 `Mockito` 模擬了各種類型的提前還款紀錄，成功驗證了該方法只會加總 `REDUCE_TERM` 類型且在時間範圍內的縮短月數。
   
使用者已於本地端執行 `mvn clean test` 測試通過，實作無誤。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT  git --no-pager diff                                                                                                                                                      10023  11.93G   8.41    09:57:59  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java
index 1326b509..54e5be45 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java
@@ -23,14 +23,39 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils;
 
+import java.time.YearMonth;
 import java.util.Collections;
 import java.util.List;
 
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.BonusPayment;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
 
 public class MortgageCalculatorUtil {
 
+    /**
+     * Calculates the total number of months that the mortgage term has been reduced
+     * by historical early repayments (REDUCE_TERM type) up to the specified
+     * YearMonth.
+     */
+    public static int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
+        if (m.getEarlyRepayments() == null) {
+            return 0;
+        }
+        int total = 0;
+        for (var er : m.getEarlyRepayments()) {
+            if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM) {
+                YearMonth erYm = YearMonth.from(er.getRepaymentDate());
+                if (!erYm.isAfter(currentYm)) {
+                    Integer reduced = er.getReducedMonths();
+                    total += (reduced != null) ? reduced : 0;
+                }
+            }
+        }
+        return total;
+    }
+
     /**
      * Calculates the fixed component of the monthly payment (legacy signature).
      * Delegates to the bonus-aware version with 0 bonus.
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index 2890ddb5..641fa060 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -177,7 +177,7 @@ public class MortgageCalculator {
                                     int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                             + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                     int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                            - calculateAccumulatedReducedMonths(m, currentYm);
+                                            - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                     remainingMonths = Math.max(1, remainingMonths);
                                     if (remainingMonths > 0) {
                                         long theoreticalNewPayment = MortgageCalculatorUtil
@@ -197,7 +197,7 @@ public class MortgageCalculator {
                                     int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                             + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                     int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                            - calculateAccumulatedReducedMonths(m, currentYm);
+                                            - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                     remainingMonths = Math.max(1, remainingMonths);
                                     if (remainingMonths > 0) {
                                         long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
@@ -220,7 +220,8 @@ public class MortgageCalculator {
                                         int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                                 + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                         int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                                - calculateAccumulatedReducedMonths(m, currentYm);
+                                                - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m,
+                                                        currentYm);
                                         remainingMonths = Math.max(1, remainingMonths);
                                         if (remainingMonths > 0) {
                                             mortgageMonthlyPayments[i] = MortgageCalculatorUtil
@@ -336,7 +337,7 @@ public class MortgageCalculator {
                                 int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                         + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                 int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                        - calculateAccumulatedReducedMonths(m, currentYm);
+                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                 remainingMonths = Math.max(1, remainingMonths);
                                 if (remainingMonths > 0) {
                                     long theoreticalNewPayment = MortgageCalculatorUtil
@@ -355,7 +356,7 @@ public class MortgageCalculator {
                                 int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                         + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                 int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                        - calculateAccumulatedReducedMonths(m, currentYm);
+                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                 remainingMonths = Math.max(1, remainingMonths);
                                 if (remainingMonths > 0) {
                                     long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
@@ -384,7 +385,7 @@ public class MortgageCalculator {
                                 int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                         + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                 int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                        - calculateAccumulatedReducedMonths(m, currentYm);
+                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                 remainingMonths = Math.max(1, remainingMonths);
                                 if (remainingMonths > 0) {
                                     mortgageMonthlyPayments[i] = MortgageCalculatorUtil
@@ -494,22 +495,4 @@ public class MortgageCalculator {
 
         return new MortgageYearlyResult(totalMortgagePaymentsThisYear, mortgageTaxDeductionThisYear, bestStrategy);
     }
-
-    private int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
-        if (m.getEarlyRepayments() == null) {
-            return 0;
-        }
-        int total = 0;
-        for (var er : m.getEarlyRepayments()) {
-            if (er.getRepaymentType() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType.REDUCE_TERM) {
-                YearMonth erYm = YearMonth.from(er.getRepaymentDate());
-                if (!erYm.isAfter(currentYm)) {
-                    Integer reduced = er.getReducedMonths();
-                    total += (reduced != null) ? reduced : 0;
-                }
-            }
-        }
-        return total;
-    }
-
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/InhabitantTaxLimitStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/InhabitantTaxLimitStrategy.java
index d2690ac5..e13724d9 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/InhabitantTaxLimitStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/InhabitantTaxLimitStrategy.java
@@ -3,17 +3,18 @@ package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.st
 public interface InhabitantTaxLimitStrategy {
 
     /**
-     * 計算住民稅可扣抵之房貸上限金額
+     * Calculates the upper limit of the mortgage deduction for inhabitant tax.
      * 
-     * @param taxableIncomeForIncomeTax 所得稅之課稅總所得
-     * @return 上限金額
+     * @param taxableIncomeForIncomeTax the total taxable income for income tax
+     * @return the limit amount
      */
     long calculateLimit(long taxableIncomeForIncomeTax);
 
     /**
-     * 取得法定的絕對固定上限金額（用於多筆房貸比較時，挑選最大上限的策略）
+     * Gets the statutory absolute fixed limit amount (used for selecting the
+     * strategy with the maximum limit when comparing multiple mortgages).
      * 
-     * @return 固定上限金額
+     * @return the fixed limit amount
      */
     long getFixedLimit();
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java
index 2834876c..a2609867 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java
@@ -1,7 +1,9 @@
 package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;
 
+import java.time.YearMonth;
 import java.util.List;
 
+import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils.MortgageCalculatorUtil;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
 
 public final class OptimizationUtils {
@@ -19,7 +21,9 @@ public final class OptimizationUtils {
         int minYear = currentYear;
         int endYear = currentYear;
         for (MortgageRecord m : mortgages) {
-            int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
+            int reducedMonths = MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m,
+                    YearMonth.of(currentYear, 12));
+            int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths).getYear();
             if (mEndYear > endYear) {
                 endYear = mEndYear;
             }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtilTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtilTest.java
index 94cb5376..9aed29ce 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtilTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtilTest.java
@@ -29,8 +29,12 @@ import java.util.List;
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.assertj.core.api.Assertions.within;
 import org.junit.jupiter.api.Test;
+import org.mockito.Mockito;
 
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.BonusPayment;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
 
 class MortgageCalculatorUtilTest {
@@ -108,4 +112,37 @@ class MortgageCalculatorUtilTest {
 
         assertThat(newPayment).isEqualTo(oldPayment);
     }
+
+    @Test
+    void testCalculateAccumulatedReducedMonths() {
+        var m = Mockito.mock(MortgageRecord.class);
+
+        var er1 = Mockito.mock(MortgageEarlyRepayment.class);
+        Mockito.when(er1.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
+        Mockito.when(er1.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2023, 5, 15));
+        Mockito.when(er1.getReducedMonths()).thenReturn(10);
+
+        var er2 = Mockito.mock(MortgageEarlyRepayment.class);
+        Mockito.when(er2.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
+        Mockito.when(er2.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2024, 8, 20));
+        Mockito.when(er2.getReducedMonths()).thenReturn(5);
+
+        var er3 = Mockito.mock(MortgageEarlyRepayment.class);
+        Mockito.when(er3.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_PAYMENT); // different type
+        Mockito.when(er3.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2023, 6, 1));
+        Mockito.when(er3.getReducedMonths()).thenReturn(20);
+
+        var er4 = Mockito.mock(MortgageEarlyRepayment.class);
+        Mockito.when(er4.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
+        Mockito.when(er4.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2025, 1, 10)); // future
+        Mockito.when(er4.getReducedMonths()).thenReturn(12);
+
+        Mockito.when(m.getEarlyRepayments()).thenReturn(List.of(er1, er2, er3, er4));
+
+        // Target: 2024-12. Should include er1 (2023-05) and er2 (2024-08). Should
+        // ignore er3 (wrong type) and er4 (future).
+        int result = MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, java.time.YearMonth.of(2024, 12));
+
+        assertThat(result).isEqualTo(15);
+    }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                                                         10023  12.02G   4.86    10:40:29  