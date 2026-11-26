# Fixed 82-04 Phase 2 Implementation Plan: Service 與 Controller 層欄位擴充

此計畫旨在針對**已經實作完成**的 Phase 2 (Service 與 Controller 層)，將我們在 Phase 1 新增的 4 個年金欄位（`maxBasicPensionMonths`, `kouseiMultiplierPerMille`, `kouseiSalaryCapMonthly`, `kouseiBonusCapPerPayment`）打通。確保這些新欄位能從 Repository 順利透過 DTO、Mapper 一路傳遞到 Controller 提供對外 API。

## User Review Required

> [!IMPORTANT]
> 前一份計畫出現了將既有檔案標示為 `[NEW]` 的嚴重失誤。目前的計畫已完全修正，所有檔案皆為 `[MODIFY]`。本次修改的範圍**僅限於擴充 4 個新欄位**，不會更動原本已經實作完成的 CRUD、單一啟用保護 (Exclusive Toggle) 等商業邏輯。
> 請您檢視下方的檔案清單，確認無誤後點擊 Proceed 授權。

## Open Questions

目前沒有未決問題。

## Proposed Changes

---

### Service 層 (DTOs & Service)

#### [MODIFY] [PensionRuleConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/result/PensionRuleConfigResult.java)
- 追加 4 個新年金欄位（需加入建構子）。

#### [MODIFY] [CreatePensionRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/CreatePensionRuleConfigCommand.java)
- 追加 4 個新年金欄位。

#### [MODIFY] [UpdatePensionRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/UpdatePensionRuleConfigCommand.java)
- 追加 4 個新年金欄位。

#### [MODIFY] [PensionRuleConfigServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImpl.java)
- 在 Create 與 Update 的 mapping 邏輯中，將 Command 中的 4 個新欄位寫入 `PensionRuleConfig` Entity。
- 初始化機制呼叫 `PensionRuleConfigFactory.create` 時，將會依賴 Factory 已完成的擴充自動處理，這裡可能不需改動，但會一併檢查。

#### [MODIFY] [PensionRuleConfigServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImplTest.java)
- 更新單元測試的資料構建 (Arrange)，以及 Assertions (Assert)，確保新欄位有正確存取。

---

### Controller 層 (DTOs & Controller)

#### [MODIFY] [CreatePensionRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/CreatePensionRuleConfigRequest.java)
- 追加 4 個新欄位，並加上對應的 `@Valid` / `@Min` 防呆驗證。

#### [MODIFY] [UpdatePensionRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/UpdatePensionRuleConfigRequest.java)
- 追加 4 個新欄位與驗證。

#### [MODIFY] [PensionRuleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/PensionRuleConfigResponse.java)
- 追加 4 個新欄位以便回傳給前端。

#### [MODIFY] [PensionRuleConfigMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/PensionRuleConfigMapper.java)
- (若是使用 MapStruct，且屬性名稱完全一致，通常不需手動寫對應，但仍需重新編譯或補充缺少的 mapping 設定)。

#### [MODIFY] [PensionRuleConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigControllerTest.java)
- 在 MockMvc 測試用的 JSON payload 內追加 4 個新欄位，並驗證 response 是否有正確回傳這些欄位。

---

## Verification Plan

### Automated Tests
- 將由您手動執行 `mvn clean test`，確保補上新欄位後的單元測試順利通過。

### Manual Verification
- 由您手動啟動伺服器，利用 API 工具測試 GET 與 POST，確認這 4 個新欄位能成功被寫入資料庫並返回。

# Fixed 82-04 Phase 2 欄位擴充: Walkthrough

## 實作總結
我們已經成功為 `PensionRuleConfig` 的 Service 與 Controller 層追加了 Phase 1 所定義的 4 個新年金欄位（`maxBasicPensionMonths`, `kouseiMultiplierPerMille`, `kouseiSalaryCapMonthly`, `kouseiBonusCapPerPayment`）。這些欄位現在可以透過 API 進行完整的新增、更新與讀取。

## 變更項目

### 1. Service 層 (DTOs & Service)
- **DTOs 擴充**: 在 [`PensionRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/result/PensionRuleConfigResult.java)、[`CreatePensionRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/CreatePensionRuleConfigCommand.java) 與 [`UpdatePensionRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/UpdatePensionRuleConfigCommand.java) 中，加入了上述的 4 個新欄位，並匯入 `java.math.BigDecimal`。
- **商業邏輯映射**: 在 [`PensionRuleConfigServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImpl.java) 內，將 `createConfig` 的建構子參數補齊，並且在 `updateConfig` 中加入了這 4 個欄位的更新邏輯。同時也在 `mapToResult` 裡補上了資料轉換。
- **單元測試**: 修正了 [`PensionRuleConfigServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImplTest.java) 內所有初始化 `PensionRuleConfig` 的建構子，並且更新了對應的驗證。

### 2. Controller 層 (DTOs & Controller)
- **Request DTOs 與防呆驗證**: 為 [`CreatePensionRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/CreatePensionRuleConfigRequest.java) 與 [`UpdatePensionRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/UpdatePensionRuleConfigRequest.java) 加入了 4 個新欄位。我們對這些新欄位套用了適當的 JSR-380 驗證，例如：
  - `@Min(value = 1)` 確保 `maxBasicPensionMonths` 至少為 1
  - `@DecimalMin(value = "0.001")` 確保 `kouseiMultiplierPerMille` 大於 0
- **Response DTO**: 於 [`PensionRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/PensionRuleConfigResponse.java) 補齊 4 個屬性。
- **Controller 邏輯**: 於 [`PensionRuleConfigController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigController.java) 中，將接收到的 Request 欄位，順利 set 進入 Command 的 Builder 當中。
- **Controller 測試**: 於 [`PensionRuleConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigControllerTest.java) 中補齊了請求參數，並加入了對回傳 JSON 內容的 Assert 斷言驗證。

## 接下來的驗證步驟

> [!IMPORTANT]
> 請您在本地端執行以下指令以確認編譯與測試皆無錯誤（包含 MapStruct 的自動重新產生邏輯）：
> ```bash
> mvn clean test
> ```
> 確認通過後，您可以啟動伺服器，利用 API 工具手動進行整合測試。
