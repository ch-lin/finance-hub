# Fixed 82-02 階段一 (Domain 與 Properties 層) 缺漏欄位擴充實作計畫

這個計畫旨在實作我們稍早討論的「想法一」，將 `PensionCalculator` 中剩下的固定常數抽離至 `PensionRuleConfig` 與對應的 Properties 中，包含厚生年金乘數千分率、月薪與獎金上限等。

## User Review Required

> [!IMPORTANT]
> 請確認這些欄位的資料型態與定義是否符合您的預期：
> - `maxBasicPensionMonths` (Long): 原預設值 `480`
> - `kouseiMultiplierPerMille` (BigDecimal): 原預設值 `5.481` (千分率，非小數)
> - `kouseiSalaryCapMonthly` (Long): 原預設值 `650000`
> - `kouseiBonusCapPerPayment` (Long): 原預設值 `1500000`
>
> 點擊 Proceed 後，我將開始實作。

## Proposed Changes

### Domain 層 (hub-domain)

#### [MODIFY] [PensionRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/pension/PensionRuleConfig.java)
- 新增欄位與 `@Column` 標註：
  - `Long maxBasicPensionMonths` (nullable = false)
  - `java.math.BigDecimal kouseiMultiplierPerMille` (nullable = false, precision = 6, scale = 3)
  - `Long kouseiSalaryCapMonthly` (nullable = false)
  - `Long kouseiBonusCapPerPayment` (nullable = false)
- 更新 Constructor，將這四個新欄位加入建構子參數並指派。

---

### App 層 (hub-app)

#### [MODIFY] [PensionRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultProperties.java)
- 新增對應的四個屬性：
  - `private Long maxBasicPensionMonths;`
  - `private java.math.BigDecimal kouseiMultiplierPerMille;`
  - `private Long kouseiSalaryCapMonthly;`
  - `private Long kouseiBonusCapPerPayment;`

#### [MODIFY] [PensionRuleConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleConfigFactory.java)
- 更新 `create` 方法，將 Properties 取出的四個新值一併傳入 `PensionRuleConfig` 建構子中。

#### [MODIFY] [PensionRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/pension/PensionRuleDefaultPropertiesTest.java)
- 更新 `ApplicationContextRunner` 的 `withPropertyValues`，加入新的測試屬性（如 480, 5.481, 650000, 1500000）。
- 更新 AssertJ 斷言，確認這四個屬性都有正確對應注入到 `PensionRuleDefaultProperties` 中。

---

### 設定檔 (Properties & Env)

#### [MODIFY] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)
- 新增預設設定：
  - `finance-hub.pension-rule.max-basic-pension-months=480`
  - `finance-hub.pension-rule.kousei-multiplier-per-mille=5.481`
  - `finance-hub.pension-rule.kousei-salary-cap-monthly=650000`
  - `finance-hub.pension-rule.kousei-bonus-cap-per-payment=1500000`

#### [MODIFY] 各種 .env 檔案
包含 `.env`, `.env.base`, `.env.dev`, `.env.prod`, `.env.example`, `ds720.env` 等環境變數配置檔。
- 新增變數設定，對應 Spring Boot 的寬鬆綁定（Relaxed Binding）：
  - `FINANCE_HUB_PENSION_RULE_MAX_BASIC_PENSION_MONTHS=480`
  - `FINANCE_HUB_PENSION_RULE_KOUSEI_MULTIPLIER_PER_MILLE=5.481`
  - `FINANCE_HUB_PENSION_RULE_KOUSEI_SALARY_CAP_MONTHLY=650000`
  - `FINANCE_HUB_PENSION_RULE_KOUSEI_BONUS_CAP_PER_PAYMENT=1500000`

## Verification Plan

### Manual Verification
- 請您手動執行 `mvn test -Dtest=PensionRuleDefaultPropertiesTest`，確認 Properties 能正確從 Context 載入這些新屬性。
- 請您手動執行完整的 `mvn clean test` 確保編譯與既有測試不受影響。

# Fixed 82-02 階段一 (Domain 與 Properties 層) 缺漏欄位擴充完成

我已經完成了「將 PensionCalculator 中剩下的固定常數抽離至 PensionRuleConfig 與對應 Properties 中」的階段一實作。

## 變更摘要

1. **`hub-domain`** 
   - 擴充了 `PensionRuleConfig`，加入以下四個新欄位：
     - `maxBasicPensionMonths` (最大基礎年金月數，預設 480)
     - `kouseiMultiplierPerMille` (厚生年金乘數千分率，預設 5.481)
     - `kouseiSalaryCapMonthly` (厚生年金月薪上限，預設 650000)
     - `kouseiBonusCapPerPayment` (厚生年金單次獎金上限，預設 1500000)
   
2. **`hub-app`**
   - 擴充了 `PensionRuleDefaultProperties`，以支援這些新屬性的注入。
   - 更新了 `PensionRuleConfigFactory`，將新讀取到的 properties 初始化進 `PensionRuleConfig`。
   - 更新了 `PensionRuleDefaultPropertiesTest`，新增這四個欄位的測試斷言。

3. **配置檔**
   - 於 `application.properties` 補上了預設值 (`finance-hub.pension-rule.*`)。
   - 於 `.env.base`, `.env.dev`, `.env.prod` 加入了對應環境變數 (`FINANCE_HUB_PENSION_RULE_*`)。

## 驗證計畫

根據您的指示，我已將原先的自動執行指令移除。請您透過您的環境執行以下手動驗證：

1. 執行單元測試：
   ```bash
   mvn test -Dtest=PensionRuleDefaultPropertiesTest
   ```
2. （選用）執行完整測試：
   ```bash
   mvn clean test
   ```

若單元測試皆能通過，即代表 Phase 1 擴充完成，接下來我們就可以開始著手 Phase 2 (Service 與 Controller 層) 的更新。
