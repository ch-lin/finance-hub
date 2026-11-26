# [Fixed-69] 修正 Global Optimizer 中 iDeCo 開始提領年齡 (idecoStartAgeOverride) 無法影響模擬結果的問題

## 問題分析

經過程式碼的實際確認，如同作者在 `Fixed-69.md` 所述：

`SimulationParameterResolver.resolveIdecoUnlockAge` 和 `resolveDcUnlockAge` 負責解析由 `FinanceHubConfig` 定義的解鎖年齡下限（法定年齡，預設為 60 歲），這部分屬於系統級設定，不應被 Optimizer 的搜尋結果覆蓋。

真正決定模擬引擎在何時開始提領的參數，是由 `SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge` 計算而出，並在 `AssetSimulationEngineImpl.java` 中傳遞給年度模擬管線 (`yearlySimulationPipeline`)。

然而，目前的 `resolveTaxAdvantagedWithdrawalAge` 僅考慮了使用者的 Profile (`profile.getTaxAdvantagedWithdrawalAge()`) 以及法定年齡下限，**完全沒有接收 Optimizer 所產生的 override 參數**。這導致 Optimizer 針對 `idecoStartAgeOverride` 進行的基因演算法搜尋變成無效運算，且回傳給使用者的建議值也無法發揮任何作用。這是我在之前的修改中遺漏的邏輯連貫性。

## 建議修改內容

### 1. `ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.SimulationParameterResolver`

#### [MODIFY] [`SimulationParameterResolver.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)
- 為 `resolveTaxAdvantagedWithdrawalAge` 方法新增一個 `Integer override` 參數。
- 更新參數的優先級邏輯：
  1. `override` (若有值，優先採用)
  2. `profile.getTaxAdvantagedWithdrawalAge()`
  3. `minUnlockAge` (基於 `dcUnlockAge` 與 `idecoUnlockAge` 取 max 的下限)
- 確保不論是使用 `override` 還是 `profile` 的值，都必須符合上下限限制：最小值不得低於 `minUnlockAge`，最大值不得超過 `75`。

### 2. `ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngineImpl`

#### [MODIFY] [`AssetSimulationEngineImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
- 在 `runSimulationInternalMem` 中，從 `overrides` 物件中取出 `idecoStartAgeOverride()`（需處理 null 檢查）。
- 呼叫更新後的 `SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge` 時，將取得的 override 值作為參數傳入。

## 驗證計畫 (Verification Plan)

### 人工驗證
- 交由使用者 (您) 執行相關的單元測試，以確保參數解析的優先順序邏輯以及上下限防護 (`Math.max` 與 `Math.min`) 行為如預期，且編譯順利。
