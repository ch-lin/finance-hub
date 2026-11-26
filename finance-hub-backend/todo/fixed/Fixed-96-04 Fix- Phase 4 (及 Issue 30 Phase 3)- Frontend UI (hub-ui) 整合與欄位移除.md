# Fixed 96-04 Phase 4 (及 Fixed-95 Phase 3): Frontend UI (hub-ui) 整合與欄位移除

由於 Fixed-95 (最佳化維度拆分) 已完成，我們不再需要進行過渡期的「加總降級」與「單欄位拆分」。本階段將直接在前端導入獨立的成長枠與積立枠欄位，並將已廢棄的舊欄位徹底移除。

## 審查重點 (User Review Required)

> [!IMPORTANT]
> 前端將會徹底捨棄舊有的 `monthlyNisaContribution` 與 `testNisaMonthlyContribution`。
> 與 Optimizer 之間的溝通將直接使用新版的雙欄位。

## Proposed Changes

### 1. Data Type & Hook 徹底替換

#### [MODIFY] [user-profile-form.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx)
- 刪除 `monthlyNisaContribution: string;`。
- 新增 `monthlyGrowthContribution: string;` 與 `monthlyTsumitateContribution: string;`。

#### [MODIFY] [use-finance-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts)
- 在提取資料時，直接綁定 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。
- **不再回填或加總** 舊的 `monthlyNisaContribution`。

### 2. UI Component 更新 (使用既有 ThousandSeparatorInput)

#### [MODIFY] [profile-contributions-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/components/profile-contributions-section.tsx)
- 移除原本單一的 `Expected Monthly NISA (¥)` 欄位。
- 引入 `@/components/ui/thousand-separator-input` 裡的 `ThousandSeparatorInput`。
- 新增兩個輸入框：「NISA 成長投資枠 (月額)」與「NISA つみたて投資枠 (月額)」。
- 增加 Client-side Validation：限制成長枠最大值為 200,000，積立枠最大值為 100,000。若超過則自動修正為上限值並以 `toast.error` 提示。

### 3. Optimizer 與 Sandbox 串接修正

#### [MODIFY] [use-simulation-data.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts)
- `SimulationOverrides` 中移除 `testNisaMonthlyContribution`。
- 加入 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`。
- 在 `fetchSimulation` 中攔截 400 Bad Request。若錯誤代碼或內容包含額度不足的資訊，解析 `totalRemaining` 與 `growthRemaining`，並使用 `toast.error()` 動態渲染錯誤文案：「您目前的 NISA 終身額度僅剩 XXX 萬（其中成長枠剩 YYY 萬）。請調降您的每月成長或積立投入設定，使其總和不超過 ZZZ 萬。」

#### [MODIFY] [use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)
- `GlobalOptimizerResponse` 介面中移除 `testNisaMonthlyContribution` 與 `nisaMonthlyContribution`。
- 新增 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`，以及 `appliedProfileBaselineDetails` 中的 `nisaGrowthMonthlyContribution` 與 `nisaTsumitateMonthlyContribution`。

#### [MODIFY] [global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)
- 將 Optimizer 推薦結果展示區塊中的舊 NISA 欄位，拆分為「NISA 成長投資枠」與「NISA 積立投資枠」兩個顯示項目。

#### [MODIFY] [page.tsx (Simulation)](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx)
- 在 Apply 結果至 Sandbox (`setOverrides`) 的邏輯中，直接套用回傳的 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`。

## Verification Plan

### Manual Verification
1. 進入 Profile 或 Onboarding 頁面，確認 NISA 欄位已分為成長與積立兩個框，並測試輸入驗證（上限 20 萬與 10 萬）。
2. 儲存設定，透過瀏覽器 Network 檢查 Payload，確認舊欄位已被移除，且新雙欄位正確傳遞。
3. 進入 Simulator 頁面執行 Global Optimizer，確認推薦結果顯示為拆分的兩個數值。
4. 點擊 "Apply to Sandbox"，確認覆蓋值正確套用，沒有引發型別錯誤，且圖表重新繪製成功。
5. 嘗試在 Sandbox 中故意填入超額投入，驗證 `toast.error` 是否正確顯示剩餘額度。

# NISA Dual-Dimension Implementation (Fixed-96 Phase 4 & Fixed-95)

## UI Updates
1. **User Profile Section**:
   - Replaced `monthlyNisaContribution` with `monthlyGrowthContribution` and `monthlyTsumitateContribution`.
   - Applied `ThousandSeparatorInput` for better formatting and user experience.
   - Added front-end validation (Growth max: 200,000¥, Tsumitate max: 100,000¥).
2. **Sandbox Simulator**:
   - `use-simulation-data.ts` now uses `testNisaGrowthMonthlyContribution` and `testNisaTsumitateMonthlyContribution` for manual sandbox testing.
   - Implemented dynamic parsing of backend JSON error string (`NisaLimitExceededException`) to provide precise user-friendly English error messages showing available quotas.
3. **AI Optimizer Integration**:
   - Updated `use-global-optimizer.ts` response types to match the backend split NISA output.
   - Restructured `global-optimizer-card.tsx` to display separate cards for optimal NISA Growth and NISA Tsumitate recommendations.
   - Modified the "Apply to Sandbox" logic in `page.tsx` to correctly map dual fields back to the sandbox.

## Backend Context
The backend API was updated to support calculating NISA investments across two separate dimensions (Growth & Tsumitate). All compatibility mappings that summed these dimensions into a single legacy value have been fully purged from the UI client.
