# Fixed-96-02 Fix: Phase 2 (Service 與 Controller 層防護實作)

本階段的目標是實作 NISA 額度的 Sandbox 防爆關卡 (Pre-flight Validation) 以及 API 例外處理。

## User Review Required
- **ApiError 的限制**：由於 `ApiError` 可能位於我們無法修改的 `ch.lin.platform` 共用函式庫中，若 `ApiError` 不支援夾帶 `details` Map，我們將在 `WebGlobalExceptionHandler` 中，把 `totalRemaining` 與 `growthRemaining` 格式化為具有特殊結構的 `message` 字串（例如 JSON 字串或特定的 key-value 格式），以便前端 `toast.error()` 解析。

## Proposed Changes

### Exceptions
#### [NEW] [NisaLimitExceededException.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/NisaLimitExceededException.java)
- 新增此業務例外類別，繼承 `RuntimeException`。
- 包含 `long totalRemaining` 與 `long growthRemaining` 屬性，用以傳遞剩餘額度供前端渲染錯誤訊息。

### Controllers & Advice
#### [MODIFY] [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java)
- 新增 `@ExceptionHandler(NisaLimitExceededException.class)` 攔截處理。
- 將 `totalRemaining` 與 `growthRemaining` 封裝，回傳 HTTP `400 Bad Request` 與標準化的 `ApiResponse`。

### Services & Engine
#### [MODIFY] [AssetSimulationEngineImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
- 在 `runSimulationWithOverrides` 函式中，取得 `SimulationData` 後，實作 Pre-flight Validation：
  - 提取傳入的 `overrides.testNisaGrowthMonthlyContribution()` 與 `testNisaTsumitateMonthlyContribution()` 計算年化欲投入總額 (`testTotal`) 與成長枠欲投入額 (`testGrowth`)。
  - 透過 `data.getBaseContext()` 取得既有的 `accumulatedNisaPrincipal` 與 `accumulatedGrowthNisaPrincipal`。
  - 計算 `totalRemaining` = `18_000_000L - accumulatedNisaPrincipal` 與 `growthRemaining` = `12_000_000L - accumulatedGrowthNisaPrincipal`。
  - 若 `testTotal > totalRemaining` 或 `testGrowth > growthRemaining`，則拋出 `NisaLimitExceededException(totalRemaining, growthRemaining)`。

## Verification Plan
### Automated Tests
預期單元測試 `AssetSimulationEngineImplTest` 與 `WebGlobalExceptionHandlerTest` 會有編譯錯誤，需在 Phase 3 一併修復。本階段暫不執行 `mvn test`。
### Manual Verification
等待全部 Phase 完成後，啟動後端應用程式並使用 Swagger 或前端 UI 進行實際 API 呼叫測試。
