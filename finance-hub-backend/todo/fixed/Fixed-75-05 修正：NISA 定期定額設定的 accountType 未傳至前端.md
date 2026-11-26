# 修正 Fixed 75-05：NISA 定期定額設定的 accountType 未傳至前端

## 問題簡述與邊際效應分析
因為 `accountType` 欄位在 API 傳遞過程中的 DTO 階層被漏掉，導致前端接收到的所有 NISA 定期定額設定資料都缺乏此資訊（永遠為 `undefined`）。這讓前端用於比對的雙鍵邏輯（`accountType + "_" + fundName`）完全失效。

**有沒有邊際效應？**
**沒有負面邊際效應。** 
這是一個單純把既有欄位補上 DTO 的操作。原本前端就已經期待收到 `accountType` 來做比對，我們只是讓後端 API 誠實地把資料庫裡已經有的這個欄位往外拋，不會影響任何原本正常的邏輯。

## User Review Required
請確認以下計畫清單。一旦您核准（Approve），我將嚴格依照清單使用 IDE API 修改檔案，絕不觸碰清單外的任何程式碼。

## Proposed Changes

### NISA API DTOs

#### [MODIFY] [NisaContributionSettingResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java)
- 新增 `private String accountType;` 欄位。
- 於 `fromEntity()` 方法中補上 `result.setAccountType(entity.getAccountType());`。

#### [MODIFY] [NisaContributionSettingResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java)
- 新增 `private String accountType;` 欄位，讓 MapStruct 能自動將 Result 的同名欄位映射過來。

### Repository

#### [MODIFY] [NisaContributionSettingRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java)
- 補上以 `accountType` 為條件的查詢方法定義，例如 `Optional<NisaContributionSetting> findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(String accountType, String fundName);`。

## Verification Plan

### Automated Tests
- 將交由使用者手動執行測試，我將不會自動代為執行 `gradlew test`。

### Manual Verification
- 請在修改完成後，透過 IDE Diff 介面審查修改內容是否正確。
