# 實作計畫：Fixed-62 階段一 (Domain 與 Properties 層)

本計畫僅涵蓋 Fixed-62 藍圖中的第一階段：建立獨立的稅制領域實體（Domain Entities）與對應的 Spring Boot Properties 設定，並包含對應的單元測試。

## User Review Required

> [!WARNING]
> 本計畫聚焦於 Domain 與 Properties 的地基建置。請確認以下規劃的 Package 路徑與類別命名是否符合您專案的架構慣例。確認無誤後請 Approve，我們即可開始產出程式碼。

## Proposed Changes

### Domain 層 (JPA Entities)

#### [NEW] `ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java`
- 標記為 `@Entity`，專責儲存稅務常數。
- 包含純量欄位：
  - `Long basicDeductionIncomeTax`
  - `Long basicDeductionHabitantTax`
  - `Long dependentIncomeThreshold`
- 定義 `@OneToMany(mappedBy = "taxRuleConfig", cascade = CascadeType.ALL, orphanRemoval = true)` 關聯至 `TaxBracket`。

#### [NEW] `ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java`
- 標記為 `@Entity`，儲存具體的級距數字。
- 包含欄位：
  - `Long maxSalary` (該級距薪資上限)
  - `BigDecimal multiplier` (乘數/稅率比例)
  - `Long offset` (加減扣除額)
  - `@Enumerated(EnumType.STRING) TaxBracketType bracketType` (級距類別)
  - `@ManyToOne` 關聯回 `TaxRuleConfig`。

#### [NEW] `ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java`
- 建立 Enum：
  - `EMPLOYMENT_INCOME_DEDUCTION` (薪資所得扣除)
  - `INCOME_TAX_RATE` (所得稅率)

---

### Properties 層 (Spring Boot Configuration)

#### [NEW] `ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultProperties.java`
- 標記為 `@ConfigurationProperties(prefix = "finance-hub.tax-rule")`。
- 內部結構對應 `TaxRuleConfig` 的純量欄位，並使用 `Map<TaxBracketType, List<BracketProp>>` 來接收 YAML 中不同類別的級距清單。
- 此原生機制完美支援 `.env` 或 OS 環境變數覆蓋。

---

### 單元測試 (Unit Tests)

#### [NEW] `ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultPropertiesTest.java`
- 撰寫單元測試以驗證 `@ConfigurationProperties` 的載入邏輯。
- 測試情境 1：能正確讀取標準設定檔的預設稅制與級距表。
- 測試情境 2：模擬 Spring Boot 環境變數（Environment 覆蓋機制）注入時，數值能被正確覆蓋。

## Verification Plan

### Automated Tests (由使用者手動執行)

> [!IMPORTANT]
> 遵照您的指示，**我絕對不會在背景自動執行任何測試指令或終端機指令**。
> 待實作完成並經您 Diff 審查後，請您在本地端自行執行以下指令進行驗證：
> 
> `mvn clean test -pl hub-app -Dtest=TaxRuleDefaultPropertiesTest`

### Manual Verification
- 檢視生成的 Entity 程式碼，確保 Import 皆為頂層宣告（無 FQCN），且符合您的 Coding Style。
- 檢視 `application.properties` 需要補上的 YAML 結構範例是否符合您的期待。

# Walkthrough: Fixed-62 Phase 1 (Domain & Properties)

依照您的 Approve 授權，我已完成 Fixed-62 第一階段的程式碼建置。

## 完成項目

### 1. 新增 Domain Entities
為了避免 `FinanceHubConfig` 變成 God Object，我建立了獨立的 `tax` package 並實作了以下實體：
- [TaxRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)
- [TaxBracket.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java)
- [TaxBracketType.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)

這些類別皆繼承自 `AuditableEntity`，且 `import` 皆為頂層宣告，完全遵循了乾淨架構與專案規範。

### 2. 建立 Properties 綁定機制
- [TaxRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultProperties.java)：使用 `@ConfigurationProperties` 以便後續能自動支援 `.env` 覆蓋。

### 3. 配置預設參數
- 更新了 [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)，加入了純量常數以及部分 `TaxBracket` 的範例設定（例如 `EMPLOYMENT_INCOME_DEDUCTION` 陣列）。

### 4. 單元測試
- 建立了 [TaxRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultPropertiesTest.java) 用於驗證 properties 機制是否生效。

### 5. 進度追蹤
- 依照要求，已在專案根目錄建立 [PROGRESS.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/PROGRESS.md) 紀錄並打勾✅。

## 驗證計畫

> [!IMPORTANT]
> 遵照「嚴禁使用終端機自動執行測試」的規定，**請您手動執行以下指令來跑測試**：
> 
> ```bash
> mvn clean test -pl hub-app -Dtest=TaxRuleDefaultPropertiesTest
> ```

當您確認第一階段的架構設計符合需求後，我們即可進入後續的 Repository、Service、Controller 開發！


# 修正計畫：Fixed-62 階段一 (Domain 與 Properties) 補齊 Profile 欄位

這份計畫旨在回頭修正階段一的實作，為 `TaxRuleConfig` 加入與 `FinanceHubConfig` 一致的 Profile 支援（`name` 與 `enabled` 欄位），確保系統架構的一致性。

## Open Questions

無。

## Proposed Changes

### Domain 層 (實體修正)

#### [MODIFY] [TaxRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)
- 加入 `@UniqueConstraint(name = TaxRuleConfig.UNIQUE_NAME, columnNames = TaxRuleConfig.NAME_COLUMN)` 於 `@Table` 宣告。
- 新增 `public static final String NAME_COLUMN = "name";` 等常數。
- 新增 `@Column(name = "name", nullable = false)` `String name`。
- 新增 `@Column(name = "enabled", nullable = false)` `Boolean enabled`。
- 修改 `@EqualsAndHashCode(of = { "name", "enabled" }, callSuper = false)`。
- 修改 Constructor，將 `name` 與 `enabled` 納入初始化參數。

### App 層 (Properties 配置與測試修正)

#### [MODIFY] [TaxRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultProperties.java)
- 新增 `String name = "default";` 屬性（對應預設 Profile 名稱）。
- 新增 `Boolean enabled = true;` 屬性。

#### [MODIFY] [TaxRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultPropertiesTest.java)
- 補上針對 `name` (預期為 "default") 以及 `enabled` (預期為 `true`) 的斷言（Assertions）。

### 配置檔 (YAML / Properties)

由於 `TaxRuleDefaultProperties` 已設定預設值為 `name=default` 與 `enabled=true`，因此 `application.properties` 與 `.env` 系列檔案**不強制**需要加上這兩個屬性，除非有想要覆寫。這裡將保持不變，或視需要僅在測試配置中補上。

---

## Verification Plan

### Automated Tests
- `mvn clean test -pl hub-app -Dtest=TaxRuleDefaultPropertiesTest`

### Manual Verification
- 檢視 `TaxRuleConfig.java` 確保加入了 `name` 與 `enabled` 以及資料庫唯一約束。


# Walkthrough: Fixed-62 Phase 1 Fix (Profile Support)

我已完成 Phase 1 的架構修正，確保 `TaxRuleConfig` 支援與 `FinanceHubConfig` 一致的 Profile 機制。

## 完成項目

### 1. 修正 Domain Entities
- [TaxRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)：新增了 `name` 與 `enabled` 欄位，並加上 `@UniqueConstraint` 以確保設定檔名稱唯一。Constructor 已同步更新。

### 2. 修正 Properties 綁定與測試
- [TaxRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultProperties.java)：加入了 `name = "default"` 與 `enabled = true` 的預設值，確保系統啟動時能自動載入一組預設的稅制。
- [TaxRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/TaxRuleDefaultPropertiesTest.java)：已補上針對 `name` 與 `enabled` 的 Assertions。

## 驗證計畫

> [!IMPORTANT]
> 遵照「嚴禁使用終端機自動執行測試」的規定，**請您手動執行以下指令來跑測試**，確認修改無誤：
> 
> ```bash
> mvn clean test -pl hub-app -Dtest=TaxRuleDefaultPropertiesTest
> ```
