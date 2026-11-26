# Fixed-38：房貸月付額試算器完全沒有考慮「ボーナス払い（獎金加成還款）」，設定獎金後結果嚴重失真且無警示直接覆寫存檔欄位

## 現況說明 (Current State)

前端 [page.tsx](../../../../hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx) 的 `calculateEstimate()`（60-86 行）只用 `formData.totalLoanAmount`、`formData.interestRate`、`formData.loanTermMonths` 三個欄位計算，公式為教科書式的無獎金版 PMT / 元金均等公式，**完全沒有參考 `formData.bonusPayments`**。

但同一份表單就在計算按鈕（380-383 行）下方，提供「Include Bonus Payments（ボーナス払いあり）」勾選框（402-430 行）與每年兩次的獎金金額輸入欄位（432 行起）。而後端實際計算月付額的 [MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java)（80-106 行）會先把獎金還款流的現值（`presentValueOfBonusStream`，元利均等）或名目總額（`countRemainingBonusPaymentsAmount`，元金均等）從貸款餘額中扣除，才計算每月固定應繳金額——這正是日本「ボーナス併用返済」房貸的標準機制。

按下「計算」按鈕會直接、無聲地覆寫 `formData.monthlyPayment`（84 行 `setFormData({ ...formData, monthlyPayment: Math.round(estimate) })`），跟平常沒設定獎金時共用同一個按鈕、同一句通用免責聲明，沒有任何區分。

## 問題原因 (Cause)

計劃書採用的公式本身沒有錯，但那是「沒有獎金加成還款」情境下的公式，被直接套用到一個本來就支援獎金加成還款的表單上，卻沒有同步擴充試算邏輯，也沒有提示使用者這個試算結果不適用於有獎金還款的情境。

實際影響（用一個極端但足以說明問題的例子）：貸款 10,000,000、年利率 1.5%、n=360，若設定每年 6 月與 12 月各還 20 萬獎金（總計整個貸款期間約 1,200 萬）：
- 前端試算器仍然顯示 34,512/月（完全忽略獎金，跟沒設定獎金時一樣）；
- 後端真實的攤還公式，扣除獎金現值後，每月正常還款其實只需約 1,283 元；
- 兩者相差超過 90%。

更嚴重的是，[MortgageCalculator.initializeMortgages](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)（44-77 行）目前的設計是**無條件信任** `monthlyPayment` 欄位為 ground truth，不會重新計算或校正。也就是說，如果使用者在設定了獎金還款的情況下按下「計算」並採用試算結果存檔，這個被高估的錯誤數字會直接流入正式模擬，沒有任何後續機制會糾正它。

**在「使用者終究要以自己銀行給的真實數字為準，系統不對試算準確度負最終責任」的前提下，這個問題的核心風險不是「試算不夠準」，而是「工具在使用者沒發現的情況下，把一個外觀正常、但完全沒考慮他剛剛設定的獎金的數字，無聲寫進之後會被永久信任的欄位」。** 試算準不準確是次要的，「有沒有讓使用者意識到這次的結果不可信」才是關鍵。

## 建議修正方向 (Suggested Fix)

- **建議做法（在使用者終究會核對銀行真實數字的前提下已足夠，優先處理）**：當 `hasBonus` 為真且獎金金額非零時，改變「計算」按鈕的行為——不要靜默覆寫欄位，而是明確警示「此試算結果未考慮您設定的獎金還款，會嚴重偏離實際金額，請直接參考銀行提供的還款計畫表」，或直接停用計算按鈕。這個警示必須跟 `hasBonus`／獎金金額狀態連動，不能只是現有的通用免責聲明（詳見 [Fixed-39.md](Fixed-39.md)）。
- **加分項（非必要，可提升工具實用性，非本次修正的必要條件）**：擴充 `calculateEstimate()`，在套用 PMT / 本金公式之前，先仿照後端邏輯扣除 `formData.bonusPayments` 的現值（元利均等）或名目總額（元金均等）。理想情況下應該讓前端呼叫後端共用的計算邏輯，避免兩份公式各自實作、未來又再度失去同步。

## 最小範例程式碼 (Sample Code)

```ts
// page.tsx calculateEstimate()：設定獎金時，不要靜默覆寫欄位
const calculateEstimate = () => {
  // ...現有驗證...

  if (hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0)) {
    toast.error("此試算結果未考慮您設定的獎金還款，會嚴重偏離實際金額，請直接參考銀行提供的還款計畫表。");
    return;
  }

  // ...現有計算邏輯...
};
```

## 修正報告 (Resolution Report)

已依照「建議做法」完成修復：

1. **前端 (UI)**:
   - 在 `page.tsx` 的 `calculateEstimate()` 函式中加入了防呆機制 (Guard Clause)。
   - 當使用者勾選了包含獎金還款 (`hasBonus == true`) 且實際設定了非零的獎金金額時，若按下「Calculate Estimate」按鈕，系統將不再靜默覆寫 `monthlyPayment` 欄位。
   - 取而代之的是，系統會中斷試算，並彈出紅色的 `toast.error` 警告訊息：「This estimate does not account for the bonus payments you've set, and will be highly inaccurate. Please refer to your bank's actual repayment schedule.」。

此修正確保了試算器不會在未經提示的情況下，將未包含獎金邏輯的錯誤金額寫入並送入後端，將原本高風險的無聲資料污染轉化為明確的 UX 引導。
