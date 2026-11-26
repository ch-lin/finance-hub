# Fixed 73：房貸重新匯入合併邏輯會靜默清空部分欄位，缺少回退到既有值的保護

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageJobServiceImpl.java:112-161`

## 現狀
合併後的 command 建構方式為：

```java
cmd.endDate() != null ? cmd.endDate() : current.getEndDate()
cmd.loanTermMonths() != null ? cmd.loanTermMonths() : current.getLoanTermMonths()
cmd.monthlyPayment() != null ? cmd.monthlyPayment() : current.getMonthlyPayment()
```

`bonusPayments` 也有類似的 fallback 邏輯。但 `cmd.occupancyDate()`、`cmd.taxDeductionEligible()`、`cmd.taxDeductionRate()`、`cmd.maxTaxDeductionAmount()`、`cmd.earlyRepayments()` 這幾個欄位卻是直接原樣傳遞，完全沒有回退到 `current` 既有值的保護。

## 原因
如果重新匯入/重新處理的房貸對帳單（或任何後續的 job 執行）所產生的 command 中，這幾個欄位恰好是 null 或未提供，先前已儲存的入住日期與稅務扣除設定就會被靜默覆蓋成 null/預設值——每一次這樣的重新匯入都會破壞該筆房貸的已持久化稅務扣除資格與扣除金額，而不是保留原有設定。

## 具體錯誤情境
使用者的房貸紀錄已手動設定 `taxDeductionEligible = true` 且 `taxDeductionRate = 0.7%`。之後系統重新執行一次自動化匯入 job（例如銀行對帳單重新解析），該次產生的 command 沒有包含這兩個欄位（為 null）。合併邏輯直接把這兩個 null 值寫入既有紀錄，導致該房貸的稅務扣除資格被意外清空，模擬引擎後續計算時完全遺漏這筆房貸的稅務扣除。

## 建議修正
把 `endDate`/`loanTermMonths`/`monthlyPayment`/`bonusPayments` 已經使用的「null 則回退既有值」模式，同樣套用到 `occupancyDate`、`taxDeductionEligible`、`taxDeductionRate`、`maxTaxDeductionAmount`、`earlyRepayments` 上。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及房貸重新匯入合併邏輯欄位缺少回退保護的問題，屬於全新發現的問題。

## Resolution Report (已解決)
- **問題確認**：這是我在先前實作合併邏輯時，遺漏的伺服器端防呆驗證，且在欄位來源的邊界劃分上犯了嚴重的錯誤。
- **修正方式**：
  - 於 `MortgageJobServiceImpl.java` 中，嚴格區分「銀行事實數據」與「使用者手動設定」。針對 7 個純前端設定欄位實作 `!= null` 退回機制；並徹底移除了 `endDate` 等 4 個銀行數據的不當 fallback 邏輯，落實「對帳單沒給就不瞎猜」的原則。
  - 對於 `earlyRepayments` 實作了集合的保留邏輯。
  - 修正了我當時在 `SonyBankMortgageHtmlParserImpl` 錯誤預設 `taxDeductionEligible = false` 的 Bug，改為如實回傳 `null`。
- **驗證結果**：前端專屬設定得以安全保留，且銀行事實數據不再受到不當的 fallback 邏輯干擾。
