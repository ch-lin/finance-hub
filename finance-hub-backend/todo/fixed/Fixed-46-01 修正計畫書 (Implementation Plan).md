# Fixed 46 修正計畫書 (Implementation Plan)

為了解決過渡年 (Initial Year) 模擬結果中，DTO 的 `netIncome` (全年理論值) 與其他稅金欄位 (剩餘期間應繳) 時間基準不一致，導致前端或使用者誤判的問題，我們規劃了以下修正方案。

> [!IMPORTANT]
> **設計決策 (Design Decision): 不修改底層狀態 `state.setNetIncome()`**
> 正如 Fixed 46 的警告所述，若在 `applyYearEndAdjustment` 內覆寫 `netIncome` 的值為「剩餘期間淨額」，將導致後續在 `applyCashflowAndSettlement` 內再次扣減 `ytdNetIncome`，引發嚴重的「雙重扣款」Bug，破壞現金流水位的正確性。
> 
> 因此，本次修正完全**不更動**核心模擬邏輯，僅在最終「組裝回傳 DTO 給前端」時抽換顯示數值。

## Proposed Changes

---

### YearlySimulationPipeline
我們將針對 `YearlySimulationPipeline` 中的 DTO 建立邏輯進行修正。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **目標位置**：`applyCashflowAndSettlement` 函式內的 `AssetSimulationYearlyResult.builder()`（約 350 行附近）。
- **變更內容**：
  原本 `netIncome` 是直接寫死讀取全年理論值：
  ```java
  .netIncome(state.getNetIncome())
  ```
  修改為：判斷若當前迴圈是「過渡年」（`context.getYear() == context.getCurrentYear()`），則改用同一方法內已經正確計算好的 `remainingNetIncome` 區域變數；否則維持原狀使用 `state.getNetIncome()`。
  ```java
  .netIncome(context.getYear() == context.getCurrentYear() ? remainingNetIncome : state.getNetIncome())
  ```

## Verification Plan

### Automated Tests
您可以使用以下指令，跑一次現有的測試，確保這個單純的 DTO 顯示層修正沒有破壞任何既有的核心算式：
```bash
./gradlew :hub-app:test --tests "ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline.*"
```

### Manual Verification
- 建議在前端啟動後，執行一次過渡年（帶有 YTD 薪資資料）的模擬。
- 檢查回傳的 JSON 或前端畫面上，該年的 `netIncome` (稅後淨利) 是否與 `estimatedIncomeTax` (預估所得稅) 等欄位保持一致的「剩餘」口徑，且最終的存款水位 (`projectedLiquidCash`) 沒有因為雙重扣款而異常減少。
