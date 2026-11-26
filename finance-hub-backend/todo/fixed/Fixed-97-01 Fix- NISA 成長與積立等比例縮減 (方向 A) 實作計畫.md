# Fixed-97-01 Fix: NISA 成長與積立等比例縮減 (方向 A) 實作計畫

這個計畫旨在解決 NISA 在觸及 1800 萬總上限時，未按比例縮減「成長枠」與「積立枠」的分配額度，導致最終總和超出法定上限的 Bug。
本實作採取**「最小範圍改動」**，接受單筆基金在 `(long)` 強制轉型時可能產生的 1~2 塊錢零頭捨去落差（Expected Behavior），以換取最乾淨的程式碼架構。

## Proposed Changes

### NISA Allocation Calculator

#### [MODIFY] [TaxAdvantagedAccountProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)

**修正總額比例算法（約第 122-125 行附近）**：
在計算 `growthScale` 和 `tsumitateScale` 之前，先以整數運算檢查 `allowedGrowth + allowedTsumitate` 是否超過了 `combinedAllowed`。
如果超過，就按原本意圖的比例，在整數層級將兩者強制縮小至剛好等於 `combinedAllowed`：
- `finalAllowedGrowth = (allowedGrowth * combinedAllowed) / (allowedGrowth + allowedTsumitate)`
- `finalAllowedTsumitate = combinedAllowed - finalAllowedGrowth`

最後將這兩個修正後的值，放進原本的 `growthScale` 與 `tsumitateScale` 的分子中。**完全不改動**下方的單筆基金分配迴圈。

## Verification Plan

### Automated Tests
- **由使用者手動執行測試**：待程式碼修改完成後，由使用者透過 `mvn` 指令執行測試，確保 NISA 相關邏輯修復且既有功能未被破壞。

### Manual Verification
- 不適用，將完全透過單元測試與程式碼邏輯審查來驗證。

# Fixed-97-01 Fix: NISA 成長與積立等比例縮減 (Walkthrough)

## Changes Made

- **TaxAdvantagedAccountProjector.java (約 117-125 行附近):**
  - 實作了 NISA 在觸及 1800 萬（或年度上限）總額時的**等比例縮減**邏輯。
  - 當 `allowedGrowth + allowedTsumitate` 加總超過 `combinedAllowed` 上限時，使用整數運算 `(allowedGrowth * combinedAllowed) / (allowedGrowth + allowedTsumitate)` 將 `allowedGrowth` 依比例縮小，而 `allowedTsumitate` 則吸收剩下的總額（`combinedAllowed - finalAllowedGrowth`）。
  - 將這兩個修正後、符合總上限的 `finalAllowedGrowth` 與 `finalAllowedTsumitate` 放回原本的比例係數 `growthScale` 與 `tsumitateScale` 中。
  - 成功從源頭堵死了當初因為係數引用錯誤（限制前的原始額度）而導致總投資金額暴衝 160 萬的 Bug。
  - 單筆基金分配迴圈 **未被修改**，維持最小改動範圍，小數點因 `(long)` 型別轉換捨去造成的一至兩日圓落差屬於預期內。

## Verification
- 程式碼已實作完畢，請使用者親自透過 `mvn` (或 Gradle) 執行自動化單元測試，驗證邏輯與修復結果。
