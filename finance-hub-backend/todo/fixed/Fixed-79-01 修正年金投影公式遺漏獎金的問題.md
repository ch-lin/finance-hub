# [Fixed-79-01] 修正年金投影公式遺漏獎金的問題

將年金投影頁面原本只計算月薪的公式，替換為與多年度模擬器相同的公式（套用 `calculateYearlyKouseiBenefitIncrement`），藉此解決同一位使用者在不同頁面看到兩種不同厚生年金數字的矛盾情況。

## Proposed Changes

### SalaryContextBuilder

#### [MODIFY] [SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)
- 定義一個 public record `BonusInfo(long annualBonus, int bonusPaymentCount)` 作為回傳型別。
- 新增獨立方法 `public BonusInfo resolveAnnualBonusInfo()`：
  - 將原本 `initializeSalaryContext` 中「撈取過去 12 個月 Bonus 加總」以及「若 UserSettings 中 `useHistoricalBonusForProjection` 為 false 則採用 `expectedAnnualBonus`」的邏輯（約 15-20 行）原封不動搬移過來。
  - 該方法會自行查詢 `salaryRecordRepository` 與 `salaryUserSettingsRepository`，然後回傳 `BonusInfo`。
- 修改原本的 `initializeSalaryContext` 方法，讓它呼叫這個新方法來獲取獎金資訊，避免邏輯重複。

---

### PensionProjectionServiceImpl

#### [MODIFY] [PensionProjectionServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
- 透過 Constructor 注入 `SalaryContextBuilder`。
- 在 `calculateProjection` 方法中：
  - 於迴圈前，呼叫 `salaryContextBuilder.resolveAnnualBonusInfo()` 取得 `annualBonus` 與 `bonusPaymentCount`。
  - 將目前獲取的 `expectedSalary` 乘以 12，轉換為 `annualBaseSalary`。
  - 在 `for` 迴圈中，移除原本的 `calculateProjectedKouseiPension` 呼叫。
  - 改為：先計算 `i * pensionCalculator.calculateYearlyKouseiBenefitIncrement(annualBaseSalary, annualBonus, bonusPaymentCount)` 得到未來的增額。
  - 將增額加上 `currentKouseiAmount` 後，再乘上 `pensionCalculator.calculatePensionMultiplier(pensionStartAge)`，完成正確的加總。

---

### PensionCalculator

#### [MODIFY] [PensionCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java)
- 直接刪除 `calculateProjectedKouseiPension` 方法，徹底清除不完整的舊公式。

## Verification Plan

### Automated Tests
- **不執行任何測試與編譯**：依照指示，因刪除舊方法造成的編譯失敗為預期內的問題。目前的範圍僅專注於核心邏輯的抽離與替換，編譯修正將留待下一階段處理。
