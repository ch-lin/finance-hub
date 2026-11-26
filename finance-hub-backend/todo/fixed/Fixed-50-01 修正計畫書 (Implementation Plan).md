# Fixed-50 修正計畫書 (Implementation Plan)

## 目標 (Goal)
修復過渡年（Transition Year）中，因系統將「當月」強制視為未來並無條件推算，導致若當月薪資單已提前輸入系統時，會發生「當月薪資被重複計算兩次（全年變成13個月）」的溢算 Bug。

## 架構設計考量 (Design Decisions)
> [!IMPORTANT]
> **資料驅動的邊界判定**：我們不依賴「今天是幾月幾號」來瞎猜發薪了沒，而是直接在建構 `SimulationContext` 時，掃描使用者的真實 `SalaryRecord`。若發現裡面已經包含當月份的薪資，就明確標記 `currentMonthSalaryRecorded = true`，藉此動態決定未來的推算月份數（`monthsToAdd`）。
> **過去缺漏不補算**：若使用者漏記了前幾個月的帳，系統維持現有的標準作法（不自動補算過去），因為 YTD 的定義是反映真實已發生的帳務，模擬器的責任僅限於推算未來。

---

## Proposed Changes

### 1. `SimulationContext.java`
新增一個狀態旗標，用來讓 Pipeline 知道當月薪水是否已經躺在 YTD 裡面了。

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- 新增 boolean 屬性：
  ```java
  private boolean currentMonthSalaryRecorded;
  ```

---

### 2. `SalaryContextBuilder.java`
在加總 YTD 薪水的同時，順手檢查是否有當月的紀錄。

#### [MODIFY] [SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)
- 取得當前月份：`int currentMonth = YearMonth.now().getMonthValue();`
- 新增區域變數：`boolean currentMonthSalaryRecorded = false;`
- 在遍歷 `salaryRecords` 的迴圈中，當 `sr.getPaymentDate().getYear() == currentYear` 時，進一步檢查：
  ```java
  if (sr.getPaymentDate().getMonthValue() == currentMonth) {
      currentMonthSalaryRecorded = true;
  }
  ```
- 迴圈結束後，將結果存入 Context：
  ```java
  context.setCurrentMonthSalaryRecorded(currentMonthSalaryRecorded);
  ```

---

### 3. `YearlySimulationPipeline.java`
移除死板的推算公式，改為動態判定。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- 修改 `monthsToAdd` 的計算邏輯：
  ```java
  int monthsToAdd = 12;
  int currentMonth = nowMonth.getMonthValue();
  if (age == initialAge) {
      monthsToAdd = context.isCurrentMonthSalaryRecorded() 
              ? 12 - currentMonth       // 當月已入帳，未來推算不含當月
              : 12 - currentMonth + 1;  // 當月未入帳，未來推算包含當月
  }
  ```

---

## Verification Plan

### Manual Verification
1. **情境 A：尚未發薪 (當月無紀錄)**
   - 準備一份不包含本月份薪資單的資料。
   - 執行模擬，觀察 `monthsToAdd` 是否為 `12 - currentMonth + 1`，且年底的 Liquid Cash 增加了 `monthsToAdd` 個月的推算淨利。
2. **情境 B：已發薪 (當月有紀錄)**
   - 在系統中手動新增一筆本月份的 `SalaryRecord`。
   - 執行模擬，觀察 `monthsToAdd` 是否自動切換為 `12 - currentMonth`，確保全年推算不多不少剛好維持 12 個月，沒有發生 13 個月的溢算。
