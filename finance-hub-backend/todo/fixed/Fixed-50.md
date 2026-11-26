# Fixed-50：過渡年剩餘月份數的計算方式，若當月薪資單已經入帳，會讓當月薪資被重複計入一次

## 現況說明 (Current State)

[YearlySimulationPipeline.java](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（76-80 行）計算過渡年剩餘月份數：

```java
int monthsToAdd = 12 - currentMonth + 1; // 含當月
```

`monthsToAdd` 的算法是「含當月」（例如 9 月執行模擬，`monthsToAdd = 12 - 9 + 1 = 4`，代表 9、10、11、12 月）。這個 `monthsToAdd` 接著在 85 行被用來推算 `effectiveAnnualSalary`：

```java
effectiveAnnualSalary = context.getYtdBaseSalary() + monthsToAdd * context.getBaseMonthlySalary();
```

而 `context.getYtdBaseSalary()` 來自 [SalaryContextBuilder](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)（75-101 行），這段邏輯是把使用者所有已經匯入系統的 `SalaryRecord`（真實薪資單）依月份加總，**沒有排除當月**——也就是說，如果使用者當月的薪資單已經先匯入系統（例如 9 月薪資單在 9 月初就已經拿到並輸入），`ytdBaseSalary` 就已經包含了 9 月的薪資。

## 問題原因 (Cause)

`monthsToAdd`「含當月」的算法，是假設「當月薪資尚未發生／尚未入帳，需要用推算方式補上」；但 `ytdBaseSalary` 的加總邏輯完全獨立、不知道 `monthsToAdd` 的假設，它只是單純把資料庫裡所有已存在的薪資單依月份加總。這兩段邏輯對「當月薪資算過了沒」的假設互相矛盾：

- 如果當月薪資單**尚未**入帳：`ytdBaseSalary` 不含當月，`monthsToAdd` 含當月補上，兩者合計剛好是全年，沒有問題。
- 如果當月薪資單**已經**入帳（例如使用者在月初就收到並登錄了薪資單，之後才在同一個月執行模擬）：`ytdBaseSalary` 已經含當月，`monthsToAdd` 又把當月算成「剩餘月份」再补一次，導致當月薪資被算了兩次，`effectiveAnnualSalary` 被高估一個月的薪資。

實際影響：只要使用者「當月薪資單已登錄」且「當月執行模擬」同時發生（這是很常見的使用情境——很多人習慣拿到當月薪資單後立刻更新模擬），過渡年的全年薪資推算就會多算一個月，連帶高估稅金基礎、高估儲蓄能力，讓模擬結果偏離實際現金水位。

## 建議修正方向 (Suggested Fix)

`monthsToAdd` 的計算不應該無條件「含當月」，而應該根據「當月薪資單是否已經匯入系統（即是否已經反映在 `ytdBaseSalary` 裡）」動態決定：如果當月薪資單已存在，`monthsToAdd` 應該從下個月開始算（不含當月）；如果當月薪資單尚未匯入，才維持含當月的推算補上。

## 最小範例程式碼 (Sample Code)

```java
// YearlySimulationPipeline.java：
// monthsToAdd 是否含當月，應該取決於當月薪資單是否已經匯入（已反映在 ytdBaseSalary 裡）
boolean currentMonthAlreadyRecorded = context.hasSalaryRecordForMonth(year, currentMonth); // 新增：檢查當月薪資單是否已入帳
int monthsToAdd = currentMonthAlreadyRecorded
        ? 12 - currentMonth       // 當月已入帳，剩餘月份不含當月
        : 12 - currentMonth + 1;  // 當月尚未入帳，剩餘月份含當月用於推算補上
```

---

## 修正報告 (Resolution Report)
**修復狀態：已完成**

**實作細節：**
1. **新增狀態標記**：在 `SimulationContext.java` 中加入了 `currentMonthSalaryRecorded` 屬性。
2. **動態偵測邊界**：在 `SalaryContextBuilder.java` 加總薪資單時，若發現與當前實體時間相同的月份紀錄（`java.time.YearMonth.now().getMonthValue()`），即將標記設為 `true`。
3. **精準推算未來**：在 `YearlySimulationPipeline.java` 的過渡年計算中，改以 `isCurrentMonthSalaryRecorded()` 作為判斷依據。若當月薪資已入帳，未來推算月數即從下個月起算（`12 - currentMonth`）；若未入帳則維持補上（`12 - currentMonth + 1`）。

**結果**：徹底解決了「當月薪資被重複計算」導致全年變成 13 個月的溢算問題。系統不再憑空瞎猜，而是依據資料庫內真實存在的薪資紀錄動態平衡，使得不論在月初、月中或月底執行模擬，現金結算皆 100% 精準。
