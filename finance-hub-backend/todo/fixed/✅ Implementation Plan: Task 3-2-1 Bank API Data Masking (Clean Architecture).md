# Implementation Plan: Task 3-2-1 Bank API Data Masking (Clean Architecture)

為確保 `bank` 模組的 API 針對 `AGGREGATE_ONLY` 或 `PRIVATE` 的敏感交易紀錄不外洩給群組內的其他無權限成員，我們將在 Mapper 轉換至前端 Response 的最後一哩路，實作基於 MapStruct 的 `@AfterMapping` 遮罩邏輯。

此實作將遵守「全域隱私防護」的核心精神：由 `PrivacyMaskingUtil` 統一判斷遮罩條件，並且**不影響 Extension API (專屬本人同步的接口)**。

## Strict Rules Enforcement
> [!IMPORTANT]
> 1. **嚴禁使用 git 回溯檔案 (git restore)**。
> 2. **嚴禁使用腳本語言 (bash, sed, python 等) 以及命令列進行修改或新增檔案**。
> 3. **進行之前必須把現在的 code 全數 commit，並且從最新狀態切出新分支** (Branch: `feature/bank-api-masking`)。
> 4. **嚴禁自動跑編譯與單元測試**，一切交給使用者執行來節省 token 跟時間。
> 5. **嚴禁超出指定的範圍**：只允許修改 `bank` 相關的檔案，絕不干涉其它模組。
> 6. **測試相容性**：如有相關單元測試的 Mock 資料與 Assert 邏輯均須同步替換且嚴禁修改測試所驗證的業務行為。
> 7. **嚴格頂層 Import**：所有類別與列舉（如 CurrencyCode、MonetaryAmount）皆必須於頂部顯式宣告 import，嚴禁使用任何 inline FQCN。

## Proposed Changes (File Manifest)

### 1. Controller Layer (Injecting Context)
我們必須把「當下發出 Request 的使用者是誰 (currentUserId)」往下傳給 Mapper，讓 Mapper 有足夠的資訊判斷是否要啟動遮罩。

#### [MODIFY] [`BankTransactionController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/bank/BankTransactionController.java)
- 匯入 `ch.lin.finance.hub.backend.api.app.util.SecurityUtil`。
- 修改 `getTransactions` 方法：
  在 `map(bankTransactionMapper::toResponse)` 處，改為傳遞 `SecurityUtil.getCurrentUserId()` 進入 `toResponse` 方法。
  *(例如：`transactions.map(result -> bankTransactionMapper.toResponse(result, SecurityUtil.getCurrentUserId()))`)*
- 修改 `addTransaction` 方法：
  在 `bankTransactionMapper.toResponse(created)` 處，補上 `SecurityUtil.getCurrentUserId()` 參數。

### 2. Mapper Layer (Masking Logic)
在 MapStruct 的自動對應完成後，掛載 `@AfterMapping` 攔截器，執行「共通原則」並清空敏感欄位。

#### [MODIFY] [`BankTransactionMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/bank/dto/BankTransactionMapper.java)
- 匯入 `ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil`。
- 匯入 `org.mapstruct.AfterMapping`、`org.mapstruct.MappingTarget`、`org.mapstruct.Context`。
- 修改 `toResponse` 方法簽名，加入 `@Context String currentUserId` 參數。
- 新增 `default void applyMasking(...)` 方法並掛上 `@AfterMapping` 註解。
  - 邏輯：呼叫 `PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())`。
  - 如果回傳為 `true`（代表無權限查看細節），則：
    - `response.setDescription(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.BANK));`
    - `response.setMemo(null);`

## Verification Plan

### Agent Action Required Before Execution
1. **Commit & Branch**: 實作前，Agent 必須確認目前狀態已由使用者 Commit，若尚未 commit 請求使用者給予 commit 許可，commit 後由 Agent 建立新分支 (`feature/bank-api-masking`) 才能開始執行。(嚴禁動到上層wapper的repo，僅commit submodule)
2. **Update Progress**: 實作完成更新PROGRESS.md

### Manual Verification (User Action Required)
1. **Review Diff**: 由使用者檢閱 Diff：
   - 確認 `BankTransactionMapper` 成功實作了 `@AfterMapping`，且判斷邏輯正確使用了 `PrivacyLevel`、`currentUserId` 及 `ownerUserId`。
   - 確認 `BankExtensionController` 沒有被意外修改（保持自己的隔離性）。
2. **Compile & Test**: 由使用者手動執行 `mvn clean install`，確保編譯通過且自動化單元測試無誤 (Agent 嚴禁自行觸發任何編譯或測試指令)。
