# [Fixed-38] 房貸月付額試算器 - 獎金加成還款防呆機制

本計畫旨在解決 Fixed-38 中指出的高風險行為：當使用者設定了「獎金加成還款 (Bonus Payments)」時，前端的「Calculate Estimate」按鈕依然會使用無獎金版的公式進行試算，且會靜默覆寫 `monthlyPayment` 欄位，導致極度失真的錯誤數據被寫入後端。

## User Review Required

> [!IMPORTANT]
> 此計畫採用的是 Fixed-38 提案中的「建議做法（優先處理）」，亦即**直接阻擋試算並給予警告**，而非「加分項」的擴充試算器邏輯。理由是房貸的核心權威數據應始終來自銀行提供的真實還款表，花費精力將前端試算器改寫到與後端引擎一樣複雜並不是最符合經濟效益的做法，及時停損並給予正確 UX 引導才是關鍵。
> 請您與 Claude 確認是否同意此策略。

## Proposed Changes

### Frontend UI

#### [MODIFY] [`page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
- 在 `calculateEstimate()` 函式的上方，加入針對獎金設定的防呆檢查 (Guard Clause)。
- 檢查條件：當 `hasBonus` 為 `true`，且 `formData.bonusPayments` 陣列中存在任何一筆 `amount > 0` 的獎金設定時，觸發阻擋。
- 阻擋行為：
  - 呼叫 `toast.error` 顯示警告訊息：「This estimate does not account for the bonus payments you've set, and will be highly inaccurate. Please refer to your bank's actual repayment schedule.」
  - 立即 `return`，中斷後續的 PMT 公式計算。
  - 確保 `formData.monthlyPayment` 不會被無聲覆寫。

## Verification Plan

### Automated Tests
- 執行前端編譯檢查，確保語法正確無誤 (`npm run build`)。

### Manual Verification
- 於 UI 介面新增一筆房貸，並勾選「Include Bonus Payments」。
- 在獎金欄位填入任意大於 0 的金額。
- 點擊「Calculate Estimate」按鈕。
- **預期結果**：系統應彈出紅色錯誤提示，且「Monthly Payment」欄位的數值不會產生任何變化。
