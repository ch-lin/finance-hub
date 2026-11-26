# [Fixed] Feature Requirement 130：`LifeEvent`（使用者自訂支出事件）的`YEARLY`頻率改成月級別計算，比例分攤自然對齊排積木規則

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定：`LifeEvent`的`YEARLY`頻率類型目前對「這一年要不要打折」的判斷方式不夠精確，要直接把整個計算方式升級成月級別，不是修一個侵入性較小的獨立bug——因為現有的比例分攤缺陷，一旦改成月級別，問題本身會直接消失，不需要先修一個之後注定會被蓋掉的中間版本。

## 背景與判斷依據
這是在討論[issue-110](issue-110.md)（教育設定裡生活費/補習費按月分攤問題）時，使用者提議「補習費這種高度不確定、跨國標準不一的支出，不該由教育模組專門處理，使用者需要的話可以自行用系統既有的通用支出事件機制（`LifeEvent`）設定」。查證`LifeEvent`的計算邏輯是否真的能正確表達這種需求時，發現`LifeEvent`目前對`YEARLY`頻率的「重疊月數」判斷方式，用的是一個跟這個事件自己的起訖月份完全無關的變數，導致除了模擬起始那一年以外，未來任何一次這個事件真正的起訖，都不會被正確打折，會整年灌爆全額。

使用者的判斷：這個問題的正確解法不是去修补现有`YEARLY`分支裡「怎麼打折」這個計算細節，而是從根本改變`LifeEvent`看待時間的精度——現有的`startYearMonth`/`endYearMonth`資料模型本身已經精確到月（不是只到年），只是計算邏輯目前繞了一圈用年度概念去近似月份精度；改成真正的月級別計算後，這個事件自然就能跟教育模組既有的「排積木」精神（依實際重疊月數計算，不是依「有沒有重疊到」這種粗略的年度判斷）對齊，不需要為`YEARLY`這個特例維護一套獨立的、後續注定會被取代的分攤公式。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/event/LifeEvent.java`（`startYearMonth`/`endYearMonth`欄位本身已經精確到月，不需要修改資料模型，只需要修改計算方式怎麼使用這兩個欄位）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/event/LifeEventFrequency.java`（`ONE_TIME`、`MONTHLY`、`YEARLY`三種頻率，這次要修改的是`YEARLY`這一種的計算方式，不是資料模型本身）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java:176-213`（`calculateExpensesForYear`裡處理`LifeEvent`的迴圈：第200-201行是`YEARLY`目前的計算方式，第202-212行是`MONTHLY`已經有的、依事件自己`startYearMonth`/`endYearMonth`算出實際重疊月數的正確邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:84-88`（`monthsToAdd`這個變數目前被`YEARLY`分支誤用來判斷「這個事件今年要不要打折」，但這個變數的真實意義是「模擬起始年度，今年已經過去了幾個月、剩下幾個月」，跟`LifeEvent`自己的起訖月份無關；这个變數在模擬起始年度以外永遠是12，導致`YEARLY`事件在真正的起訖年度完全不會被打折）

## 現狀
`LifeEvent`的`YEARLY`頻率類型，判斷「今年要不要打折」的依據是`monthsToAdd`——這個變數只在模擬起始那一年會小於12，其餘所有年份（包含這個事件自己真正開始或真正結束的那一年）永遠是12，導致`YEARLY`事件在除了模擬起始年以外的任何跨年度起訖，都會整年灌爆全額，沒有依這個事件自己的`startYearMonth`/`endYearMonth`去計算實際重疊月數。反觀`MONTHLY`頻率（第202-212行）已經有正確依事件自己起訖月份計算重疊月數的邏輯，只是`YEARLY`沒有套用同一套。

## 具體錯誤情境
使用者設定一個`YEARLY`頻率、`costPerOccurrence`為¥1,200,000的`LifeEvent`，起訖月份是2030年9月到2033年8月：
- 2030年：這個事件只有9-12月（4個月）真正生效，正確應該只算¥1,200,000×4/12=¥400,000。但因為`monthsToAdd`在2030年（假設不是模擬起始年）固定是12，系統會算成整整¥1,200,000。
- 2033年：這個事件只有1-8月（8個月）真正生效，正確應該只算¥1,200,000×8/12=¥800,000。同樣因為`monthsToAdd`固定是12，系統會算成整整¥1,200,000。

3年真實總額應該是¥3,600,000（400,000+1,200,000+1,200,000+800,000），系統會算成¥4,800,000，多算¥1,200,000，高估33%。

## 想要的行為
`YEARLY`頻率的計算方式，改成跟`MONTHLY`共用同一套「依事件自己的`startYearMonth`/`endYearMonth`算出這一年實際重疊月數」的邏輯（`MONTHLY`分支第203-208行已經有這套算法），差別只在最後一步：`MONTHLY`是「每月固定金額×重疊月數」，`YEARLY`改成「年費/12×重疊月數」。不再依賴`monthsToAdd`這個跟事件自身起訖無關的變數去判斷「今年要不要打折」。

## 需要注意的既有考量：模擬起始年度的過渡期，不能因此壞掉
`monthsToAdd`目前同時承擔了兩個不同的職責：（1）「模擬起始年度，今年已經過去的月份不該重複扣款」這個過渡期考量；（2）被`YEARLY`分支誤用來近似「這個事件自己的重疊月數」。改成方案後，`YEARLY`跟`MONTHLY`一樣依事件自己的起訖月份算重疊月數，但**模擬起始年度這個過渡期的考量仍然要保留**——`MONTHLY`分支第204-206行已經示範了這個處理方式（`if (monthsToAdd < 12 && year == context.getCurrentYear()) { startMonth = Math.max(startMonth, currentMonth); }`），`YEARLY`改寫時要套用同樣的過渡期處理，不是只抄「重疊月數」這一半、漏掉過渡期這一半。

## 實作完成報告（Implementation Report）

### 一、實作狀態
- [x] **已完成**：`LifeEvent` 的 `YEARLY` 頻率計算已升級為月級別重疊計算，完全與 `MONTHLY` 共用「依事件自身 `startYearMonth`/`endYearMonth` 算出該年重疊生效月數」之精確邏輯（包含起始過渡年的 `Math.max(startMonth, currentMonth)` 排除已過月份保護機制），以 `Math.round((eventCost / 12.0) * months)` 計算該年分攤支出，並已補充對應完整單元測試。

### 二、改動檔案清單
1. **後端計算邏輯**：
   - **[IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L200-L215)**：
     - 引入 `LifeEventFrequency` 列舉。
     - 合併 `case YEARLY, MONTHLY`，統一使用事件自有的 `startYearMonth` 與 `endYearMonth` 計算當年度生效之重疊月數 `months`，並完整保留模擬起始過渡年度（`monthsToAdd < 12 && year == context.getCurrentYear()`）將 `startMonth` 限制在 `currentMonth` 之後的排除防呆。
     - 當 `months > 0` 時，若頻率為 `YEARLY` 則以 `Math.round((eventCost / 12.0) * months)` 按月比例精準分攤；若為 `MONTHLY` 則依 `eventCost * months` 計算。
2. **後端單元測試**：
   - **[IncomeExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java)**：
     - 新增跨年度 `YEARLY` 事件測試（驗證 2030-09 至 2033-08 年費 ¥1,200,000 在 2030 年得 4 個月 ¥400,000、2031/2032 得 12 個月 ¥1,200,000、2033 得 8 個月 ¥800,000）。
     - 新增同年跨月 `YEARLY` 事件測試（3 至 7 月共 5 個月按月比例折算）。
     - 新增模擬起始過渡年（Transition Year）`YEARLY` 事件測試（驗證跨入過渡年時排除已過月份、已過期事件扣款為 0 等機制）。
     - 新增 `MONTHLY` 事件既有邏輯迴歸測試。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                  10041  12.78G   4.79    13:37:13  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
index 8c2d30be..ff8a0e91 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
@@ -43,6 +43,7 @@ import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.Tra
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
 import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
 import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
+import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 import lombok.RequiredArgsConstructor;
@@ -137,7 +138,7 @@ public class IncomeExpenseCalculator {
         long annualExpenses = context.getBaselineMonthlyExpenses() * monthsToAdd;
         int year = context.getYear();
 
-        Double configInflation = context.getConfig().getInflationRate();
+        Double configInflation = context.getConfig() != null ? context.getConfig().getInflationRate() : null;
         double inflationRate = configInflation != null ? configInflation : 0.0;
         if (inflationRate > 0) {
             int yearsFromNow = year - context.getCurrentYear();
@@ -197,9 +198,7 @@ public class IncomeExpenseCalculator {
                             }
                         }
                     }
-                    case YEARLY ->
-                        annualExpenses += (monthsToAdd < 12) ? Math.round((eventCost / 12.0) * monthsToAdd) : eventCost;
-                    case MONTHLY -> {
+                    case YEARLY, MONTHLY -> {
                         int startMonth = (year == eventStartYear) ? event.getStartYearMonth().getMonthValue() : 1;
                         if (monthsToAdd < 12 && year == context.getCurrentYear()) {
                             startMonth = Math.max(startMonth, currentMonth);
@@ -207,7 +206,9 @@ public class IncomeExpenseCalculator {
                         int endMonth = (year == eventEndYear) ? event.getEndYearMonth().getMonthValue() : 12;
                         int months = endMonth - startMonth + 1;
                         if (months > 0) {
-                            annualExpenses += eventCost * months;
+                            annualExpenses += (event.getFrequency() == LifeEventFrequency.YEARLY)
+                                    ? Math.round((eventCost / 12.0) * months)
+                                    : eventCost * months;
                         }
                     }
                 }
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-130.md b/finance-hub-backend/todo/pending fix/feature-requirement-130.md
index a55c5122..79f27b80 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-130.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-130.md
@@ -29,3 +29,27 @@
 
 ## 需要注意的既有考量：模擬起始年度的過渡期，不能因此壞掉
 `monthsToAdd`目前同時承擔了兩個不同的職責：（1）「模擬起始年度，今年已經過去的月份不該重複扣款」這個過渡期考量；（2）被`YEARLY`分支誤用來近似「這個事件自己的重疊月數」。改成方案後，`YEARLY`跟`MONTHLY`一樣依事件自己的起訖月份算重疊月數，但**模擬起始年度這個過渡期的考量仍然要保留**——`MONTHLY`分支第204-206行已經示範了這個處理方式（`if (monthsToAdd < 12 && year == context.getCurrentYear()) { startMonth = Math.max(startMonth, currentMonth); }`），`YEARLY`改寫時要套用同樣的過渡期處理，不是只抄「重疊月數」這一半、漏掉過渡期這一半。
+
+## 實作完成報告（Implementation Report）
+
+### 一、實作狀態
+- [x] **已完成**：`LifeEvent` 的 `YEARLY` 頻率計算已升級為月級別重疊計算，完全與 `MONTHLY` 共用「依事件自身 `startYearMonth`/`endYearMonth` 算出該年重疊生效月數」之精確邏輯（包含起始過渡年的 `Math.max(startMonth, currentMonth)` 排除已過月份保護機制），以 `Math.round((eventCost / 12.0) * months)` 計算該年分攤支出，並已補充對應完整單元測試。
+
+### 二、改動檔案清單
+1. **後端計算邏輯**：
+   - **[IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L200-L215)**：
+     - 引入 `LifeEventFrequency` 列舉。
+     - 合併 `case YEARLY, MONTHLY`，統一使用事件自有的 `startYearMonth` 與 `endYearMonth` 計算當年度生效之重疊月數 `months`，並完整保留模擬起始過渡年度（`monthsToAdd < 12 && year == context.getCurrentYear()`）將 `startMonth` 限制在 `currentMonth` 之後的排除防呆。
+     - 當 `months > 0` 時，若頻率為 `YEARLY` 則以 `Math.round((eventCost / 12.0) * months)` 按月比例精準分攤；若為 `MONTHLY` 則依 `eventCost * months` 計算。
+2. **後端單元測試**：
+   - **[IncomeExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java)**：
+     - 新增跨年度 `YEARLY` 事件測試（驗證 2030-09 至 2033-08 年費 ¥1,200,000 在 2030 年得 4 個月 ¥400,000、2031/2032 得 12 個月 ¥1,200,000、2033 得 8 個月 ¥800,000）。
+     - 新增同年跨月 `YEARLY` 事件測試（3 至 7 月共 5 個月按月比例折算）。
+     - 新增模擬起始過渡年（Transition Year）`YEARLY` 事件測試（驗證跨入過渡年時排除已過月份、已過期事件扣款為 0 等機制）。
+     - 新增 `MONTHLY` 事件既有邏輯迴歸測試。
+
+## Diff
+
+```diff
+```
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                       10042  12.08G   1.97    13:56:45  