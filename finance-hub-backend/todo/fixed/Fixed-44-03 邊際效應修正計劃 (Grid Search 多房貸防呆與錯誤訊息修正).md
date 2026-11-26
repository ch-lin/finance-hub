# Fixed-44-03 邊際效應修正計劃 (Grid Search 多房貸防呆與錯誤訊息修正)

為了解決在 Fixed-44-02 中發現的邊際效應：多房貸情境下選擇 Grid Search 會導致「所有房貸都在同一年被一次全額清償」，且前端錯誤訊息被吃掉的問題，以下是實作計畫。

## User Review Required
> [!IMPORTANT]
> 此計畫採用最小干預原則，針對 Backend 入口與 Frontend 錯誤顯示進行精準修復，不涉及複雜的 UI 選單聯動。
> 請您審查此計畫。若無異議，請點擊 Proceed 授權我開始實作。

## Proposed Changes

---

### Backend Service (防線建立)

#### [MODIFY] [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
- **增加防呆阻擋邏輯**：在 `optimize()` 方法的 `switch (strategy)` 內，針對 `FORCE_GRID` 分支的最前方加入多房貸數量的檢查。
  如果解鎖了房貸最佳化（`!seed.isMortgageRepaymentsLocked()`）且房貸數量大於 1，將直接拋出 `OptimizationNotSupportedException`，拒絕執行 Grid Search。
- `WebGlobalExceptionHandler` 已具備捕捉此 Exception 並回傳 HTTP 400 的能力，因此不需新增例外處理邏輯。

---

### Frontend UI (錯誤訊息修復)

#### [MODIFY] [use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)
- **修復錯誤訊息解析**：目前第 51 行的 `toast.error` 只能讀取到 `json.message`，但後端的 API 回應標準格式會將具體錯誤放在 `json.data.message`。
  將其修改為：
  ```typescript
  toast.error(json.data?.message || json.message || "Failed to optimize simulation.");
  ```
- 如此一來，當使用者觸發上方的後端防線時，UI 就能清楚顯示「Grid Search 僅支援單一房貸情境，請改用 AUTO 或 FORCE_GA」的完整引導訊息。

## Verification Plan

### Automated Tests
- 此修改為純防線阻擋與前端訊息顯示，不影響現有的正常模擬與最佳化邏輯（因為原本多房貸走 Grid Search 就是非預期的狀態）。

### Manual Verification
1. 模擬多筆房貸的情境，在 UI 上勾選「優化房貸」並強制選擇「Grid Search」。
2. 預期會立刻被擋下（不會執行耗時運算），並在畫面右下角跳出正確說明的 Toast 錯誤訊息。
