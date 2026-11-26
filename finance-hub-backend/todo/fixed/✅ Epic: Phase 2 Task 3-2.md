# Task 3-1: AI Optimizer Checkbox UI & Backend Integration (Clean Architecture)

這份計畫將實作全新的 Optimizer Tab UI，並透過 Clean Architecture (DTO -> Mapper -> Command) 來保護沙盒引擎原本的 `SimulationOverrides` 不被污染，同時還清 `AssetSimulationController` 的技術債。

## Strict Rules Enforcement
> [!IMPORTANT]
> 1. **嚴禁使用 git 回溯檔案 (git restore)**。
> 2. **嚴禁使用腳本語言 (bash, sed, python 等) 以及命令列進行修改或新增檔案**。
> 3. **進行之前必須把現在的 code 全數 commit，並且從最新狀態切出新分支** (Branch: `feature/global-optimizer-ui`)。
> 4. **嚴禁自動跑編譯與單元測試**，一切交給使用者執行來節省 token 跟時間。
> 5. **嚴禁超出指定的範圍**：只允許修改本次計畫相關的檔案，絕不干涉其它模組。
> 6. **測試相容性**：如有相關單元測試的 Mock 資料與 Assert 邏輯均須同步替換且嚴禁修改測試所驗證的業務行為。

## Proposed Changes

### Backend: Clean Architecture Refactoring (Technical Debt)

#### [NEW] `AssetSimulationRequest.java` (Controller DTO)
- 建立在 `controller.simulation.dto`。
- 作為 `/asset-simulation/{targetAge}` 的 Payload，欄位對應原本的 `SimulationOverrides`，但不包含 `strategy`。

#### [MODIFY] `AssetSimulationMapper.java`
- 新增 `toOverrides(AssetSimulationRequest request)` 的映射方法，將 Controller DTO 轉為 Service 層的 `SimulationOverrides`。

#### [MODIFY] `AssetSimulationController.java`
- 將原本的 `@RequestBody SimulationOverrides` 替換為 `@RequestBody AssetSimulationRequest`。
- 透過 Mapper 轉換後再傳給 `AssetSimulationEngine`。

#### [MODIFY] `SimulationOverrides.java` (Service Model)
- 將 `OptimizationStrategy` 從 `SimulationOverrides` 移除，讓它成為純粹的沙盒推演參數模型。

---

### Backend: Global Optimizer API (Clean Architecture)

#### [NEW] `GlobalOptimizationRequestDto.java` & `GlobalOptimizationResponseDto.java` (Controller DTO)
- 建立在 `controller.simulation.optimizer.dto`。
- `GlobalOptimizationRequestDto` 包含：
  - `AssetSimulationRequest baseOverridesDto`
  - `OptimizationConfigDto optimizationConfigDto`
- `GlobalOptimizationResponseDto` 包含：
  - `AssetSimulationRequest optimizedOverridesDto`

#### [NEW] `GlobalOptimizationCommand.java` & `OptimizationConfig.java` (Service Model)
- 建立在 `app.service.simulation.optimizer.model`。
- `OptimizationConfig` 包含 5 個 boolean，對應前端的所有 Checkbox：
  - `optimizeRetirementAge`
  - `optimizePensionAge`
  - `optimizeIdecoAge`
  - `optimizeNisa`
  - `optimizeMortgage`
- `GlobalOptimizationCommand` 包含：
  - `SimulationOverrides baseOverrides`
  - `OptimizationConfig optimizationConfig`
  - `OptimizationStrategy strategy`

#### [NEW] `GlobalOptimizerMapper.java`
- 負責將 `GlobalOptimizationRequestDto` 轉為 `GlobalOptimizationCommand`。
- 負責將 Service 層產出的 `SimulationOverrides` 轉為 `GlobalOptimizationResponseDto`。

#### [NEW] `GlobalOptimizerController.java`
- 提供 `POST /simulation/global-optimizer/optimize/{targetAge}` API。
- 接收 `GlobalOptimizationRequestDto`，呼叫 Mapper 轉為 `Command`，交給 `GlobalOptimizerService`，再將結果轉回 `ResponseDto`。

#### [MODIFY] `GlobalOptimizerService.java`
- 將 `optimize` 的參數改為 `(int targetAge, GlobalOptimizationCommand command)`。

---

### Frontend: UI & API Hook

#### [MODIFY] `simulation/page.tsx` (備份舊版 Optimizer)
- 為了確保舊版 API (`/mortgage-early-repayment-year` 與 `/nisa-goal`) 不會被弄壞，在 `AnimatedTabs` 中新增一個 Tab 叫做 `Legacy Optimizer`。
- 將原本在 `optimizer` Tab 裡面的 `OptimizationInsightsPanel` 與 `NisaGoalSolverCard` 搬移到 `Legacy Optimizer` Tab 中備份起來。
- 清空 `optimizer` Tab，留給新的 Global Optimizer 使用。

#### [NEW] `use-global-optimizer.ts`
- 建立專屬於 Optimizer 的 Custom Hook，裡面封裝呼叫 `POST /api/finance/simulation/global-optimizer/optimize/{targetAge}` 的邏輯。

#### [NEW] `global-optimizer-card.tsx`
- 在清空後的 `optimizer` Tab 加入全新的全域最佳化卡片組件。
- 提供完整對應 Epic 規劃的 Checklist UI：
    - `[ ] 目標退休年齡`
    - `[ ] 年金提領年齡`
    - `[ ] iDeCo/DC 提領年齡`
    - `[ ] NISA 逆向求解`
    - `[ ] 房貸提前還款策略 (年份、次數與單次金額上限)`
- 點擊「Run AI Optimizer」按鈕後，呼叫後端 API。
- 拿到回傳的 `GlobalOptimizationResponseDto` 後，提供一個「Apply to Sandbox」按鈕，讓使用者可以把結果套用到沙盒模式。

---

## Verification Plan

### Agent Action Required Before Execution
1. **Commit & Branch**: 實作前，Agent 必須確認目前狀態已由使用者 Commit，若尚未 commit 請求使用者給予 commit 許可，commit 後由 Agent 建立新分支 (`feature/global-optimizer-ui`) 才能開始執行。(嚴禁動到上層wapper的repo，僅commit submodule)
2. **Update Progress**: 實作完成更新PROGRESS.md

### Manual Verification (User Action Required)
1. **Review Diff**: 由使用者檢閱 Diff：
   - 確認 `AssetSimulationController` 與 `GlobalOptimizerController` 正確使用 Clean Architecture 的 DTO 結構，沒有直接把 Service Model (`SimulationOverrides`) 暴露給 `@RequestBody`。
   - 確認 `SimulationOverrides.java` 成功拔除 `strategy` 欄位。
   - 確認前端的 `simulation/page.tsx` 成功備份舊版卡片到 `Legacy Optimizer` Tab。
   - 確認前端的 `global-optimizer-card.tsx` 能夠正確顯示所有 5 個最佳化選項，並呼叫新的 API。
2. **Compile & Test**: 由使用者手動執行 `mvn clean install` 與前端編譯，確保編譯通過且自動化單元測試無誤 (Agent 嚴禁自行觸發任何編譯或測試指令)。
