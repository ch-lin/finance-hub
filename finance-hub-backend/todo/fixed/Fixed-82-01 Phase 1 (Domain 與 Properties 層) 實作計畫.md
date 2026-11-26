# Fixed 82-01 階段一 (Domain 與 Properties 層) 實作計畫

這個計畫旨在實作 Issue 24 藍圖的「階段一」，解決老年基礎年金滿額（816,000）被寫死的問題。我們將會比照 `TaxRuleConfig` 的架構，在 Domain 層建立獨立的年金設定實體，並設定對應的 Properties 使其能在啟動時載入預設值。

## Proposed Changes

---

### Domain 層 (hub-domain)

#### [NEW] [PensionRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/pension/PensionRuleConfig.java)
- **路徑**：`hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/pension/PensionRuleConfig.java`
- **職責**：存放年金法規常數的 JPA Entity。
- **實作細節**：
  - 繼承 `AuditableEntity`。
  - `@Table(name = "pension_rule_configs")`，並對 `name` 欄位加入 Unique 限制 (`uk_pension_rule_config_name`)。
  - 包含欄位：
    - `String name` (設定檔名稱，例如 "default")
    - `Boolean enabled` (是否啟用)
    - `Long maxBasicPensionAmount` (國民年金滿額基準)
    - `Long maxBasicPensionMonths` (國民年金滿額月數)
    - `java.math.BigDecimal kouseiMultiplierPerMille` (厚生年金乘數千分率)
    - `Long kouseiSalaryCapMonthly` (厚生年金月薪上限)
    - `Long kouseiBonusCapPerPayment` (厚生年金單次獎金上限)
  - 使用 Lombok `@Getter`, `@Setter`, `@EqualsAndHashCode(of = { "name", "enabled" })` 簡化程式碼。

---

### App 層 (hub-app) 屬性配置

#### [NEW] [PensionRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultProperties.java)
- **路徑**：`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultProperties.java`
- **職責**：從 `application.properties` 綁定預設設定。
- **實作細節**：
  - 標註 `@Component` 與 `@ConfigurationProperties(prefix = "finance-hub.pension-rule")`。
  - 包含屬性：`name = "default"`, `enabled = true`, `maxBasicPensionAmount`，以及其他對應常數。

#### [NEW] [PensionRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultPropertiesTest.java)
- **路徑**：`hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultPropertiesTest.java`
- **職責**：確保 `@ConfigurationProperties` 綁定正確。
- **實作細節**：利用 `ApplicationContextRunner` 載入設定參數（例如 `finance-hub.pension-rule.max-basic-pension-amount=831700`）進行斷言測試。

---

### 專案設定檔 (Application Properties)

#### [MODIFY] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)
- **路徑**：`hub/src/main/resources/application.properties`
- **變更**：新增 `finance-hub.pension-rule` 相關設定（例如 `finance-hub.pension-rule.max-basic-pension-amount=831700`、`finance-hub.pension-rule.kousei-multiplier-per-mille=5.481` 等），對齊 `TaxRuleDefaultProperties` 的排版風格。若專案中有使用到 `.env` 檔案（如 `.env.base` 等），也會一併更新其預設變數。

## Verification Plan

### Manual Verification
- 請您手動執行 `PensionRuleDefaultPropertiesTest` 單元測試。
- 請您手動執行 `mvn clean test` 確保新增的 Domain Entity 符合編譯與 ORM 規範，且沒有破壞現有的任何邏輯。

# Fixed 82-01 階段一 (Domain 與 Properties 層) 實作完成

這個階段我們順利將 `PensionRuleConfig` 相關的基礎建設實作完成，將原本寫死的老年基礎年金滿額獨立為可設定的 Entity 與 Properties。

## 變更摘要

### 1. 新增 Domain Entity
建立了 [`PensionRuleConfig`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/pension/PensionRuleConfig.java) Entity：
- 繼承自 `AuditableEntity`，支援審計功能（CreatedAt/UpdatedAt）。
- 加入 `name` 與 `enabled` 屬性，並在 `name` 加上唯一限制 (`uk_pension_rule_config_name`)。
- 加入 `maxBasicPensionAmount`、`maxBasicPensionMonths`、`kouseiMultiplierPerMille`、`kouseiSalaryCapMonthly` 與 `kouseiBonusCapPerPayment` 等欄位以儲存年金各項基準。

### 2. 新增 Properties 綁定
建立了 [`PensionRuleDefaultProperties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultProperties.java)：
- 透過 `@ConfigurationProperties(prefix = "finance-hub.pension-rule")` 將設定檔綁定至此類別中。

### 3. 新增單元測試
建立了 [`PensionRuleDefaultPropertiesTest`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultPropertiesTest.java)：
- 利用 `ApplicationContextRunner` 測試 Properties 的注入與預設值是否正確載入（預設為 831,700）。

### 4. 更新 Application.properties
在 [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 中加入了以下預設設定，以供系統啟動與測試：
```properties
finance-hub.pension-rule.max-basic-pension-amount=831700
finance-hub.pension-rule.max-basic-pension-months=480
finance-hub.pension-rule.kousei-multiplier-per-mille=5.481
finance-hub.pension-rule.kousei-salary-cap-monthly=650000
finance-hub.pension-rule.kousei-bonus-cap-per-payment=1500000
```

## 後續驗證

> [!IMPORTANT]
> 請您手動執行 `PensionRuleDefaultPropertiesTest` 單元測試，並執行 `mvn clean test` 確保整個專案的編譯與 ORM 規範未受影響，且相關實體驗證正確無誤。如果驗證順利，我們就可以進入下一階段了。
