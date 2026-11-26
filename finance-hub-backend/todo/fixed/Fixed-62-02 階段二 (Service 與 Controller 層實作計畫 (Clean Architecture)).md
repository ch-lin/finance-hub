# Fixed-62 Phase 2: Service 與 Controller 層實作計畫 (Clean Architecture)

此計畫將針對 `TaxRuleConfig` 進行完整的 Clean Architecture 實作，涵蓋 API 請求接收、DTO 轉換、業務邏輯處理以及資料庫存取。

> [!IMPORTANT]
> 此計畫完全遵循您要求的 Clean Architecture 規範：
> `Request` (Controller) -> `Command` (Service) -> `Entity` (Domain) -> `Result` (Service) -> `Response` (Controller)。
> 計算引擎將在 Phase 3 再進行實作。

## User Review Required

1. **唯一啟用機制**：在 `TaxRuleConfigService` 的 `create` 或 `update` 方法中，如果傳入的 `enabled` 為 `true`，服務層將負責把資料庫中**所有其他設定檔**的 `enabled` 設為 `false`，確保同一時間只有一組稅務設定生效，行為模式與 `FinanceHubConfig` 保持一致。

## Open Questions

無。

## Proposed Changes

---

### App 層 (Service, Repository, Command, Result)

建立專屬於稅務設定的 Service 與資料庫存取介面，並定義跨層傳遞的 Command 與 Result DTO。

#### [NEW] [TaxRuleConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleConfigFactory.java)
- 建立 Factory 類別，負責將 `TaxRuleDefaultProperties` 轉換為 `TaxRuleConfig` 實體。

#### [NEW] [TaxRuleConfigRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java)
繼承 `JpaRepository<TaxRuleConfig, Long>`。
新增方法：`Optional<TaxRuleConfig> findByEnabledTrue();` 與 `Optional<TaxRuleConfig> findByName(String name);`

#### [NEW] [CreateTaxRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/command/CreateTaxRuleConfigCommand.java)
#### [NEW] [UpdateTaxRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/command/UpdateTaxRuleConfigCommand.java)
Service 層接收的創建與更新參數物件 (含 `List<TaxBracketCommand>` 用於傳遞級距表)。

#### [NEW] [TaxRuleConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/result/TaxRuleConfigResult.java)
#### [NEW] [TaxBracketResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/result/TaxBracketResult.java)
#### [NEW] [AllTaxRuleConfigsData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/result/AllTaxRuleConfigsData.java)
Service 層回傳的 DTO (對應實體資料)。

#### [NEW] [TaxRuleConfigService.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/TaxRuleConfigService.java)
#### [NEW] [TaxRuleConfigServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/TaxRuleConfigServiceImpl.java)
- 實作 Service 介面，依賴 `TaxRuleConfigRepository`、`TaxRuleDefaultProperties` 與 `TaxRuleConfigFactory`。
- **啟動初始化**: 實作 `@EventListener(ApplicationReadyEvent.class)`，在應用程式啟動且資料庫無資料時，自動透過 Factory 將 default properties 寫入資料庫並設為 enabled。
- **建立/更新邏輯**: 在儲存前，檢查新設定若設為 `enabled = true`，則呼叫 `repository.findAllByEnabledTrue()` 將目前所有已啟用的 config 先設為 false，再儲存新 config，確保「只有一個生效」。

---

### Controller 層 (API Endpoints, Request, Response, Mapper)

建立對外的 REST Controller 並使用 MapStruct 自動轉換 DTO。

#### [NEW] [CreateTaxRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/CreateTaxRuleConfigRequest.java)
#### [NEW] [UpdateTaxRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/UpdateTaxRuleConfigRequest.java)
#### [NEW] [TaxBracketRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/TaxBracketRequest.java)
API Payload 結構，包含 `@Valid` 校驗與基礎控除、級距陣列等欄位。

#### [NEW] [TaxRuleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/TaxRuleConfigResponse.java)
#### [NEW] [TaxBracketResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/TaxBracketResponse.java)
#### [NEW] [AllTaxRuleConfigsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/AllTaxRuleConfigsResponse.java)
API 回應結構。

#### [NEW] [TaxRuleConfigMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/TaxRuleConfigMapper.java)
MapStruct Mapper，負責 `TaxRuleConfigResult -> TaxRuleConfigResponse` 的轉換。

#### [NEW] [TaxRuleConfigController.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java)
實作以下端點：
- `GET /configs/tax-rule`：回傳所有 profile 名稱與當前啟用的名稱。
- `GET /configs/tax-rule/{name}`：回傳特定 profile 的詳細參數與級距表。
- `POST /configs/tax-rule`：新建設定。Controller 內將 `Request` 轉為 `Command`。
- `PATCH /configs/tax-rule/{name}`：更新設定。
- `DELETE /configs/tax-rule/{name}`：刪除設定。

---

## Verification Plan

### Automated Tests
- 新增 `TaxRuleConfigServiceImplTest` 以驗證 Service 邏輯（包含 `enabled` 的切換與 CRUD）。
- 新增 `TaxRuleConfigControllerTest` 驗證端點 `@WebMvcTest`。
- **(注意：由於您強烈要求嚴禁自動執行測試，上述測試撰寫完成後，需由您手動執行。)**

### Manual Verification
- 啟動後端應用程式。
- 透過 Swagger UI 或 Postman 手動測試 `/configs/tax-rule` 相關的 GET, POST, PATCH, DELETE 請求。
