# [HIGH] Mortgage rate-scenario stress-test overrides apply identically to every mortgage, including fixed-rate loans

## Severity: High (produces wrong interest/payment/cash-flow for any household with mixed fixed/variable or multiple variable mortgages)

## Summary
`MortgageCalculator.getApplicableRate(originalRate, overrides, currentYear)` selects a rate purely
from `overrides.mortgageRateScenarios()` by calendar year — it never references the mortgage's own
id or its `InterestRateType`. Both call sites (`fastForwardMortgagesToPresent` line 123,
`simulateMortgagesForYear` line 261) invoke it inside the per-mortgage loop using the exact same
shared override list, so a single rate-scenario override is force-applied identically to **every**
mortgage in the household — including ones whose `rateType` is `FIXED` (which by definition never
changes rate), and identically to multiple distinct `VARIABLE` mortgages that should each follow
their own independent rate path.

This is a real architectural inconsistency, not just an edge case: `SimulationOverrides` already
has an established, correct convention for per-mortgage overrides —
`Map<Long, List<MortgageEarlyRepaymentOverride>> mortgageEarlyRepayments`, looked up via
`overrides.mortgageEarlyRepayments().get(m.getId())` — but `MortgageRateScenarioOverride` was
defined as a flat `(Integer startYear, Double rate)` with no mortgage-id field, breaking that
convention.

## Evidence

[MortgageCalculator.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) lines 54-68 —
`getApplicableRate` never references `m.getId()` or `m.getRateType()`; a repo-wide grep of this
class for `rateType`/`RateType` returns zero hits, confirming `InterestRateType.FIXED` is never
consulted to gate this override.

`SimulationOverrides.MortgageRateScenarioOverride(Integer startYear, Double rate)` — no mortgage-id
field, in contrast to `mortgageEarlyRepayments`, which is keyed by mortgage id and looked up via
`overrides.mortgageEarlyRepayments().get(m.getId())` at line 75 — the codebase's own established
convention for per-mortgage overrides, which `mortgageRateScenarios` conspicuously violates.

Both call sites invoke `getApplicableRate(m.getInterestRate(), overrides, currentYm.getYear())`
inside the per-mortgage loop with the same shared `overrides` object.

## Failure scenario
A household has two mortgages: Mortgage A (`rateType=FIXED`, `interestRate=0.008`) and Mortgage B
(`rateType=VARIABLE`, `interestRate=0.004`). The user (or the GA optimizer, via
`SimulationOverrides.mortgageRateScenarios`) supplies a scenario meant to stress-test the variable
loan, e.g. `[{startYear: 2030, rate: 0.02}]`.

From 2030 onward, Mortgage A's rate is silently overwritten to 2% even though it is contractually
fixed — its domain Javadoc explicitly states "the interest rate remains the same throughout the
selected period" — triggering a bogus 5-year-rule/rate-change payment recalculation and inflating
its interest and payment for the rest of the simulation. Simultaneously, Mortgage A and Mortgage B
are forced to the identical literal rate (2%) rather than each following its own trajectory, purely
because the override carries no mortgage id.

This produces a wrong interest/payment amount, and therefore a wrong cash-flow trajectory and
bankruptcy-year prediction, for any household with a mix of fixed/variable mortgages, or with
multiple variable mortgages, whenever a rate-scenario override is used — including when the GA
optimizer itself generates and evaluates such scenarios internally.

## Suggested direction (not required by this review, noted for scoping a fix)
Add a mortgage-id field to `MortgageRateScenarioOverride` (mirroring the `mortgageEarlyRepayments`
map pattern), and have `getApplicableRate` gate on `m.getRateType() == VARIABLE` so a scenario can
never be silently applied to a fixed-rate loan.

---

# Solution

# Fix Fixed-03: Mortgage rate-scenario override applies globally and ignores rate type

本次計畫針對後端進行重構，將原先全域無差別的利率變動腳本 (`List`)，改為針對特定房貸 (`Map<Long, List>`)，並在核心計算時尊重房貸的 `RateType`（固定/機動）。

## User Review Required

> [!WARNING]
> **API Breaking Change 預警**
> 這個改動會改變 `AssetSimulationRequest` 和 `GlobalOptimizerResponse` 中 `mortgageRateScenarios` 欄位的 JSON 結構。從單純的陣列變成以 `mortgageId` 為 key 的物件 (Map)。
> 若前端有使用此欄位，您必須在後續更新前端的 TypeScript 介面。本次計畫僅專注於**後端 API 與領域邏輯**的重構。

## Open Questions

- 針對 `GlobalOptimizerServiceImpl` 中的基因演算法（Genetic Algorithm），目前 `ChromosomeMapper` 直接返回 `null` 給 `mortgageRateScenarios`。我們確認本次改動將不改變最佳化器在這部分的預設行為。

## Proposed Changes

---
### Data Transfer Objects (DTO)
修改對外公開的 API 結構，使其與 `mortgageEarlyRepayments` 保持一致的 Map 結構。

#### [MODIFY] [AssetSimulationRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java)
- 將 `List<MortgageRateScenarioRequest> mortgageRateScenarios` 改為 `Map<Long, List<MortgageRateScenarioRequest>> mortgageRateScenarios`。

#### [MODIFY] [GlobalOptimizerResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java)
- 將 `List<MortgageRateScenarioResponse> mortgageRateScenarios` 改為 `Map<Long, List<MortgageRateScenarioResponse>> mortgageRateScenarios`。

---
### Domain Command Layer
修改內部使用的指令模型，保持與 DTO 同步。

#### [MODIFY] [SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
- 將 `List<MortgageRateScenarioOverride> mortgageRateScenarios` 改為 `Map<Long, List<MortgageRateScenarioOverride>> mortgageRateScenarios`。

---
### Core Calculation Logic
修正核心的利率套用邏輯，使其具備針對性與防護機制。

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- 修改 `getApplicableRate` 的方法簽名：傳入 `MortgageRecord m` 而非 `double originalRate`。
- 新增第一道防線：`if (m.getRateType() != ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType.VARIABLE) { return m.getInterestRate(); }`，確保固定利率永遠不受外部情境干擾。
- 新增第二道防線：改從 `overrides.mortgageRateScenarios().get(m.getId())` 取得專屬的變動劇本，取代原先的無腦覆蓋。
- 更新內部兩處呼叫點 (`fastForwardMortgagesToPresent` 與 `simulateMortgagesForYear`) 以傳入 `m`。

## Strict Rules Enforcement
> [!IMPORTANT]
> 1. **嚴禁使用 git 回溯檔案 (git restore)**。
> 2. **嚴禁使用腳本語言 (bash, sed, python 等) 以及命令列進行修改或新增檔案**。
> 3. **進行之前必須把現在的 code 全數 commit，並且從最新狀態切出新分支** (Branch: `bugfix/mortgage-rate-scenario-global`)。
> 4. **嚴禁自動跑編譯與單元測試**，一切交給使用者執行來節省 token 跟時間。
> 5. **嚴禁超出指定的範圍**：只允許修改清單上指定的 DTO 與 Calculator 檔案，絕不干涉其它模組。
> 6. **測試相容性**：如有相關單元測試的 Mock 資料與 Assert 邏輯均須同步替換且嚴禁修改測試所驗證的業務行為。

## Verification Plan

### Agent Action Required Before Execution
1. **Commit & Branch**: 實作前，Agent 必須確認目前狀態已由使用者 Commit，若尚未 commit 請求使用者給予 commit 許可，commit 後由 Agent 建立新分支 (`bugfix/mortgage-rate-scenario-global`) 才能開始執行。(嚴禁動到上層wapper的repo，僅commit submodule)
2. **Update Progress**: 實作完成更新 `EPIC PROGRESS.md` 與 `Fixed-03-mortgage-rate-scenario-override-global.md`

### Manual Verification (User Action Required)
1. **Review Diff**: 由使用者檢閱 Diff：
   - 確認 API DTO 與 Command 物件已成功轉換為 `Map<Long, List>` 結構。
   - 確認 `MortgageCalculator` 的 `getApplicableRate` 正確使用了 `m.getId()` 及 `m.getRateType()`。
2. **Compile & Test**: 由使用者手動執行 `mvn clean install`，確保編譯通過且自動化單元測試與 MapStruct 轉換無誤 (Agent 嚴禁自行觸發任何編譯或測試指令)。

# Frontend Plan: Fixed-03 (Mortgage Rate Scenario Map Refactoring)

本次計畫專注於前端 (`hub-ui`) 的介面與狀態重構，以配合後端已完成的 `Map<Long, List>` 結構變更。我們將讓使用者能夠在 UI 上明確指定「哪一筆機動利率房貸」要套用壓力測試，並且過濾掉固定利率房貸。

## User Review Required

> [!WARNING]
> **LocalStorage 資料結構變更**
> 本次修改會改變 `finance_hub_mortgage_rate_scenarios` 在 LocalStorage 中的儲存結構。
> 由於目前只有 Local 環境，為了不撰寫多餘的相容性程式碼，**請您在前端更新完成後，自行前往瀏覽器開發者工具 (F12 -> Application -> Local Storage) 清除舊的 LocalStorage 紀錄**，以避免舊有的陣列資料造成白畫面。

## Proposed Changes

### Data & State Layer
更新 TypeScript 介面與 Hooks。

#### [MODIFY] [use-simulation-data.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts)
- 將 `SimulationOverrides` 介面中的 `mortgageRateScenarios` 型別由 `{ startYear: number; rate: number }[] | null` 改為 `Record<number, { startYear: number; rate: number }[]> | null`。

#### [MODIFY] [use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)
- 將 `GlobalOptimizerResponse` 中的 `mortgageRateScenarios` 型別從 `any[] | null` 更新為 `Record<number, any[]> | null`。

### UI Presentation Layer
重構 `SimulationOverridePanel`，套用類似早期還款 (Early Repayment) 的 `flatRepayments` 處理邏輯。

#### [MODIFY] [simulation-override-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
- **新增 `flatRateScenarios`**: 使用 `useMemo` 將 `Record<number, array>` 轉平，方便在 React 中透過 `.map()` 渲染。
- **修改 Handlers**: 
  - `handleAddScenario`: 自動尋找第一筆 `rateType === 'VARIABLE'` 的房貸作為預設對象。若找不到，則阻止新增。修改 `update` 的 payload 為 Map (`Record<number, any[]>`)。
  - `handleUpdateScenario` & `handleRemoveScenario`: 對應 `flatRateScenarios` 索引，修改後將陣列重新分群打包回 `Record<number, any[]>`。
- **更新 Render 邏輯**:
  - 在原本只有 `Start Year` 與 `Rate` 的欄位前，加入一個下拉選單 (Select)，用來選擇**哪一筆機動利率房貸**（過濾掉固定利率）。
  - 若所有房貸皆為固定利率，則顯示提示文字並禁用「Add Rate Scenario」按鈕。

## Strict Rules Enforcement
> [!IMPORTANT]
> 1. **嚴禁使用 git 回溯檔案 (git restore)**。
> 2. **嚴禁使用腳本語言 (bash, sed, python 等) 以及命令列進行修改或新增檔案**。
> 3. **請使用者切換至對應的 frontend repository (`hub-ui`) 並建立分支 (`bugfix/mortgage-rate-scenario-frontend`)**。
> 4. **嚴禁自動跑編譯與單元測試**，一切交給使用者執行。
> 5. **嚴禁超出指定的範圍**：只允許修改指定的 React 組件與 Hooks。

## Verification Plan

### Agent Action Required Before Execution
1. 實作前，Agent 必須向使用者確認 `hub-ui` 是否已經準備好新的分支 (`bugfix/mortgage-rate-scenario-frontend`)，並取得執行許可。
2. 實作完成後更新 `task.md` 與對應的報告。

### Manual Verification (User Action Required)
1. 由使用者檢閱前端 Diff：
   - 確認 UI 上出現房貸選擇下拉選單，且成功過濾固定利率房貸。
2. 使用者手動清除 LocalStorage 中的 `finance_hub_mortgage_rate_scenarios` 快取。
3. 使用者啟動 `npm run dev` 並在瀏覽器中操作：
   - 測試新增、修改、刪除 Rate Scenario，觀察送到後端的 JSON Payload 是否正確轉為 `mortgageRateScenarios: { "1": [...] }` 格式。

**Resolution Status**: Fixed

1. **DTO Refactoring**: Changed `mortgageRateScenarios` in `AssetSimulationRequest` and `GlobalOptimizerResponse` to `Map<Long, List<...>>`.
2. **Mapper Logic**: Updated `AssetSimulationMapper` and `GlobalOptimizerMapper` with custom `default` methods to handle the complex map conversion.
3. **Core Calculation Logic (`MortgageCalculator.java`)**: Updated `getApplicableRate` to take the full `MortgageRecord`. Rate overrides are now gated behind `m.getRateType() == VARIABLE` and specific to `m.getId()`. Fixed-rate mortgages are immune.
4. **Frontend Architecture (`hub-ui`)**: Refactored `SimulationOverrides` and `use-simulation-data.ts` to support the Map/Record structure. Updated `simulation-override-panel.tsx` to automatically filter variable mortgages and provide a dropdown to select the target mortgage for rate scenarios.
