# Fixed-84-01 Phase 1: Data Plumbing (Domain, Command/Result, Request/Response, Service, Controller, Mapper)

## Goal
完成 Phase 1 (Data Plumbing)：單純將 `reducedMonths` (縮短月數) 欄位從 API 請求一路貫通到資料庫儲存。此階段不會修改任何計算邏輯，僅負責打通資料流。

## Open Questions
無。皆為單純的欄位擴充與 mapping。

## Proposed Changes

### Backend Domain Layer

#### [MODIFY] [MortgageEarlyRepayment.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageEarlyRepayment.java)
- 新增欄位 `@Column(name = "reduced_months") private Integer reducedMonths;`。
- 更新建構子 `public MortgageEarlyRepayment(...)`，新增 `Integer reducedMonths` 參數。
- 內部賦值：`this.reducedMonths = reducedMonths;`。

### Backend Command & Result Layer (App Layer)

#### [MODIFY] [SaveMortgageRecordCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/command/SaveMortgageRecordCommand.java)
- 在內部的 `EarlyRepaymentCommand` record 宣告中，新增參數 `Integer reducedMonths`。

#### [MODIFY] [MortgageEarlyRepaymentResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/result/MortgageEarlyRepaymentResult.java)
- 新增屬性 `private Integer reducedMonths;`。
- 在靜態方法 `fromEntity(MortgageEarlyRepayment entity)` 內部，加入 `dto.setReducedMonths(entity.getReducedMonths());`。

#### [MODIFY] [MortgageRecordServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java)
- 在 `applyEarlyRepayments` 方法內，當把 `EarlyRepaymentCommand` 轉換為 `MortgageEarlyRepayment` 實體並呼叫建構子時，補上傳入 `erCmd.reducedMonths()`。

### Backend DTO Layer (Controller Layer)

#### [MODIFY] [MortgageRecordRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java)
- 在內部的 `MortgageEarlyRepaymentRequest` record 宣告中，新增參數 `Integer reducedMonths`。

#### [MODIFY] [MortgageEarlyRepaymentResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageEarlyRepaymentResponse.java)
- 在 `MortgageEarlyRepaymentResponse` record 宣告中，新增參數 `Integer reducedMonths`。
- （備註：MapStruct `MortgageRecordMapper` 會自動將 `MortgageEarlyRepaymentResult` 的 `reducedMonths` 映射過來，故無需修改 Mapper 本身）。

## Verification Plan

### Manual Verification
1. 請使用者審核此計畫。
2. 執行此計畫，實作完成後，不需要測試計算邏輯，只需要編譯通過（`mvn clean install -DskipTests` 或修改對應的編譯錯誤）。
3. 測試部分留待 Phase 2/3 完整接通後再進行整合測試。
