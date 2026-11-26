# [FIXED] Issue 138：基礎控除改成動態策略查表後，`TaxRuleConfig`裡原本的固定數字欄位變成「幽靈設定」——使用者在系統設定畫面改這兩個數字，存檔會成功，但完全不會影響任何模擬計算結果

## 這份issue怎麼被發現的
這是review [question-118](question-118.md)（基礎控除改成依所得分級的策略模式）實作完成度時發現的問題。核心的策略模式本身（依所得分級動態查表）已經正確實作，數字也跟官方校正後的級距表核對一致，這部分沒有問題。這份issue記錄的是這次修法方式帶來的一個副作用。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:102-106,354-357`（薪資所得、退休年金兩個情境，確認都已經改成呼叫`basicDeductionStrategyFactory.getIncomeTaxStrategy(...)`/`getInhabitantTaxStrategy(...)`動態查表，完全不再讀取`context.getTaxConfig().getBasicDeductionIncomeTax()`或`.getBasicDeductionInhabitantTax()`）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java:39-43`（`basicDeductionIncomeTax`、`basicDeductionInhabitantTax`這兩個欄位，資料庫裡依然是`nullable = false`的必填欄位，沒有被標記為棄用或移除）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java:119-120,158-159`（create/update這兩個端點，依然完整接收並處理這兩個欄位的值）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java:51-55`（`@NotNull`必填驗證，依然要求前端送出這兩個值才能通過驗證）
- `hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx:55-56,137-167`（前端系統設定畫面，依然顯示「Basic Deduction (Income Tax)」「Basic Deduction (Resident Tax)」兩個標示清楚、可自由編輯的數字輸入框，表單預設值480000/430000，畫面上沒有任何提示說明這兩個欄位已經不會影響計算結果）

## 現狀
基礎控除的計算方式，已經從「讀取資料庫裡系統管理員設定的單一固定數字」，改成「依照模擬年份跟使用者所得，透過策略工廠動態查表」——這個新機制完全封裝在`hub-app`內部的純計算層，沒有改動資料庫欄位、沒有改動API的Request/Response格式、也沒有改動前端畫面。

這代表：資料庫裡的`basic_deduction_income_tax`、`basic_deduction_inhabitant_tax`這兩個欄位，前端系統設定畫面上對應的這兩個輸入框，整條CRUD鏈路（Controller接收、DTO驗證、Service儲存）都還活著、都還能正常運作——但`ActiveTaxCalculationEngine`已經完全不會去讀這兩個值了。

## 為何是問題
系統管理員（也就是使用者自己）如果打開系統設定畫面，把「Basic Deduction (Income Tax)」這個數字從480000改成任何其他數字，存檔會成功、資料庫也會真的被更新，畫面上看起來這個設定生效了——但實際上，不管改成多少，模擬計算出來的結果完全不會有任何變化，因為計算引擎已經改用策略表裡寫死的分級數字，不會再去看這個被改掉的設定值。

這個落差目前使用者本人大概不會踩到（因為使用者剛好是這次改動的決策者，知道這個機制已經換掉），但如果忘記這件事、或者之後有協作者接手維護，看到畫面上這個設定欄位還在、改了存檔也成功，卻發現模擬結果完全沒變化，會以為是別的地方出了bug，而不會想到「這個設定本身已經被架空、不再生效」這個真正的原因。

## 跟Question-117的類似落差
這跟review question-117時發現的另一個問題性質類似（E-Trade股票庫存重構後，舊的`etrade_stock_lot`資料表變成沒人讀寫的孤兒表）——用新機制取代舊機制時，舊的資料路徑或UI介面，沒有被同步標記廢棄或清除，導致系統裡留著一個「看起來還在運作、但實際上已經失效」的介面。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：把這兩個欄位、對應的API欄位、前端輸入框整個移除**
- 優點：徹底清除失效的介面，不會再有人被誤導。
- 缺點：如果資料庫裡已經有舊資料存過這兩個欄位的值，移除欄位本身是不可逆的，需要先確認移除的時機跟方式。

**方向B：保留欄位，但在前端畫面上明確加提示（例如「此設定已被動態稅制策略取代，不再生效」）**
- 優點：改動小，不涉及資料庫欄位或API格式異動。
- 缺點：系統裡依然留著一個形式上存在、但功能上死掉的欄位，資料庫跟API層面的「幽靈狀態」沒有被根本解決。

**方向C：維持現狀，不處理**
- 理由：使用者目前是這個系統唯一的操作者，知道這個機制已經換掉，實際踩雷機率低；如果之後真的有協作者或要上GitHub公開，再回頭處理也不遲。

---

## 修正報告（2026/10/09 已修復完成）

### 1. 修改摘要
- **採行方向**：方向 A（徹底移除這兩個欄位、對應的 API 欄位、設定檔、環境變數及前端輸入框）。
- **修改檔案與位置**：
  1. `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java`
     - 移除 `@Column(name = "basic_deduction_income_tax")` 與 `@Column(name = "basic_deduction_inhabitant_tax")` 欄位映射、建構子參數與 getter。
  2. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java`
     - 移除 `basicDeductionIncomeTax` 與 `basicDeductionInhabitantTax` 屬性宣告。
  3. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java`
     - 移除建立預設 `TaxRuleConfig` 實體時傳遞的 properties 純量參數。
  4. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java` & `UpdateTaxRuleConfigCommand.java`
     - 移除 Command 上的 `basicDeductionIncomeTax` 與 `basicDeductionInhabitantTax` 欄位。
  5. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java`
     - 移除 Result 欄位宣告與 `fromEntity` 中的 builder 賦值。
  6. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java`
     - 移除建立與更新實體時對這兩項純量欄位的賦值邏輯。
  7. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java`
     - 移除傳遞給模擬引擎的核心 DTO 上的 `basicDeductionIncomeTax` 與 `basicDeductionInhabitantTax` 純量欄位。
  8. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java`
     - 組裝 `TaxConfigDto` 時，移除傳遞的這兩項純量參數。
  9. `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java`
     - 移除帶有 `@NotNull` 驗證的 `basicDeductionIncomeTax` 與 `basicDeductionInhabitantTax` 欄位。
  10. `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java` & `TaxRuleConfigResponse.java`
      - 移除更新 Request 與 API Response 物件上的這兩項欄位。
  11. `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java`
      - 移除 Controller 中組合 Command 時向 builder 賦值的呼叫。
  12. 設定檔與環境變數：
      - `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties` & `application-h2.properties`：移除 `app.tax-rule.default.basic-deduction-income-tax` 與 `basic-deduction-inhabitant-tax`。
      - `finance-hub/finance-hub-backend/hub/src/main/resources/.env.base`、`.env.dev`、`.env.prod`：移除 `TAX_RULE_BASIC_DEDUCTION_INCOME` 與 `TAX_RULE_BASIC_DEDUCTION_INHABITANT`。
  13. 前端 UI（`hub-ui`）：
      - `hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts`：移除 Zod 驗證 schema 與新建 profile defaultValues 中的對應欄位。
      - `hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx`：移除表單 defaultValues 及「Scalar Constants」區塊中的兩個輸入框。
  14. 單元測試校準：
      - `SimulationContextFactoryTest.java`：移除 mock 賦值與斷言。
      - `TaxRuleDefaultPropertiesTest.java`：移除測試屬性與斷言。
      - `TaxRuleConfigServiceImplTest.java`：修正 Entity 建構子調用與 Command builder。
      - `TaxRuleConfigControllerTest.java`：修正 Request 與 Result builder。
      - `ActiveTaxCalculationEngineTest.java`：15 處 `TaxConfigDto` 建構子調用同步更新為 3 參數。
      - `AssetSimulationEngineImplTest.java`：移除 Mock `TaxRuleConfigResult.builder()` 中的過時呼叫。

### 2. 修復效果
1. **徹底消除幽靈設定**：計算引擎改由動態策略查表後，將失效的純量常數從資料庫、API、設定檔及前端畫面全鏈路拔除，避免使用者改了數字卻完全不生效的認知陷阱與維護混淆。
2. **前後端契約與架構一致**：API Request 不再強制要求傳入無意義的常數，前端表單與 Zod schema 保持純淨，落實 Clean Architecture 與單一真實來源（SSOT）。
3. **零迴歸與代碼純淨**：全專案單元測試建構參數同步調整，計算引擎內部維持以動態策略查表計算，無殘留任何懸空依賴或死代碼。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main  INSERT  git --no-pager diff                                                                10001  12.76G   3.74    12:52:04  
diff --git a/.env.base b/.env.base
index 9d908c0c..d981b507 100644
--- a/.env.base
+++ b/.env.base
@@ -72,9 +72,6 @@ FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
-
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
diff --git a/.env.dev b/.env.dev
index ddc0f403..e09d9e4f 100644
--- a/.env.dev
+++ b/.env.dev
@@ -71,9 +71,6 @@ FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
-
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
diff --git a/.env.prod b/.env.prod
index 2b3431a4..bde5d303 100644
--- a/.env.prod
+++ b/.env.prod
@@ -73,9 +73,6 @@ FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
-FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
-
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java
index 05d18e23..81ce67b7 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java
@@ -45,9 +45,7 @@ public class TaxRuleConfigFactory {
     public TaxRuleConfig create(TaxRuleDefaultProperties properties) {
         TaxRuleConfig config = new TaxRuleConfig(
                 properties.getName(),
-                properties.getEnabled(),
-                properties.getBasicDeductionIncomeTax(),
-                properties.getBasicDeductionInhabitantTax());
+                properties.getEnabled());
 
         if (properties.getBrackets() != null) {
             properties.getBrackets().forEach((type, list) -> {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java
index 9d3c47c1..c985308e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java
@@ -19,9 +19,6 @@ public class TaxRuleDefaultProperties {
     private String name = "default";
     private Boolean enabled = true;
 
-    private Long basicDeductionIncomeTax;
-    private Long basicDeductionInhabitantTax;
-
     private Map<TaxBracketType, List<TaxBracketProperty>> brackets = new HashMap<>();
 
     @Data
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java
index 2f84243a..4b38a156 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java
@@ -127,9 +127,7 @@ public class TaxRuleConfigServiceImpl implements TaxRuleConfigService {
 
         TaxRuleConfig newConfig = new TaxRuleConfig(
                 configName,
-                command.getEnabled(),
-                command.getBasicDeductionIncomeTax(),
-                command.getBasicDeductionInhabitantTax());
+                command.getEnabled());
 
         if (command.getBrackets() != null) {
             command.getBrackets().forEach(b -> {
@@ -160,12 +158,6 @@ public class TaxRuleConfigServiceImpl implements TaxRuleConfigService {
         if (command.getEnabled() != null) {
             config.setEnabled(command.getEnabled());
         }
-        if (command.getBasicDeductionIncomeTax() != null) {
-            config.setBasicDeductionIncomeTax(command.getBasicDeductionIncomeTax());
-        }
-        if (command.getBasicDeductionInhabitantTax() != null) {
-            config.setBasicDeductionInhabitantTax(command.getBasicDeductionInhabitantTax());
-        }
 
         if (command.getBrackets() != null) {
             config.clearTaxBrackets();
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java
index e2eebcef..89bd8866 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java
@@ -36,7 +36,5 @@ import lombok.Value;
 public class CreateTaxRuleConfigCommand {
     String name;
     Boolean enabled;
-    Long basicDeductionIncomeTax;
-    Long basicDeductionInhabitantTax;
     List<TaxBracketCommand> brackets;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java
index b304ca4b..7600661b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java
@@ -35,7 +35,5 @@ import lombok.Value;
 @Builder
 public class UpdateTaxRuleConfigCommand {
     Boolean enabled;
-    Long basicDeductionIncomeTax;
-    Long basicDeductionInhabitantTax;
     List<TaxBracketCommand> brackets;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java
index 2b3f4780..a059a894 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java
@@ -38,8 +38,6 @@ import lombok.Value;
 public class TaxRuleConfigResult {
     String name;
     Boolean enabled;
-    Long basicDeductionIncomeTax;
-    Long basicDeductionInhabitantTax;
     List<TaxBracketResult> brackets;
 
     public static TaxRuleConfigResult fromEntity(TaxRuleConfig config) {
@@ -59,8 +57,6 @@ public class TaxRuleConfigResult {
         return TaxRuleConfigResult.builder()
                 .name(config.getName())
                 .enabled(config.getEnabled())
-                .basicDeductionIncomeTax(config.getBasicDeductionIncomeTax())
-                .basicDeductionInhabitantTax(config.getBasicDeductionInhabitantTax())
                 .brackets(bracketResults)
                 .build();
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
index e45b541b..e450b81b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
@@ -134,8 +134,6 @@ public class SimulationContextFactory {
                 .collect(Collectors.toList());
 
         TaxConfigDto taxConfigDto = new TaxConfigDto(
-                taxConfigResult.getBasicDeductionIncomeTax(),
-                taxConfigResult.getBasicDeductionInhabitantTax(),
                 employmentBrackets,
                 incomeTaxBrackets,
                 inhabitantTaxBrackets);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java
index bb18aea3..6ca96bcd 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java
@@ -32,9 +32,6 @@ import lombok.Value;
  */
 @Value
 public class TaxConfigDto {
-    Long basicDeductionIncomeTax;
-    Long basicDeductionInhabitantTax;
-
     List<TaxBracketDto> employmentIncomeDeductionBrackets;
     List<TaxBracketDto> incomeTaxRateBrackets;
     List<TaxBracketDto> inhabitantTaxRateBrackets;
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java
index 90689129..12e7eeec 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java
@@ -13,8 +13,6 @@ import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
 
 @SpringBootTest(classes = { TaxRuleDefaultPropertiesTest.TestConfig.class })
 @TestPropertySource(properties = {
-        "finance-hub.tax-rule.basic-deduction-income-tax=480000",
-        "finance-hub.tax-rule.basic-deduction-inhabitant-tax=430000",
         "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=1625000",
         "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0",
         "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=550000"
@@ -32,8 +30,6 @@ public class TaxRuleDefaultPropertiesTest {
     void shouldLoadDefaultProperties() {
         assertThat(properties.getName()).isEqualTo("default");
         assertThat(properties.getEnabled()).isTrue();
-        assertThat(properties.getBasicDeductionIncomeTax()).isEqualTo(480000L);
-        assertThat(properties.getBasicDeductionInhabitantTax()).isEqualTo(430000L);
 
         assertThat(properties.getBrackets()).containsKey(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION);
         assertThat(properties.getBrackets().get(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION)).hasSize(1);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java
index 7e346f02..55fc685d 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java
@@ -70,7 +70,7 @@ class TaxRuleConfigServiceImplTest {
         when(repository.count()).thenReturn(0L);
         when(defaultProperties.getName()).thenReturn("default");
 
-        TaxRuleConfig config = new TaxRuleConfig("default", true, 480000L, 430000L);
+        TaxRuleConfig config = new TaxRuleConfig("default", true);
         when(factory.create(defaultProperties)).thenReturn(config);
 
         when(repository.findAll()).thenReturn(List.of(config));
@@ -88,7 +88,7 @@ class TaxRuleConfigServiceImplTest {
         when(repository.count()).thenReturn(1L);
         when(defaultProperties.getName()).thenReturn("default");
 
-        TaxRuleConfig config = new TaxRuleConfig("default", true, 480000L, 430000L);
+        TaxRuleConfig config = new TaxRuleConfig("default", true);
         when(repository.findAll()).thenReturn(List.of(config));
         when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));
 
@@ -114,8 +114,6 @@ class TaxRuleConfigServiceImplTest {
         CreateTaxRuleConfigCommand cmd = CreateTaxRuleConfigCommand.builder()
                 .name("default")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .build();
 
         InvalidRequestException exception = assertThrows(InvalidRequestException.class,
@@ -129,13 +127,11 @@ class TaxRuleConfigServiceImplTest {
     void testCreateConfig_AlreadyExists() {
         when(defaultProperties.getName()).thenReturn("default");
         when(repository.findByName("custom"))
-                .thenReturn(Optional.of(new TaxRuleConfig("custom", true, 480000L, 430000L)));
+                .thenReturn(Optional.of(new TaxRuleConfig("custom", true)));
 
         CreateTaxRuleConfigCommand cmd = CreateTaxRuleConfigCommand.builder()
                 .name("custom")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .build();
 
         InvalidRequestException exception = assertThrows(InvalidRequestException.class,
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index e0e86107..e45dab8e 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -263,8 +263,6 @@ class AssetSimulationEngineImplTest {
         TaxRuleConfigResult taxResult = TaxRuleConfigResult.builder()
                 .name("default-tax")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .brackets(Collections.emptyList())
                 .build();
         lenient().when(taxRuleConfigService.getConfig("default-tax")).thenReturn(taxResult);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
index d41c2849..570751bc 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
@@ -147,8 +147,6 @@ class SimulationContextFactoryTest {
         TaxRuleConfigResult taxConfigResult = TaxRuleConfigResult.builder()
                 .name("default-tax")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .brackets(List.of(b1, b2))
                 .build();
         when(taxRuleConfigService.getConfig("default-tax")).thenReturn(taxConfigResult);
@@ -171,8 +169,6 @@ class SimulationContextFactoryTest {
 
         // Assert
         assertThat(context.getTaxConfig()).isNotNull();
-        assertThat(context.getTaxConfig().getBasicDeductionIncomeTax()).isEqualTo(480000L);
-        assertThat(context.getTaxConfig().getBasicDeductionInhabitantTax()).isEqualTo(430000L);
 
         assertThat(context.getTaxConfig().getEmploymentIncomeDeductionBrackets()).hasSize(1);
         assertThat(context.getTaxConfig().getEmploymentIncomeDeductionBrackets().get(0).getMaxSalary())
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index 5af51c2b..077850cb 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -54,7 +54,7 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -102,7 +102,7 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -141,7 +141,7 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -178,7 +178,7 @@ class ActiveTaxCalculationEngineTest {
         long pensionIncome = 3_000_000L;
         int age = 65;
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.05"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -218,7 +218,7 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 26_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -263,7 +263,7 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 27_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -307,7 +307,7 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 20_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -348,7 +348,7 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 20_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -388,7 +388,7 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 30_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -428,7 +428,7 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 30_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -467,7 +467,7 @@ class ActiveTaxCalculationEngineTest {
         long mortgageDeduction = 200_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -517,7 +517,7 @@ class ActiveTaxCalculationEngineTest {
 
     @Test
     void calculateRetirementTax_incomeBelowDeduction_returnsZeroTax() {
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.emptyList(),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
@@ -539,7 +539,7 @@ class ActiveTaxCalculationEngineTest {
         // Short-term strategy applies half taxation: taxableIncome = 1,000,000 JPY
         // Income tax rate: 5% (offset 0) -> base = 50,000 JPY -> with 2.1% surtax = 51,050 JPY
         // Inhabitant tax rate: 10% -> 100,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.emptyList(),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.05"), 0L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
@@ -563,7 +563,7 @@ class ActiveTaxCalculationEngineTest {
         // baseIncomeTax = 7,300,000 * 0.23 - 636,000 = 1,043,000 JPY
         // incomeTax = 1,043,000 + (long)(1,043,000 * 0.021) = 1,064,903 JPY
         // Inhabitant tax: 7,300,000 * 0.10 = 730,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.emptyList(),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.23"), -636_000L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
@@ -586,7 +586,7 @@ class ActiveTaxCalculationEngineTest {
         // baseIncomeTax = 4,400,000 * 0.20 - 427,500 = 452,500 JPY
         // incomeTax = 452,500 + (long)(452,500 * 0.021) = 462,002 JPY
         // Inhabitant tax: 4,400,000 * 0.10 = 440,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L,
+        TaxConfigDto taxConfig = new TaxConfigDto(
                 Collections.emptyList(),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.20"), -427_500L)),
                 Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java
index 55477b21..38f939f1 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java
@@ -36,21 +36,13 @@ public class TaxRuleConfig extends AuditableEntity {
     @Column(name = "enabled", nullable = false)
     private Boolean enabled;
 
-    @Column(name = "basic_deduction_income_tax", nullable = false)
-    private Long basicDeductionIncomeTax;
-
-    @Column(name = "basic_deduction_inhabitant_tax", nullable = false)
-    private Long basicDeductionInhabitantTax;
-
     @OneToMany(mappedBy = "taxRuleConfig", cascade = CascadeType.ALL, orphanRemoval = true)
     @jakarta.persistence.OrderBy("maxSalary ASC")
     private final List<TaxBracket> taxBrackets = new ArrayList<>();
 
-    public TaxRuleConfig(String name, Boolean enabled, Long basicDeductionIncomeTax,Long basicDeductionInhabitantTax) {
+    public TaxRuleConfig(String name, Boolean enabled) {
         this.name = name;
         this.enabled = enabled;
-        this.basicDeductionIncomeTax = basicDeductionIncomeTax;
-        this.basicDeductionInhabitantTax = basicDeductionInhabitantTax;
     }
 
     public void addTaxBracket(TaxBracket bracket) {
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java
index d546d0cf..40c1cee7 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java
@@ -116,8 +116,6 @@ public class TaxRuleConfigController {
         CreateTaxRuleConfigCommand command = CreateTaxRuleConfigCommand.builder()
                 .name(request.getName())
                 .enabled(Boolean.TRUE.equals(request.getEnabled()))
-                .basicDeductionIncomeTax(request.getBasicDeductionIncomeTax())
-                .basicDeductionInhabitantTax(request.getBasicDeductionInhabitantTax())
                 .brackets(bracketCommands)
                 .build();
 
@@ -155,8 +153,6 @@ public class TaxRuleConfigController {
 
         UpdateTaxRuleConfigCommand command = UpdateTaxRuleConfigCommand.builder()
                 .enabled(request.getEnabled())
-                .basicDeductionIncomeTax(request.getBasicDeductionIncomeTax())
-                .basicDeductionInhabitantTax(request.getBasicDeductionInhabitantTax())
                 .brackets(bracketCommands)
                 .build();
 
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java
index 8785b51b..02d5bfa6 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java
@@ -48,12 +48,6 @@ public class CreateTaxRuleConfigRequest {
     @NotNull
     private Boolean enabled;
 
-    @NotNull
-    private Long basicDeductionIncomeTax;
-
-    @NotNull
-    private Long basicDeductionInhabitantTax;
-
     @Valid
     private List<TaxBracketRequest> brackets;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java
index 8f0c5071..2596261a 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java
@@ -36,7 +36,5 @@ import lombok.Data;
 public class TaxRuleConfigResponse {
     private String name;
     private Boolean enabled;
-    private Long basicDeductionIncomeTax;
-    private Long basicDeductionInhabitantTax;
     private List<TaxBracketResponse> brackets;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java
index 4dc629ff..01edd8e1 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java
@@ -41,8 +41,6 @@ import lombok.NoArgsConstructor;
 public class UpdateTaxRuleConfigRequest {
 
     private Boolean enabled;
-    private Long basicDeductionIncomeTax;
-    private Long basicDeductionInhabitantTax;
 
     @Valid
     private List<TaxBracketRequest> brackets;
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index 06fb4412..76f0ea38 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -113,8 +113,6 @@ finance.hub.system-config.ga-elite-ratio=10
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
-finance-hub.tax-rule.basic-deduction-income-tax=1040000
-finance-hub.tax-rule.basic-deduction-inhabitant-tax=430000
 
 # Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 0b480fb5..fd75ecbd 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -107,8 +107,6 @@ finance.hub.system-config.simulation-early-repayment-month=12
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
-finance-hub.tax-rule.basic-deduction-income-tax=1040000
-finance-hub.tax-rule.basic-deduction-inhabitant-tax=430000
 
 # Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java
index 2041b59b..775f2ba9 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java
@@ -100,15 +100,11 @@ class TaxRuleConfigControllerTest {
         CreateTaxRuleConfigRequest request = CreateTaxRuleConfigRequest.builder()
                 .name("custom")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .build();
 
         TaxRuleConfigResult result = TaxRuleConfigResult.builder()
                 .name("custom")
                 .enabled(true)
-                .basicDeductionIncomeTax(480000L)
-                .basicDeductionInhabitantTax(430000L)
                 .build();
 
         when(configService.createConfig(any(CreateTaxRuleConfigCommand.class))).thenReturn(result);
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                   10002  13.11G   5.04    14:59:14  


  edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  cd ../../hub-ui                                                                  10002  13.11G   5.04    14:59:14  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                  10003  13.21G   4.72    15:00:37  
diff --git a/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx b/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx
index 2dc2cca..efd91b9 100644
--- a/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx
+++ b/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx
@@ -52,8 +52,6 @@ export function TaxRuleConfigPanel({ configsHook }: Props) {
   const { register, handleSubmit, control, formState: { errors }, reset } = useForm<TaxRuleConfigFormValues>({
     resolver: zodResolver(taxRuleConfigSchema) as Resolver<TaxRuleConfigFormValues>,
     defaultValues: {
-      basicDeductionIncomeTax: 480000,
-      basicDeductionInhabitantTax: 430000,
       brackets: []
     },
   });
@@ -129,43 +127,6 @@ export function TaxRuleConfigPanel({ configsHook }: Props) {
               <Label htmlFor="tax-enabled">Enabled</Label>
             </div>
 
-            <div className="md:col-span-2 mt-4">
-              <h3 className="text-lg font-medium border-b pb-2">Scalar Constants (JPY)</h3>
-            </div>
-
-            <div className="space-y-2">
-              <Label htmlFor="basicDeductionIncomeTax">Basic Deduction (Income Tax)</Label>
-              <Controller
-                control={control}
-                name="basicDeductionIncomeTax"
-                render={({ field }) => (
-                  <ThousandSeparatorInput
-                    id="basicDeductionIncomeTax"
-                    value={field.value}
-                    onChange={field.onChange}
-                    placeholder="e.g. 480000"
-                  />
-                )}
-              />
-              {errors.basicDeductionIncomeTax && <p className="text-xs text-destructive mt-1">{errors.basicDeductionIncomeTax.message}</p>}
-            </div>
-
-            <div className="space-y-2">
-              <Label htmlFor="basicDeductionInhabitantTax">Basic Deduction (Resident Tax)</Label>
-              <Controller
-                control={control}
-                name="basicDeductionInhabitantTax"
-                render={({ field }) => (
-                  <ThousandSeparatorInput
-                    id="basicDeductionInhabitantTax"
-                    value={field.value}
-                    onChange={field.onChange}
-                    placeholder="e.g. 430000"
-                  />
-                )}
-              />
-              {errors.basicDeductionInhabitantTax && <p className="text-xs text-destructive mt-1">{errors.basicDeductionInhabitantTax.message}</p>}
-            </div>
 
             <div className="md:col-span-2 mt-6">
               <h3 className="text-lg font-medium border-b pb-2 mb-4">Tax Brackets</h3>
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
index 0da8bbe..1f49e87 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
@@ -12,8 +12,6 @@ export const taxBracketSchema = z.object({
 export const taxRuleConfigSchema = z.object({
   name: z.string().min(1, "Name is required"),
   enabled: z.boolean().default(false),
-  basicDeductionIncomeTax: z.number().min(0).default(480000),
-  basicDeductionInhabitantTax: z.number().min(0).default(430000),
   brackets: z.array(taxBracketSchema).default([]),
 });
 
@@ -105,8 +103,6 @@ export function useTaxRuleConfigs() {
     setTaxConfigDetails({
       name: "",
       enabled: false,
-      basicDeductionIncomeTax: 480000,
-      basicDeductionInhabitantTax: 430000,
       brackets: []
     });
   };
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                       10004  13.21G   4.66    15:00:39  