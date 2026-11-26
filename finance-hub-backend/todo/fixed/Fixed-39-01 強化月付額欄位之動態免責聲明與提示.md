# [Fixed-39] 強化月付額欄位之動態免責聲明與提示

本計畫旨在解決 Fixed-39 所指出的問題：前端 `page.tsx` 中針對 `monthlyPayment` 欄位下方的免責聲明過於空泛，無法在特定且容易填錯的情境下給予使用者明確的操作引導。

## User Review Required

> [!IMPORTANT]
> 根據 Fixed-39 提案，我們將會移除原本固定不動的 "* System estimate. Please refer to actual bank details." 字樣，改為根據使用者的表單狀態，動態顯示具有實質指引意義的提示訊息。
> 這些提示文字將以英文撰寫，以符合 codebase 的語言一致性。請您與 Claude 確認是否同意此 UX 改善方向。

## Proposed Changes

### Frontend UI

#### [MODIFY] [`page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
- 定位到 `monthlyPayment` 輸入欄位下方的 `<p className="text-xs text-muted-foreground italic">` 免責聲明區塊。
- 將原本固定的文字替換為兩段獨立判定、可同時顯示的動態提示：

**1. 元金均等 (EQUAL_PRINCIPAL) 專屬提示**
- **觸發條件**：`formData.repaymentMethod === "EQUAL_PRINCIPAL"`
- **顯示內容**：使用醒目的顏色（如 `text-amber-600`）提醒使用者：
  `* EQUAL PRINCIPAL: Please enter only the principal portion (excluding interest). Refer to the "Principal" column in your bank's repayment schedule, NOT the total payment.`

**2. 獎金還款 (Bonus Payments) 專屬警示**
- **觸發條件**：`hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0)`
- **顯示內容**：使用紅色與粗體（如 `text-red-600 font-medium`）強烈警告：
  `* ⚠️ BONUS CONFIGURED: The estimate feature does not support bonus payments and will be highly inaccurate. Please directly use the values from your bank's schedule.`

## Verification Plan

### Automated Tests
- 執行 `npm run build` 確認 JSX 語法正確，沒有 Type 錯誤。

### Manual Verification
1. 打開房貸新增表單。
2. 選擇「EQUAL_PRINCIPAL」，應看到橘色的純本金輸入提示。
3. 勾選「Include Bonus Payments」並填寫金額，應看到紅色的獎金警示提示。
4. 同時滿足兩者時，兩個提示應同時顯示，不會互相覆蓋。
