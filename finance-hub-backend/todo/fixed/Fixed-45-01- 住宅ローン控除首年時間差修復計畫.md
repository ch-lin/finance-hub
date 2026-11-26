# Fixed 45: 住宅ローン控除首年時間差修復計畫

這份實作計畫詳述了修復「住宅ローン控除（房貸抵稅）」時間差問題所需的架構調整。目標是精準還原日本稅法：第一年的控除額必須透過次年的「確定申告」才能以退稅形式領取，而非像現有程式碼那樣在第一年透過「年末調整」直接折抵。

## 提議的變更

### `ch.lin.finance.hub.backend.api.app.service.simulation.core.model`

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- **新增狀態變數**: 增加 `private long pendingNextYearTaxRefund;`，用來記錄今年已經算出、但要等到明年才會以現金發放的確定申告退稅款。
- **備註**: 不需修改 `clone()` 方法，因為基本型別 (primitive type) 在呼叫 `super.clone()` 時會自動被正確拷貝。

---

### `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator`

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- **判定是否為適用首年**: 在計算抵稅額的區塊中，判定目前模擬的年度是否為該筆房貸的第一年。我們可以透過檢查房貸的入住日 `occupancyDate`（或退而求其次使用 `startDate`）來精準判斷。
  ```java
  LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
  boolean isFirstEligibleYear = (refDate != null && year == refDate.getYear());
  ```
- **攔截首年抵稅額**: 如果 `isFirstEligibleYear` 為 true，我們把算出來的 `deduction` 加上 `context.getPendingNextYearTaxRefund()`，並把原本的 `deduction` 區域變數強制作為 `0L`。這能確保後續的 `ActiveTaxCalculationEngine` 不會在第一年就把這筆錢拿去抵扣所得稅。

---

### `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline`

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **處理待發放退稅 (確定申告)**: 在 `processSimulationYear` 每年一開始（模擬發薪日與費用之前），新增邏輯來檢查去年是否有遺留下來的退稅款。
  ```java
  if (context.getPendingNextYearTaxRefund() > 0) {
      context.setCurrentLiquidCash(context.getCurrentLiquidCash() + context.getPendingNextYearTaxRefund());
      context.setPendingNextYearTaxRefund(0L);
  }
  ```
- 這個動作完美模擬了使用者在跨年後的 3~4 月收到國稅局的現金退稅款。

## 驗證計畫

### 自動化測試
- 新增/擴充單元測試 (Unit Tests)，確保新邏輯能夠正確判定首年房貸，並驗證首年的房貸抵稅在年末調整時為 `0`，以及使用者的 `liquidCash` 在第二年會正確暴增一筆退稅款。

### 人工驗證
- 變更完成後，請使用者在 Sandbox 中建立一筆全新的房貸，並觀察前兩年的現金流報表，確認第一年的「年末調整」不再包含異常龐大的負值，且次年有一筆無名現金入帳（確定申告退稅）。
