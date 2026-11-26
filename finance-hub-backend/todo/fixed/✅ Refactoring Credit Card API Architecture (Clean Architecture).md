# Implementation Plan: Refactoring Credit Card API Architecture (Clean Architecture)

將 `creditcard` 模組的 API 架構進行深度的 Clean Architecture 重構。目前 `CreditCardTransactionService` 會將 Domain Entity (`CreditCardTransaction`) 甚至連動的 `CreditCardStatement` 相關邏輯直接回傳給 Controller。這種「Entity 外洩」違反了分層隔離原則。

**重構核心目標與前端合約保證：**
1. **內部傳遞**：Service 層必須回傳繼承自 `BaseResult` 的 `CreditCardTransactionResult`。只有繼承 `BaseResult`，才能確保 `ownerUserId` 與 `privacyLevel` 欄位被正確傳遞到外層，讓攔截器 (Interceptor / PrivacyMaskingUtil) 統一進行過濾或遮罩。
2. **對外合約 (前端)**：傳給前端的資料 (DTO/Response) **絕對不可以包含** `ownerUserId` 與 `privacyLevel`。這點至關重要，前端不需要也不應該獲得這些內部識別與隱私權限標籤。我們將嚴格把關 `CreditCardTransactionResponse`，確保這兩個欄位在 Mapper 轉換階段被徹底隔絕，不會外流。

## Design Decision: Object Mapping Strategy
> [!NOTE]
> **Domain -> Result (Service 層)**：
> 採用 **手刻靜態方法 `fromEntity()`** 進行轉換。
> *理由*：內部的 Domain 轉 Result 通常是非常純粹的一對一屬性複製，邏輯簡單，「殺雞焉用牛刀」，不需要依賴 MapStruct。更重要的是，手寫的轉換容易撰寫單元測試，能避免 MapStruct 自動產生的程式碼在 Jacoco 覆蓋率報告中造成困擾與雜訊。
> 
> **Result -> Response (Controller 層)**：
> 暫時維持使用 **MapStruct (`Mapper`)** 進行轉換。
> *理由*：雖然 MapStruct 產生的 code 對 Jacoco 覆蓋率依然不太友善，但 Controller 邊界涉及 API 欄位的對應與可能的多重轉換，我們目前在 Controller 層「將就一下」維持原狀，未來有機會再來針對這層的最佳實踐做思考。

## Strict Rules Enforcement
> [!IMPORTANT]
> 1. **嚴禁使用 git 回溯檔案 (git restore)**。
> 2. **嚴禁使用腳本語言 (bash, sed, python 等) 以及命令列進行修改或新增檔案**。
> 3. **進行之前必須把現在的 code 全數 commit，並且從最新狀態切出新分支** (Branch: `feature/creditcard-api-clean-arch-refactor`)。
> 4. **嚴禁自動跑編譯與單元測試**，一切交給使用者執行來節省 token 跟時間。
> 5. **嚴禁超出指定的範圍**：只允許修改 `creditcard` 相關的檔案，絕不干涉其它模組。
> 6. **測試相容性** : 相關單元測試 (如 XXXXControllerTest) 的 Mock 資料與 Assert 邏輯均須同步替換且嚴禁修改測試所驗證的業務行為。

## Proposed Changes (File Manifest)

### 1. Service Layer (New Result Class)
建立對應的 Result 類別，它必須負責承載 Entity 的資料以及 BaseResult 的隱私欄位。

#### [NEW] `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/result/CreditCardTransactionResult.java`
- 屬性包含：`id`, `cardName`, `transactionDate`, `storeName`, `amount`, `memo` 等與 Entity 對應的基本屬性。
- **必須繼承 `BaseResult`**。
- 實作手刻的靜態工廠方法 `public static CreditCardTransactionResult fromEntity(CreditCardTransaction entity)`：
  - 手動複製基本屬性。
  - **關鍵**：將 Entity 身上的 `ownerUserId` 與 `privacyLevel` (若有) 賦值給父類別 `BaseResult`。

### 2. Service Implementation (Refactoring Returns)
修改服務層的回傳型別，徹底根絕 Domain Entity 往外傳遞。

#### [MODIFY] `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionService.java`
- `getTransactions(...)`: 回傳 `Page<CreditCardTransactionResult>`。
- `addTransaction(...)`: 回傳 `CreditCardTransactionResult`。

#### [MODIFY] `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java`
- `getTransactions`: 回傳時呼叫 `transactionRepository.findAll(...).map(CreditCardTransactionResult::fromEntity)`。
- `addTransaction`: 將 `transactionRepository.save(transaction)` 的結果透過 `CreditCardTransactionResult.fromEntity(...)` 包裝後回傳。

#### [MODIFY] `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardServiceImpl.java`
- `processFile`: 將原本直接放入 `FileProcessingResult` 的 `statement.getTransactions()` 修改為轉換後的 Result 清單：
  `statement.getTransactions().stream().map(CreditCardTransactionResult::fromEntity).toList()`。

### 3. Controller & Mapper Layer (Strict Contract Enforcement)
確保對前端的合約 (`CreditCardTransactionResponse`) 不受內部重構的污染。

#### [VIEW] `finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/creditcard/dto/CreditCardTransactionResponse.java`
- 確認該類別內 **沒有** 定義 `ownerUserId` 與 `privacyLevel`。維持乾淨對前端的 Payload 結構。

#### [MODIFY] `finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/creditcard/dto/CreditCardTransactionMapper.java`
- 將原本的 `CreditCardTransactionResponse toResponse(CreditCardTransaction entity);` 
- 修改為 `CreditCardTransactionResponse toResponse(CreditCardTransactionResult result);`。
- MapStruct 會自動對應欄位，且因為 `CreditCardTransactionResponse` 中不存在 `ownerUserId` 與 `privacyLevel`，MapStruct 會自然將其丟棄，達成隱私與架構隔離。

#### [MODIFY] `finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/creditcard/CreditCardTransactionController.java`
- 將依賴的 Mapper 參數型別修正。
- 移除不再需要的 Domain Entity Import (`CreditCardTransaction`)，確保 Controller 層達到 100% Entity Free。

## Verification Plan

### Agent Action Required Before Execution
1. **Commit & Branch**: 實作前，Agent 必須確認目前狀態已 Commit，然後才建立新分支 (`feature/creditcard-api-clean-arch-refactor`)。

### Manual Verification (User Action Required)
1. **Review Diff**: 由使用者檢閱 Diff：
   - 確認 `CreditCardTransactionResult` 正確繼承了 `BaseResult` 並且負責裝載內部欄位。
   - 確認 `CreditCardTransactionResponse` **保持乾淨，無任何 `ownerUserId` 相關欄位**，確保前端合約安全。
2. **Compile & Test**: 由使用者手動執行 `mvn clean install`，確保編譯通過且自動化單元測試無誤 (Agent 嚴禁自行觸發任何編譯或測試指令)。
