# Fixed-91-01 Fix OptimizationUtils Mortgage Range Calculation (Approach A)

修復最佳化引擎在估算房貸最晚還款年限時，未扣除「縮短年限 (REDUCE_TERM)」歷史紀錄所省下月數的問題。這會讓最佳化引擎（Grid Search 及 GA 的組合數計算）使用更準確的搜索範圍，避免把運算資源浪費在房貸已經還清的無效年份。

## User Review Required
這是一個純內部架構重構與計算優化，不涉及外部 API 或資料庫 Schema 變更。採用我們剛才討論的**方向 A**（抽出至 `MortgageCalculatorUtil`）。

## Open Questions
- **[已確認]** 這裡假設 `OptimizationUtils` 在呼叫 `calculateAccumulatedReducedMonths` 時，傳入的 `currentYm` 固定為今年 12 月（`YearMonth.of(currentYear, 12)`）。這代表只要是在今年底前（包含 12 月當月）發生的提前還款，都會被正確扣除。此邊界條件已確認無誤，無需再討論。

## Proposed Changes

---

### Core Calculator Util
將 `calculateAccumulatedReducedMonths` 這個純無狀態計算邏輯抽離成共用工具。

#### [MODIFY] [MortgageCalculatorUtil.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java)
- 新增 `public static int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm)` 方法。
- 若有需要，增加對應的 import（`EarlyRepaymentType`, `YearMonth` 等）。

#### [MODIFY] [MortgageCalculatorUtilTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtilTest.java)
- 新增單元測試 `testCalculateAccumulatedReducedMonths` 來驗證該方法是否能正確加總 `REDUCE_TERM` 類型的 `reducedMonths`，並忽略其他類型的提前還款以及未來的提前還款。

---

### Core Simulation Engine
將原本直接寫死在核心模擬引擎裡的 private 方法刪除，改用 Util。

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- 刪除原有的 `private int calculateAccumulatedReducedMonths(...)`。
- 將原先所有呼叫此方法的地方（約 6 處），改為呼叫 `MortgageCalculatorUtil.calculateAccumulatedReducedMonths(...)`。

---

### Optimization Engine
修復年份邊界計算錯誤的問題。

#### [MODIFY] [OptimizationUtils.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java)
- 在計算 `mEndYear` 之前，先呼叫 `MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, YearMonth.of(currentYear, 12))` 取得已經省下的月數 (`reducedMonths`)。
- 將 `m.getStartDate().plusMonths(m.getLoanTermMonths())` 修改為 `m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths)`。
- 新增缺少的 import。

## Verification Plan

### Automated Tests
- 因為您會親自跑測試，實作完成後，請您執行 `mvn clean test` 確保所有既有測試與新加的單元測試皆能通過。

### Manual Verification
- 確認編譯正常無錯誤，且未違反任何既有的 DDD 依賴方向原則。

# Fixed-91-01: OptimizationUtils Mortgage Range Fix

## Changes Made
- **Refactored `calculateAccumulatedReducedMonths`**: Moved this method from a private method in `MortgageCalculator` to a public static utility method in `MortgageCalculatorUtil`.
- **Updated `MortgageCalculator`**: Replaced all 6 internal calls of the old private method to now use the new shared `MortgageCalculatorUtil.calculateAccumulatedReducedMonths`.
- **Fixed `OptimizationUtils`**: Updated `calculateMortgageYearRange` to properly account for `reducedMonths` (historic early repayments) before calculating the final mortgage year. This ensures the grid search optimizer does not search unnecessarily wide ranges.
- **Added Unit Test**: Created `testCalculateAccumulatedReducedMonths` in `MortgageCalculatorUtilTest` to verify the boundary conditions (such as proper type filtering and ignoring future dates).

## Next Steps
As per the implementation plan, please manually run the test suite to verify everything compiles and passes correctly:
```bash
mvn clean test
```
