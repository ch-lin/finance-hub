# [Fixed-40 & Fixed-41] 修復 ESPP 與 DC Matching 的 YTD 滲漏及錯誤的外推與雙重計算

本計畫旨在根據 Fixed-40 與 Fixed-41 的指示，徹底解決模擬器第一年（過渡年）計算現金流時的「基準不對齊」與「ESPP 錯誤外推」問題。

## User Review Required

> [!IMPORTANT]
> **本次計畫將執行以下根本解法（同時修好 Fixed-40 與 Fixed-41）**：
> 1. 完全刪除 ESPP 的錯誤加回與外推預測邏輯。
> 2. 導入 `computeYtdNetIncomeBaseline`，讓減法的兩邊（全年度與 YTD）的計算基準完全一致：都是「毛薪資減去法定稅金與社保」，不包含任何如 ESPP 或 DC Matching 等未在 `netIncome` 裡扣除的雜項。

## Proposed Changes

### Core Pipeline (Simulation Engine)

#### [MODIFY] [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **移除錯誤加法**：在 `applyYearEndAdjustment` 中，刪除將 `projectedAnnualEsppThisYear` 加回 `netIncome` 的 `if` 區塊（約 244-246 行）。
- **新增對齊基準方法**：新增 `computeYtdNetIncomeBaseline` 私有方法，透過 `ytdGrossIncome - ytdIncomeTax - ytdResidentTax - ytdHealthInsurance - ytdPension - ytdEmploymentInsurance` 來重建乾淨的 YTD 淨利基準。
- **修正 YTD 扣減邏輯**：在 `applyCashflowAndSettlement` 中，將 `ytdNetIncome = context.getYtdNetAmount();` 替換為 `ytdNetIncome = computeYtdNetIncomeBaseline(context);`，一次性解決 ESPP 與 DC Matching 的 YTD 滲漏。

---

### Context & Factories

#### [MODIFY] [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- **清理死欄位**：刪除 `private Long projectedAnnualEsppThisYear;`。不再需要追蹤 ESPP 的年度預測，因為我們不對未來的 ESPP 進行無根據的外推假設。

#### [MODIFY] [`SalaryContextBuilder.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)
- **清理死程式碼**：在 `initializeSalaryContext` 中，刪除整段用以計算歷史 ESPP 平均值並外推到未來剩餘月份的邏輯（約 181-198 行），以及對 `setProjectedAnnualEsppThisYear` 的呼叫。

## Verification Plan

### Manual Verification
1. 啟動後端，透過 Swagger 或前端送出第一年的模擬請求。
2. 檢查回傳的 `remainingCashThisYear` 與 `projectedLiquidCash`，確認不再有 ESPP 或 DC Matching 金額造成的異常暴增。
3. 驗證過渡年之後的模擬年份，現金流不會被不正確的 ESPP 預測所干擾。
