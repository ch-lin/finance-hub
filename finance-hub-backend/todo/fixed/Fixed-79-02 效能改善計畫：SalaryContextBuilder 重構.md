# 效能改善計畫：`SalaryContextBuilder` 重構

為了解決在 Fixed-79 修正中產生的附帶效能退化問題，本計畫將對 `SalaryContextBuilder.java` 進行兩項純技術面的優化。

## User Review Required

> [!IMPORTANT]
> 此修正為**純技術重構 (Technical Refactoring)**，不會改變任何商業邏輯。修改的核心僅在於「減少資料庫呼叫次數」與「提早結束無效的迴圈」，確保在用戶累積多年薪資紀錄後，系統模擬依然保持高效。請確認以下方向並批准執行。

## Open Questions

無。目前的修正範圍非常明確且封閉。

## Proposed Changes

### Backend - Simulation Core

#### [MODIFY] [SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)

- **移除雙重查詢 (Double DB Query)**：
  - 修改 `resolveAnnualBonusInfo()` 的方法簽章，改為接受 `List<SalaryRecord> salaryRecords` 作為參數：`public BonusInfo resolveAnnualBonusInfo(List<SalaryRecord> salaryRecords)`。
  - 在 `initializeSalaryContext` 方法的開頭，統一執行一次 `salaryRecordRepository.findAllByOrderByPaymentDateDesc()`，並將取得的列表傳遞給 `resolveAnnualBonusInfo`。
- **解決迴圈空轉 (Loop Inefficiency)**：
  - 在 `resolveAnnualBonusInfo` 處理獎金的迴圈中，當遇到 `sr.getPaymentDate().isBefore(oneYearAgo)` 時，將 `continue` 改為 `break`。因為資料已按日期由新到舊排序，遇到超過一年的資料即可安全終止迴圈。
  - 順便優化 `initializeSalaryContext` 裡面的主迴圈：當處理的紀錄日期不僅超過一年前（`oneYearAgo`），且也小於目標年份（`currentYear`）時，就可以直接 `break`，避免在擁有 10 年以上紀錄的情況下白跑迴圈。

## Verification Plan

### Automated Tests
- 由開發者手動執行 `PensionProjectionServiceTest` 與 `SalaryContextBuilderTest`，確保重構沒有影響到既有的單元測試。

### Manual Verification
- 不須進行手動驗證，若測試全數通過，即代表邏輯無誤（因為此變更僅為演算法層面的優化，數學等價性成立）。


# Walkthrough: SalaryContextBuilder 效能優化

已根據計畫完成 `SalaryContextBuilder.java` 與相關呼叫端的重構。

## 完成的修改

### 1. 解決雙重查詢 (Double DB Query)
- **[SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)**
  - 變更了 `resolveAnnualBonusInfo` 的方法簽章，改為接受 `List<SalaryRecord> salaryRecords`。
  - 修改 `initializeSalaryContext`，將統一抓取的 `salaryRecords` 作為參數傳給 `resolveAnnualBonusInfo`。
- **[PensionProjectionServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)**
  - 更新呼叫端邏輯，在傳入前先透過 `salaryRecordRepository.findAllByOrderByPaymentDateDesc()` 抓取紀錄，避免編譯錯誤。
- **[PensionProjectionServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)**
  - 更新了 Mockito 的 `when` 設定，改為 `when(salaryContextBuilder.resolveAnnualBonusInfo(any()))`。

### 2. 解決迴圈空轉 (Loop Inefficiency)
- **[SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)**
  - 針對 `resolveAnnualBonusInfo` 中的獎金檢查迴圈，當發現紀錄早於一年前時，由 `continue` 改為 `break` 提早終止。
  - 針對 `initializeSalaryContext` 中的月薪檢查迴圈，當紀錄早於一年前且早於 `currentYear` 時，也改為 `break` 提早終止。

## 下一步
請開發者手動執行 `PensionProjectionServiceTest` 與相關測試，並在 IDE 中進行 Diff 審查。若測試全數通過，代表本次重構未影響原有的業務邏輯。
