# Feature: Mortgage Monthly Payment Estimator (房貸月付額試算器)

Add a real-time "Estimate Monthly Payment" calculation feature to the frontend Mortgage form, allowing users to automatically calculate rough monthly payments based on loan amount, interest rate, term, and repayment method, while keeping the flexibility to override the value manually.

## User Review Required

### 關於「還款型態」是否能更改的釋疑 (Can the Repayment Type be Changed?)
您問到：「法律上決定還款型態之後就不能改對吧？」

答案是：**在真實世界的銀行合約中，通常是的。** 
當您與銀行對保、簽訂房貸合約時，還款型態（元利均等 或 元金均等）就會寫死在合約裡。如果您日後想更改，通常需要向銀行申請「合約變更（条件変更）」，這往往需要重新審核並支付手續費，甚至可能需要重新轉貸（Refinance）。這不是可以隨意每個月切換的。

**但在我們的系統（軟體）設計上：**
雖然現實中不能改，但我們在軟體的「編輯 (Edit)」畫面中，**目前仍保留讓使用者修改的彈性**。這是為了防呆——如果使用者第一天建檔時不小心選錯了，他事後還可以點進去修改修正。但這個欄位在現實意義上，一旦選定確實是跟著這筆房貸一輩子的。

### 房貸還款型態的差異解釋 (Explanation of Repayment Types)
您問到兩種型態的差別，以及「要還的總額是否不一樣」。答案是：**是的，總繳利息會不一樣！** 

以下是兩種方式的詳細比較，我們的計算器會根據使用者的選擇，套用不同的公式：

#### 1. 本息平均攤還 (元利均等 / Equal Payment)
* **特色**：把「本金＋總利息」平均打散，**每個月繳的「總金額」都一模一樣**。
* **計算器顯示**：算出來的數字（例如 10 萬），代表未來每個月都是固定繳 10 萬。
* **優缺點**：
  * **優點**：每個月還款金額固定，非常容易做家庭理財規劃。
  * **缺點**：因為前期還的「本金」比較少（一開始繳的錢很大比例是在還利息），導致本金消耗得慢，所以最終的**「總利息支出較高」**。

#### 2. 本金平均攤還 (元金均等 / Equal Principal)
* **特色**：每個月還的「本金」是固定的，再加上當月剩下的本金所產生的利息。因為本金每個月都在減少，所以**每個月繳的總金額會越來越少**。
* **計算器顯示**：因為每個月金額不同，我們的計算器會算給使用者**「第一個月（最貴）的還款金額」**。
* **優缺點**：
  * **優點**：因為前期用力還本金，本金消耗得快，所以最終的**「總利息支出較低（比較省錢）」**。
  * **缺點**：前幾年的還款壓力非常大，理財規劃比較緊繃。

## Proposed Changes

### Frontend Component

#### [MODIFY] `page.tsx` (file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
- **Add Calculation Logic**: 
  Implement a `calculateEstimate()` function inside the component using standard financial formulas:
  - *Equal Payment (元利均等)*: `PMT = Principal * [r(1+r)^n] / [(1+r)^n - 1]`
  - *Equal Principal (元金均等)*: First month = `(Principal / n) + (Principal * r)`
  *(where `r` is the monthly interest rate, and `n` is the total months).*
- **Update UI Layout**:
  - Modify the `Monthly Payment (JPY)` input field to include an adjacent **"Estimate (計算)"** button (using a `Calculator` icon).
  - Add a small, italicized disclaimer text below the input field: *"此為系統粗估，實際還款金額請依銀行資訊為準 (System estimate. Please refer to actual bank details)."*
- **Validation**: 
  - Ensure the calculation only runs if `totalLoanAmount`, `interestRate`, and `loanTermMonths` are filled out and valid. Show a toast error if required fields are missing when the button is clicked.

## Verification Plan

### Manual Verification
1. Open the UI and click "New Mortgage".
2. Input `10,000,000` for Amount, `1.5%` for Interest, and `360` (30 years) for Term.
3. Click the "Estimate" button.
4. Verify the monthly payment populates correctly (`34,512` JPY for Equal Payment).
5. Switch to "Equal Principal" and recalculate. Verify it shows the first month's payment (`40,277` JPY).
6. Manually change the populated number and save to ensure manual overrides are respected.
