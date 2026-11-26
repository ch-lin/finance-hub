# Fixed-37：「元金均等」monthlyPayment 欄位語意衝突——不論數字來自試算器或銀行真實還款表，都會被模擬引擎誤判

## 現況說明 (Current State)

前端 [page.tsx](../../../../hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx) 的 `calculateEstimate()`（80-84 行），針對「元金均等（Equal Principal）」還款方式：

```ts
// page.tsx 80-84 行
} else if (formData.repaymentMethod === "EQUAL_PRINCIPAL") {
  estimate = (P / n) + (P * r);   // 本金 + 利息 = 總額
}
setFormData({ ...formData, monthlyPayment: Math.round(estimate) });
```

算出的是「本金+利息」的總額，UI 上的欄位標籤（371 行）也寫著 `"First Month Est. Payment (JPY)"`，並直接寫入 `formData.monthlyPayment`。欄位下方目前的免責聲明（385-387 行）寫著：

```tsx
* System estimate. Please refer to actual bank details.
```

但後端實際跑模擬的 [MortgageCalculator.java](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)（413-416 行，`fastForwardMortgagesToPresent` 的 254-255 行亦同）對「元金均等」的處理方式完全相反：

```java
// MortgageCalculator.java 413-416 行
if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PRINCIPAL) {
    principal = mortgageMonthlyPayments[i];   // 直接把 monthlyPayment 當成「純本金」
    principal += currentMonthBonus;
    payment = principal + interest;           // 再加上利息才是真正的還款總額
}
```

模擬引擎把 `monthlyPayment` 欄位當成「**純本金**」使用，這個「本金專用」語意也由 [MortgageCalculatorUtil.java](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java) 的 `calculateFixedPaymentOrPrincipal`（93-96 行，`monthlyPortionBalance / remainingMonths`，完全不含利息）以及對應的單元測試 `bonusAwarePrincipal_fullyAmortizesExactlyOverLoanTerm` 所確認，是整個 codebase 的既定 ground truth。

## 問題原因 (Cause)

**這不是「估算準不準」的問題，是欄位語意衝突的問題——跟這個數字究竟是誰算出來的無關。**

即使使用者完全不使用「計算」按鈕，而是照著目前的免責聲明指示，直接抄自己銀行給的真實還款計畫表（返済予定表）上的數字填進這個欄位，一樣會出錯。因為日本銀行對「元金均等」貸款的還款計畫表，每月列出的「お支払い額／應繳金額」慣例上就是本金+利息的**當月合計**（即使表格本身會拆出本金、利息兩欄，headline 數字仍是合計）。所以「請參考銀行實際資料」這句指示，實際上是在引導使用者把「總額」填進一個模擬引擎期待收到「純本金」的欄位——跟試算器本身算錯是完全一樣的後果，工具的估算準確度反而不是問題的核心。

實際影響（以計劃書自己的範例驗算）：貸款 10,000,000、年利率 1.5%、n=360 個月，正確的本金部分應為 `P/n ≈ 27,778`，但無論是試算器算出的 40,278（本金+利息），還是使用者抄自己銀行還款表上的當月應繳總額，都會被模擬引擎誤判為純本金，導致：
- 貸款會在約第 248 個月（而非合約的第 360 個月）就被模擬提前攤還完畢；
- 模擬出的總利息支出被系統性低估；
- 由於「元金均等」的 `mortgageMonthlyPayments[i]` 只有在利率變動或提前還款（REDUCE_PAYMENT）時才會被重新計算，一般情況下這個錯誤數字會**永久卡在資料裡**，持續影響每一次模擬。

## 建議修正方向 (Suggested Fix)

兩個方向擇一（建議方向 A，改動範圍最小、且與既有的攤還單元測試及 `calculateFixedPaymentOrPrincipal` 語意一致）：

- **方向 A**：
  1. 把前端 `calculateEstimate()` 的元金均等分支改成只回傳 `P / n`（純本金），讓試算器輸出的值本身就符合欄位真正的語意。
  2. 把 UI 欄位標籤（371 行）從「First Month Est. Payment (JPY)」改成明確標示「本金部分」的用語，例如「Monthly Principal (JPY)」。
  3. 光改標籤還不夠——這個欄位主要的填寫來源終究是使用者手動對照銀行還款表，因此**必須**同步強化欄位下方的提示文字，明確指引使用者去銀行還款計畫表中找「本金」欄位，而不是「當月應繳總額」（詳見 [Fixed-39.md](Fixed-39.md) 免責聲明強化建議）。
  4. 順手清掉後端目前無人呼叫的 `calculateEstimatedMonthlyPayment` 元金均等分支（同樣有本金+利息的語意錯誤），避免未來被包裝成 API 後誤用。
- **方向 B**：反過來讓 `MortgageCalculator.java` 把 `mortgageMonthlyPayments[i]` 當成「總還款額」，並在計算時內部扣除利息取得本金（`principal = mortgageMonthlyPayments[i] - interest`，跟元利均等分支的寫法一致），同時修改 `calculateFixedPaymentOrPrincipal` 的元金均等分支在提前還款後重新計算時也要「加回」第一個月利息。

無論選哪個方向，都建議加一個迴歸測試：用試算器公式算出的 `monthlyPayment` 建立一筆元金均等房貸，跑完整個 `loanTermMonths` 的攤還迴圈，斷言貸款餘額在合約期滿時（而不是提前）才歸零。

## 最小範例程式碼 (Sample Code)

```ts
// page.tsx calculateEstimate()，方向 A：改成只回傳純本金
} else {
  // EQUAL_PRINCIPAL：每月本金部分（不含利息），與 MortgageCalculator 的語意保持一致
  estimate = P / n;
}
```

## 修正報告 (Resolution Report)

已依照「方向 A」完成全面修復，解決了系統將「總應繳金額」誤判為「純本金」的語意衝突：

1. **前端 (UI)**: 
   - 修改了 `page.tsx` 中的 `calculateEstimate()`：當使用者選擇 `EQUAL_PRINCIPAL` (元金均等) 時，改為只回傳純本金 `P / n`，不再加上首期利息。
   - 實作了 Label 的動態切換：當選擇 `EQUAL_PRINCIPAL` 時，輸入欄位的標籤會從 `First Month Est. Payment (JPY)` 動態切換為 `Monthly Principal (JPY)`，藉由 UX 的引導，確保即使使用者不依賴試算功能，也能知道這裡應該填寫的是純本金。
2. **後端 (Backend)**: 
   - 清理了 `MortgageCalculatorUtil.java` 內已不再使用的 `calculateEstimatedMonthlyPayment` 方法，徹底拔除這個帶有語意衝突且被廢棄的死程式碼。
   - 將原本依賴該方法產生假資料的 `AssetSimulationEngineImplTest.java`，改寫為直接使用標準金融數學公式內聯計算，確保系統測試穩定通過。

經過上述修正，前後端的語意已完全統一為「純本金」。後續的 PR 已將此問題結案。
