# Fixed 82-03 Phase 2 Implementation Plan: Service 與 Controller 層 (Clean Architecture)

這個階段的目標是實作 `PensionRuleConfig` 的 Repository、Service、Controller 與所有的 DTO/Mapper。我們會完全比照 `TaxRuleConfig` 的架構來建立對應的元件，以保持系統一致性並落實 Clean Architecture。

## Proposed Changes

以下是預計新增的檔案清單與功能說明：

---

### Repository 層

#### [NEW] [PensionRuleConfigRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/PensionRuleConfigRepository.java)
- 繼承 `JpaRepository<PensionRuleConfig, UUID>`。
- 加入 `Optional<PensionRuleConfig> findByName(String name)` 方法。

---

### Service 層 (DTOs & Service)

#### [NEW] [PensionRuleConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/result/PensionRuleConfigResult.java)
- Service 回傳的單一設定資料結構 (包含 id, name, enabled, maxBasicPensionAmount, maxBasicPensionMonths, kouseiMultiplierPerMille, kouseiSalaryCapMonthly, kouseiBonusCapPerPayment)。

#### [NEW] [AllPensionRuleConfigsData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/result/AllPensionRuleConfigsData.java)
- Service 回傳的所有設定列表。

#### [NEW] [CreatePensionRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/CreatePensionRuleConfigCommand.java)
- 用於新增設定的命令物件 (包含 name, enabled, 以及各項年金基準常數)。

#### [NEW] [UpdatePensionRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/command/UpdatePensionRuleConfigCommand.java)
- 用於更新設定的命令物件 (包含 enabled 以及各項年金基準常數)。

#### [NEW] [PensionRuleConfigService.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigService.java)
- 介面：定義 `getAll`, `create`, `update`, `delete` 以及啟用某配置的邏輯。

#### [NEW] [PensionRuleConfigServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImpl.java)
- 實作 CRUD 邏輯。
- 負責呼叫 `PensionRuleConfigFactory` 生成預設值（當前沒資料時）。
- 實作「啟用某 Profile 時，將其他同名 Profile 設為未啟用」的排他邏輯。

#### [NEW] [PensionRuleConfigServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImplTest.java)
- 測試 Service 的商業邏輯（CRUD 與預設值載入）。

---

### Controller 層 (DTOs & Controller)

#### [NEW] [CreatePensionRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/CreatePensionRuleConfigRequest.java)
- 接收前端的 Create 請求，包含 `@Valid` 防呆驗證（例如數值不得為負）。

#### [NEW] [UpdatePensionRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/UpdatePensionRuleConfigRequest.java)
- 接收前端的 Update 請求。

#### [NEW] [PensionRuleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/PensionRuleConfigResponse.java)
- 回傳單一設定給前端 (包含所有年金基準常數欄位)。

#### [NEW] [AllPensionRuleConfigsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/AllPensionRuleConfigsResponse.java)
- 回傳列表給前端。

#### [NEW] [PensionRuleConfigMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/pension/PensionRuleConfigMapper.java)
- 負責 DTO 與 Command / Result 間的轉換。

#### [NEW] [PensionRuleConfigController.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigController.java)
- 提供 `/api/finance/configs/pension-rule` 的 RESTful 端點 (GET, POST, PUT, DELETE)。

#### [NEW] [PensionRuleConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigControllerTest.java)
- 測試 Controller 層的 Endpoint 與 HTTP 狀態碼。

---

## Verification Plan

### Automated Tests
- 請您手動執行 `mvn clean test` 確保新建立的單元測試 (`PensionRuleConfigServiceImplTest`, `PensionRuleConfigControllerTest`) 順利通過。

### Manual Verification
- 請使用 API 工具（如 Postman、cURL 或 Swagger）手動打打看 `/api/finance/configs/pension-rule`，確認 CRUD 行為是否如預期。

# Fixed 82-03 階段二 (Service 與 Controller 層) 實作完成

這個階段我們順利將 `PensionRuleConfig` 相關的業務邏輯層（Service）與對外介面層（Controller）實作完成，完全比照 `TaxRuleConfig` 的架構設計，支援了完整的 CRUD 與 `Enabled` 單一啟用切換機制。

## 變更摘要

### 1. Repository 層
建立了 [`PensionRuleConfigRepository`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/PensionRuleConfigRepository.java) 介面：
- 提供 `findFirstByEnabledTrue()`、`findAllByEnabledTrue()` 等方法，支援查詢目前啟用狀態與獨占啟用的防護機制。

### 2. Service 層 (業務邏輯)
建立了 [`PensionRuleConfigServiceImpl`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImpl.java) 與介面，並實作完整的業務邏輯：
- **初始化機制**：在 `ApplicationReadyEvent` 觸發時檢查資料庫，若無資料則自動透過 Factory 寫入帶有 `831700` 預設值的設定檔。
- **單一啟用防護 (Exclusive Toggle)**：建立與更新設定時，若傳入 `enabled: true`，會自動將其他已啟用的設定檔切換為 `false`。
- **系統保留名稱保護**：預設設定檔名稱 (`default`) 禁止手動創建與刪除。
- 建立了對應的 Command 與 Result DTOs，嚴格隔離 Entity 避免其污染外層。
- 完成了 [`PensionRuleConfigServiceImplTest`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/pension/PensionRuleConfigServiceImplTest.java) 測試覆蓋。

### 3. Controller 層 (對外 API)
建立了 [`PensionRuleConfigController`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigController.java) 與 MapStruct Mapper：
- 開放 `GET /configs/pension-rule` (取得清單)、`GET /{name}`、`POST`、`PATCH /{name}`、`DELETE /{name}` 等 REST API。
- 建立了 `@Valid` 標註的 Request DTO，並加入 `@Min(0)` 等防呆驗證。
- 完成了 [`PensionRuleConfigControllerTest`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/PensionRuleConfigControllerTest.java) 使用 MockMvc 進行端點驗證。

## 後續驗證

> [!IMPORTANT]
> 請您手動執行 `mvn clean test` 確保整個專案的單元測試順利通過，沒有受到此階段重構的影響。您也可以手動啟動伺服器，透過 Postman 或 cURL 驗證 `/configs/pension-rule` 的端點與 `enabled` 獨占切換邏輯是否正確。若測試確認無誤，我們可以繼續進入**階段三 (Simulation Context 隔離層)**。
