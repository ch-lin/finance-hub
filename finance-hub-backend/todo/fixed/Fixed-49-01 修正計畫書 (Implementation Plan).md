# Fixed-49 修正計畫書 (Implementation Plan)

## 目標 (Goal)
修復過渡年中「排定在剩餘月份的職涯事件 (加薪/轉職) 被靜默忽略」的問題。
本次修正秉持「最小改動」原則，保留現有的計算基底，僅針對過渡年的傳參與事件時間處理進行防呆與擴充，以最高安全性解決 Fixed-49 提出的 Pipeline 覆寫問題。

## 架構設計考量 (Design Decisions)
> [!IMPORTANT]
> **維持現有計算單位**：本計畫保留既有的 `totalYearlySalary += 年薪 * 適用月數`，並在迴圈結束後統一 `/ 12` 的演算法。此做法能保證非過渡年的歷史模擬結果 100% 不受影響。
> **單一方法呼叫**：為了避免 `SimulationContext` 中的 `currentAnnualSalary` 狀態被重複更新，我們在 `YearlySimulationPipeline` 中會透過 `if/else` 分流，確保每年只會呼叫一次 `calculateSalaryForYear`，防止狀態被錯誤疊加。

## 待確認風險 (Open Questions)
> [!WARNING]
> **`RELATIVE_CHANGE` (相對薪資變動) 的數值定義可能存在隱患**
> 
> 目前原始碼中處理相對薪資變動的實作為：
> `newSalary = (long) (salaryAtLastMonth * event.getEventValue());`
> 
> **潛在風險說明**：
> 這個乘法邏輯高度依賴資料庫/前端對 `eventValue` 的定義約定。
> - 如果系統約定「加薪 10%」需儲存為 `1.1`，則 `salary * 1.1` 計算結果正確。
> - 如果系統約定（或使用者在前端直接輸入）「加薪 10%」是儲存為 `0.1`，那麼目前的程式碼會計算為 `salary * 0.1`，導致薪水瞬間暴跌為原本的 10%（等同於變相減薪 90%）。
> 
> **建議行動**：
> 本次實作專注於修復 Fixed-49 核心問題，為尊重最小改動原則，暫不更動此乘法邏輯。但強烈建議下一位 Reviewer 盤點 API 契約，或檢查資料庫內 `CareerEvent` 實際的資料格式。若發現系統實際儲存格式為 `0.x`，則必須另開 Issue 將邏輯修正為 `salaryAtLastMonth * (1.0 + event.getEventValue())` 以策安全。

---

## Proposed Changes

### 1. `IncomeExpenseCalculator.java`
擴充薪資計算邏輯，使其能精確接受過渡年參數，並加入排序與過往狀態繼承的防呆機制。

#### [MODIFY] [IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)
- **新增 Import**: `import java.util.Comparator;`
- **方法多載 (Method Overloading)**:
  - 保留原有介面：`public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents)`，內部直接轉呼叫 `calculateSalaryForYear(context, careerEvents, null, null)`，確保既有呼叫端完全不受影響。
- **主邏輯擴充**: `public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents, Long ytdSalary, Integer monthsToAdd)`
  - **防呆 1 (強制排序)**：將傳入的 `careerEvents` 依照發生年月排序，避免外部傳入順序錯亂時，導致「時間旅行」的負數月份 Bug。
    ```java
    List<CareerEvent> sortedEvents = careerEvents.stream()
            .sorted(Comparator.comparing(CareerEvent::getEventYearMonth))
            .toList();
    ```
  - **防呆 2 (起始月與過往狀態繼承)**：透過 `monthsToAdd` 反推 `startMonth`。當迴圈遇到 `eventMonth < startMonth`（發生在過渡年早期的事件）時，**只更新 `salaryAtLastMonth` 而不加入剩餘薪資加總**，藉此完美修復「靜默吃掉加薪」的問題，同時避免將已發生在 YTD 中的金額重複計算。
  - **精確回傳**：若傳入了 `ytdSalary`，則最終回傳 `ytdSalary + 剩餘月分推算結果`。

---

### 2. `YearlySimulationPipeline.java`
移除過渡年的強制覆寫，改為傳遞真實參數給計算機。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **單一化呼叫**：
  原本的程式碼先無條件呼叫了一次 `calculateSalaryForYear`，然後在過渡年又強制把結果洗掉。
  我們將改為：
  ```java
  long annualizedSalaryRate;
  long effectiveAnnualSalary;

  if (age == initialAge) {
      // 過渡年：傳入 YTD 與 剩餘月份，由計算機負責精確結算
      effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
              context, careerEvents, context.getYtdBaseSalary(), monthsToAdd);
      // 取出年底結算後的最新年薪率
      annualizedSalaryRate = context.getCurrentAnnualSalary(); 
  } else {
      // 完整年：直接推算整年
      annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents, null, null);
      effectiveAnnualSalary = annualizedSalaryRate;
  }
  ```
  如此可確保 `SimulationContext` 內的薪水與增長狀態在同一年內不會被重複疊加。

---

## Verification Plan

### Manual Verification
1. **過渡年加薪測試 (核心情境)**：設定模擬從今年 9 月開始，並設定 11 月發生 100 萬的「絕對值加薪」。驗證今年度的 `effectiveAnnualSalary` 是否精確包含了 9~10 月的舊薪水以及 11~12 月的 100 萬薪水。
2. **過渡年早期加薪測試 (修復吞噬問題)**：設定模擬從今年 9 月開始，但加薪事件設在今年 4 月。驗證系統是否能正確以「加薪後」的薪資水準來推算 9~12 月的薪水，並且不會把 4 月的事件錯誤加進總數。
3. **無事件的標準年測試**：驗證隔年（無任何新事件）的 `annualizedSalaryRate` 是否正確繼承了第一年年終的最新薪水，確保狀態結算無誤。
