# Fixed 47 修正計畫書 (Implementation Plan)

為了解決 Grid Search 的最佳化結果（建議提前還款年度）在傳遞到前端時被「無聲丟棄」的問題，並同時修復畫面上渲染出「空內容卡片」的 Bug，我們將在**前端**實作缺失的資料對接邏輯。

> [!NOTE]
> 雖然 Fixed 44 的計畫是最終會棄用 Grid Search，但在您做出最終決定前，我們應該讓現有的 Grid Search 能夠正常發揮作用。這次修正只涉及前端的資料綁定，不改動後端核心，成本極低。

## Proposed Changes

我們將修改 `hub-ui` (前端) 的 3 個檔案，把斷掉的資料流接回來。

### Frontend (`hub-ui`)

#### [MODIFY] [use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)
- 在 `GlobalOptimizerResponse` 介面中補上後端 API 實際有回傳的欄位：
  ```typescript
  mortgageEarlyRepaymentYear?: number;
  ```

#### [MODIFY] [page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-ui/src/app/finance/simulator/simulation/page.tsx)
- 在 `onApplyResult` (套用最佳化結果到沙盒) 中，將接收到的 `mortgageEarlyRepaymentYear` 寫入沙盒覆寫狀態：
  ```typescript
  if (result.mortgageEarlyRepaymentYear != null) {
      updateSandboxOverrides({ mortgageEarlyRepaymentYear: result.mortgageEarlyRepaymentYear });
  }
  ```

#### [MODIFY] [global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)
在 `OptimizerResultPanel` 裡進行兩項調整：
1. **修復「有資料但內容空白」的卡片 Bug**：
   - 後端預設會傳遞一個「有 Key 但 Value 為空陣列」的 map (`{ "mortgageId": [] }`)。
   - 前端過濾掉這種空陣列，避免錯誤地渲染出空白的「局部提前還款策略」卡片。
2. **新增 Grid Search 專用顯示**：
   - 如果資料中包含 `mortgageEarlyRepaymentYear`，則渲染出專屬的顯示區塊，例如：「**最佳全額清償年度：第 X 年**」。

## Verification Plan
1. 編譯並啟動 `hub-ui` 前端。
2. 針對單一房貸設定進行一次 Global Optimizer (Grid Search) 的最佳化運算。
3. 觀察結果卡片，確認原本空白的卡片消失，並正確顯示出「建議的清償年度」。
4. 點擊「Apply (套用)」，確認該年度能成功寫入沙盒設定，並觸發沙盒重新模擬。
