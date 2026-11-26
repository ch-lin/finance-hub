# 修復房貸月付額語意衝突 (Fixed-37)

本次修正旨在解決前後端對於「元金均等」還款方式的語意不一致問題，確保送入資料庫的 `monthlyPayment` 在「元金均等」時為「純本金」。

## User Review Required

> [!WARNING]  
> 這次的改動將會改變前端 `calculateEstimate()` 在「元金均等」時的行為（從計算總額改為計算純本金）。
> 未來使用者在建立或編輯「元金均等」的房貸時，必須填寫「純本金」而非銀行帳單上的「當月應繳總額」。我們將透過修改 UI 上的欄位 Label 來引導使用者。

## Proposed Changes

### Frontend (UI & 試算邏輯)

#### [MODIFY] `page.tsx` (hub-ui/src/app/finance/accounts/mortgages/page.tsx)
1. **試算公式修正**：在 `calculateEstimate()` 中，若 `repaymentMethod === "EQUAL_PRINCIPAL"`，將公式改為只回傳 `P / n`（純本金），去除利息部分。
2. **Label 動態切換**：在 UI 表單中，當選擇 `EQUAL_PRINCIPAL` 時，將原本的 "First Month Est. Payment (JPY)" 標籤動態切換為 "Monthly Principal (JPY)"，以明確告知使用者此欄位的預期意義。

### Backend (死代碼清理)

#### [MODIFY] `MortgageCalculatorUtil.java` (ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java)
1. **修正或移除無用分支**：`calculateEstimatedMonthlyPayment` 方法目前在後端無人呼叫，但其內部針對「元金均等」的分支卻犯了與前端相同的語意錯誤（計算出本金+利息）。將會修正此分支使其僅回傳純本金，或直接移除此未使用的邏輯，以防未來誤用。

## Verification Plan

### Manual Verification
1. 在 UI 上建立一筆新的房貸，選擇「元金均等」，輸入金額與期數，按下「計算」，確認結果只包含「純本金」。
2. 確認在「元金均等」時，欄位的標籤會即時變更為 "Monthly Principal (JPY)"。
