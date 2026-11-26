# Fixed 80-03 修正計畫：最佳化結果畫面精準顯示套用的 Profile 數值

解決 Fixed-80-02 中，Global Optimizer 結果畫面的提示文字判斷邏輯不精確的問題，確保顯示的訊息完全反映這次最佳化實際發生的情況。為了讓使用者能清楚看見「未勾選的維度實際套用了哪個自訂數值」，我們將原本的 boolean 旗標改為直接回傳實際套用的具體數值。

## User Review Required

我們將變更後端 API 的回傳結構，把原本單一的 boolean flag 改為一個包含具體套用數值的 Object。
> **Note: Product Direction Decision by User**
> 雖然原 issue 描述中提到「記錄四個布林值」，但經過 User 審閱與指示，產品整體的方針應為「直接將實際套用的具體數值包在回傳值裡面傳回前端」，這能提供最佳的使用者體驗。此項設計變更是 User 敲定的最終產品方向，特此註明以對齊實作與需求。

## Proposed Changes

### Backend

#### [MODIFY] [SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
- 將原有的 `Boolean usedProfileBaseline` 欄位移除。
- 新增一個內部 record `ProfileBaselineUsageDetails`，包含四個欄位：
  - `Integer targetRetirementAge`
  - `Integer pensionStartAge`
  - `Integer idecoStartAge`
  - `Long nisaMonthlyContribution`
  用來記錄實際從 Profile 取用的具體數值。若未取用該欄位，則值為 `null`。
- 在 `SimulationOverrides` 建構子中加入 `ProfileBaselineUsageDetails appliedProfileBaselineDetails`。

#### [MODIFY] [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
- 在 `optimize()` 方法的尾端，計算並蒐集實際取用自 Profile 的數值。
- 檢查產生的 `seed` 中哪些項目是被鎖定的，且對應的 Profile 欄位有值。若有，則將該數值存入對應的 `Integer` / `Long` 變數中，最後封裝進 `ProfileBaselineUsageDetails`。
- 將計算好的 `ProfileBaselineUsageDetails` 傳給 `SimulationOverrides`。

### Frontend

#### [MODIFY] [use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)
- 更新 `GlobalOptimizerResponse` 介面，將 `usedProfileBaseline` 替換為 `appliedProfileBaselineDetails: { targetRetirementAge?: number; pensionStartAge?: number; idecoStartAge?: number; nisaMonthlyContribution?: number; } | null`。

#### [MODIFY] [global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)
- 在 `OptimizerResultPanel` 中，加入「是否全部項目皆勾選最佳化」的判斷（透過傳入的 `config` 檢查各項目是否皆為 true）。若全部勾選最佳化，則完全隱藏該提示訊息框。
- 若有收到 `appliedProfileBaselineDetails` 且裡面有值，則根據傳回的數值，在畫面上明確列出：例如「Applied your custom Profile baseline ages to dimensions you didn't optimize: Retirement (60), Pension (65).」。
- 若完全沒有取用到 Profile 數值（例如取消勾選但 Profile 空白），則顯示：「Statutory baseline defaults ... were applied ... (either because Profile overriding is disabled or the specific Profile fields are empty).」。

### Tests

#### [MODIFY] [GlobalOptimizerServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)
- 新增三個 Unit Test 案例，嚴格覆蓋報告中要求的驗證情境：
  1. **「全部項目都勾選要最佳化」**：驗證回傳的 `appliedProfileBaselineDetails` 為空（null 或無任何數值），確保不會顯示錯誤訊息。
  2. **「取消勾選某一項，且 Profile 該欄位有填值」**：驗證回傳的 `appliedProfileBaselineDetails` 確切包含該項目的 Profile 數值。
  3. **「取消勾選某一項，但 Profile 該欄位是空白」**：驗證回傳的 `appliedProfileBaselineDetails` 為空，確認系統正確 fallback 且不標示為使用了 Profile。

## Verification Plan

### Automated Tests
您可以使用以下指令來執行單元測試：
`cd finance-hub/finance-hub-backend && ./gradlew :hub-app:test --tests "ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.GlobalOptimizerServiceTest"`

### Manual Verification
- 啟動前後端，於瀏覽器中開啟 Global Optimizer 畫面。
- 實際操作驗證上述三種情境，觀察提示框的文案與動態插入的數值是否正確反映。


# Walkthrough: Fixed 80-03 (Optimizer Profile Values)

## Overview
This issue has been resolved by returning the exact values applied from the user's Profile directly to the frontend, instead of a confusing single boolean flag.

## Changes Made

### Backend
1. **[SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)**
   - Replaced `Boolean usedProfileBaseline` with a new record: `ProfileBaselineUsageDetails appliedProfileBaselineDetails`.
   - The new record explicitly holds the values applied: `targetRetirementAge`, `pensionStartAge`, `idecoStartAge`, and `nisaMonthlyContribution`.
2. **[GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)**
   - Updated the end of the `optimize()` method to evaluate the seed locks and properly extract which Profile fields were actually used during optimization baseline construction.
   - Passed these actual values into the `SimulationOverrides`.
3. **[GlobalOptimizerServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)**
   - Updated all old constructor usages.
   - Added 3 new unit tests ensuring that when configurations are skipped, the proper Profile details are accurately mapped and returned.

### Frontend
1. **[use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)**
   - Replaced `usedProfileBaseline` in the `GlobalOptimizerResponse` interface with `appliedProfileBaselineDetails`.
2. **[global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)**
   - Now checks if all optimization configs are checked (`allOptimized`); if so, completely hides the info block.
   - If not all are optimized, it dynamically checks if `appliedProfileBaselineDetails` contains any data and prints exactly which dimensions fell back to custom profile values.

## Verification
I have intentionally left the actual unit test execution to you, as requested (`單元測試我跑，改掉`).
You can verify the backend correctness by running:
```bash
cd finance-hub/finance-hub-backend && ./gradlew :hub-app:test --tests "ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.GlobalOptimizerServiceTest"
```
