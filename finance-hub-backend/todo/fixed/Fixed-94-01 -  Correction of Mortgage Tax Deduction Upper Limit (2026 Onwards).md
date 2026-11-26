# Fixed 94-01: Correction of Mortgage Tax Deduction Upper Limit (2026 Onwards)

修復 2026 年（含）以後入住的「省節能住宅 (ENERGY_SAVING)」房貸扣除額上限。根據 2026 年新制，貸款餘額上限從 3,000 萬元下修為 2,000 萬元，因此每年的扣除額上限應為 `2,000萬 × 0.7% = 140,000 元`，而非現行的 `210,000 元`。

## Proposed Changes

### Backend Strategy

#### [MODIFY] [Years2026OnwardsTaxStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2026OnwardsTaxStrategy.java)
- 將 `calculateMaxAmount` 方法中 `ENERGY_SAVING` 條件下的回傳值從 `210_000L` 修正為 `140_000L`。

#### [MODIFY] [Years2026OnwardsTaxStrategyTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2026OnwardsTaxStrategyTest.java)
- 更新單元測試中對 `ENERGY_SAVING` 的斷言，將預期的回傳上限改為 `140_000L`。

---

### Documentation

#### [MODIFY] [issue-46.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-46.md)
- 於實作完成後，在檔案中標註已修復完成。

## Verification Plan

### Automated Tests
- 透過 IDE 內建工具執行 `Years2026OnwardsTaxStrategyTest` 確認單元測試通過。

# Walkthrough: Fixed 94-01 (Mortgage Tax Deduction Limit Correction)

## Changes Made
- 修正 `Years2026OnwardsTaxStrategy.java` 中 `calculateMaxAmount` 的邏輯，將 2026 年（含）之後入住的「省節能住宅 (ENERGY_SAVING)」的房貸扣除上限從 `210_000L` 下修為 `140_000L` (即 2000 萬 × 0.7%)。
- 同步修正 `Years2026OnwardsTaxStrategyTest.java` 的相關測試斷言。
- 於 `Fixed-94.md` 標註修復狀態。

## Verification
- 代碼邏輯變更僅限於 2026 年之後入住且類型為 `ENERGY_SAVING` 的房貸，無邊際效應。
- 請執行單元測試 `Years2026OnwardsTaxStrategyTest` 來確認修改生效且無回歸錯誤。
