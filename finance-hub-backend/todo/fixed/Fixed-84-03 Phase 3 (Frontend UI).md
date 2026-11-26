# Fixed-84-03 Phase 3 (Frontend UI)

Phase 3 的目標是在前端（hub-ui）的「提前還款」表單中新增「縮短月數 (reducedMonths)」的輸入區塊。配合我們稍早做出的決策，這個數值將由使用者手動輸入，藉此避開複雜且容易算錯的自動反推公式。

## User Review Required
沒有破壞性變更，但請審查以下的 UI 調整方式是否符合您的期望。

## Open Questions
- 若使用者選擇了 `REDUCE_TERM` (期間短縮) 但**沒有填寫**或**填寫小於等於 0** 的 `reducedMonths`，計畫中已預設會在 `handleSave` 階段阻擋並跳出 Toast 錯誤提示。這符合您的預期嗎？

## Proposed Changes

### hub-ui 專案

#### [MODIFY] [`types.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/types.ts)
- 在 `MortgageEarlyRepayment` 介面中新增 `reducedMonths?: number;` 可選屬性。

#### [MODIFY] [`page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
- **表單驗證 (`handleSave` 函數)**：
  - 新增防呆邏輯：當迴圈檢查提前還款項目時，若發現 `er.repaymentType === 'REDUCE_TERM'`，則驗證其 `reducedMonths` 是否大於 0，否則跳出 `toast.error` 阻擋儲存。
  - 在發送 Request 前清理髒資料：整理 `formData.earlyRepayments` 陣列，若類型為 `REDUCE_PAYMENT`，強制將 `reducedMonths` 覆寫為 `null` 或 `undefined`。
- **UI 渲染**：
  - 修改 `earlyRepayments.map` 產生的表單區塊，將原本單一橫列的 `grid-cols-12` 包入一個 `flex flex-col` 容器中。
  - 新增條件渲染 (`er.repaymentType === 'REDUCE_TERM'`)：若條件成立，則在原先的 12 欄位下方多顯示一列輸入區塊，提供標籤「Reduced Term (Months)」與對應的 `<Input type="number" />` 讓使用者填寫。

## Verification Plan

### Manual Verification
等待程式碼修改完成後，您可以進行以下手動測試：
1. 開啟 UI 進入 Mortgages 管理頁面，點擊新增或編輯。
2. 勾選「Has Early Repayments (繰上げ返済あり)」。
3. 點擊「Add Repayment」，將類型切換為「REDUCE_TERM (期間短縮)」，確認畫面下方會出現「縮短月數」的數字輸入框。
4. 測試輸入框消失：將類型切換為「REDUCE_PAYMENT (返済額軽減)」，確認輸入框消失。
5. 測試防呆驗證：選擇「REDUCE_TERM」但把縮短月數留白或填 0，點擊 Save，確認畫面上會跳出要求輸入有效月數的錯誤。
6. 填妥正確數字後儲存，打開瀏覽器開發者工具 (Network tab) 確認發送的 API 請求中確實包含了正確的 `reducedMonths` 數值。
