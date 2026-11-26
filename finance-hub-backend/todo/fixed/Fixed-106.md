# [Fixed] Issue 106：Sandbox設定的提前還款，系統會自己決定扣在幾月，而且這個月份是借用自房貸契約上的獎金還款月，有時候還會因為資料順序不穩定而每次不一樣

## 這是一個需要你決定方向的問題，不是單純的bug修正

## 背景
使用者在Sandbox或AI Optimizer裡設定提前還款時，只會填「哪一年」跟「還多少錢」，不會填月份——資料結構本身就只有這兩個欄位（`SimulationOverrides.MortgageEarlyRepaymentOverride`，`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/service/simulation/command/SimulationOverrides.java:90-94`，只有`year`、`amount`）。

但模擬引擎實際計算時，一定要知道「這一年的這筆錢，是哪個月扣掉的」，才能算出正確的利息。系統目前的做法是：直接去看這筆房貸契約上原本就簽好的獎金還款月（例如6月、12月那種，房貸契約常見的設計），抓第一筆月份來用（`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`的`getOverrideEarlyRepaymentAmount`，第116-118行）。如果這筆房貸完全沒簽獎金還款，就直接用12月。

## 為何是問題
1. 使用者在填這筆提前還款的時候，完全不知道系統會這樣決定月份，也沒有地方可以確認或修改。
2. 房貸契約上的獎金還款月份清單（`bonusPayments`），存的時候沒有加上排序規則（`finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageRecord.java:108-110`），資料庫不保證讀回來的順序跟存進去時一樣。如果這筆房貸同時簽了6月跟12月兩筆獎金還款，系統抓到的「第一筆」月份可能這次是6月、下次變成12月，連帶讓同一筆提前還款，算出來的利息也會跟著變。

## 具體錯誤情境
使用者的房貸契約有6月、12月兩筆獎金還款，在Sandbox設定「2030年提前還500萬」。這500萬實際上會在6月還是12月被扣掉，取決於資料庫這次讀回來的順序，使用者完全不知道、也沒有決定過。

## 需要你決定的方向
這裡列出三個可能的方向，供你選擇，不代為決定：

**方向A：提前還款一律固定在12月扣**
- 優點：邏輯最單純，不再需要去讀房貸契約上的任何月份資料，資料順序不穩定這個問題也會一併消失。
- 缺點：如果使用者模擬的情境是「年中拿到一筆錢馬上還掉」，系統會把它排到年底才扣，跟使用者想模擬的時間點有落差。

**方向B：維持現在「借用房貸自己的獎金月」這個做法，只修正讀取順序不穩定這個問題**
- 做法：把「抓第一筆」改成「明確找出最早的月份」（例如用`Collections.min`排序），不再依賴資料庫回傳的順序。
- 優點：改動最小。
- 缺點：使用者還是不知道、也不能決定提前還款扣在幾月，只是這次系統會穩定地選同一個月，不會每次都不一樣。

**方向C：讓使用者自己填月份**
- 做法：在`MortgageEarlyRepaymentOverride`裡加一個選填的月份欄位，使用者填了就用使用者的，沒填就預設12月。
- 優點：使用者自己決定，系統不用再猜或借用任何其他資料。
- 缺點：這會動到前端Sandbox的輸入畫面（`simulation-override-panel.tsx`，目前「Add Early Repayment」只有年份跟金額兩個輸入框，要多加一個月份選擇）、後端的資料格式跟驗證規則，工程量比A、B都大。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`（`getOverrideEarlyRepaymentAmount`，決定提前還款扣在哪個月的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:90-94`（`MortgageEarlyRepaymentOverride`，目前只有`year`、`amount`兩個欄位）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageRecord.java:108-110`（`bonusPayments`，房貸契約上的獎金還款月份清單，沒有排序規則）

---

## 最終決策與架構定案（Architecture Decision Record）

### 決策結論
**改由系統配置（Finance Hub Config）統一控制提前還款月份，預設值為 12 月。**  
- **不寫死（No Magic Number）**：不在 `MortgageCalculator` 內部寫死數字 12。
- **暫不開放使用者介面修改**：維持目前前端 Sandbox 簡潔直覺的 `(year, amount)` 輸入介面，不增加前端負擔與使用者認知成本。
- **徹底解耦**：完全拔除 `m.getBonusPayments().get(0)` 偷借契約月份的邏輯。

### 決策理由與好處
1. **防止隱性技術債（Magic Number 遺忘症）**：
   如果直接在計算器裡硬編碼 `12`，時間一久團隊將遺忘這個業務假設的存在。提升為系統設定值後，在配置檔與系統設定清單中清晰可見。
2. **為未來擴充預留彈性**：
   未來若想開放「讓使用者在設定頁自訂偏好還款月」或「個別模擬覆蓋自訂月」，底層資料流已經由 Config 串接好（`context.getConfig().getSimulationEarlyRepaymentMonth()`），擴充時只需在前端/API 加開欄位，底層引擎無需再次重構。
3. **消除不確定性與無辜耦合**：
   徹底解決 Hibernate `@ElementCollection` 集合無序導致 6 月/12 月隨機跳動的問題，也讓常態契約獎金還款（ボーナス払い）回歸原本單純的日常扣款職責。

### 相關異動檔案（全鏈路實作範圍）

本設定項（`simulationEarlyRepaymentMonth`）屬於系統全域設定，遵循系統既有的 `FinanceHubConfig` 完整生命週期架構，異動範圍分為以下四層：

#### 1. 核心計算層（Core Calculator）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`（`getOverrideEarlyRepaymentAmount`，改由 `context.getConfig()` 取得月份，徹底拔除對 `bonusPayments` 的讀取）

#### 2. 領域實體與預設配置（Domain & Default Properties）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java`（新增實體欄位、常數與 `@Min(1) @Max(12)` 驗證）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java`（`SystemConfig` 新增屬性）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java`（`create` 方法映射新屬性至實體）

#### 3. 管理員 REST API 全鏈路（Admin API & CQRS / DTO / Service）
*因為系統設定可由管理者透過 API 進行 CRUD 管理，需同步擴充請求、回應與指令物件：*
- **Controller & DTO**：
  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java`
  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java`
  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java`
  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigMapper.java`
- **Application Command & Service**：
  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java`
  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java`
  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java`
  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java`

#### 4. 部署環境變數與設定檔（Deployment & Environment Profiles）
- `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties`（新增 `finance.hub.system-config.simulation-early-repayment-month=12`）
- `finance-hub/.env.base`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
- `finance-hub/.env.dev`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
- `finance-hub/.env.prod`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
- `finance-hub/.env.example`（同步補齊範例環境變數）

#### 5. 單元與整合測試同步更新（Tests）
- `FinanceHubConfigTest.java`
- `FinanceHubDefaultPropertiesTest.java`
- `DefaultConfigFactoryTest.java`
- `FinanceHubConfigServiceImplTest.java`
- `FinanceHubConfigControllerTest.java`

### 實作要點指引
1. **設定項擴充與驗證**：
   - 欄位限制：月份必須為 1～12 之間的整數（`@Min(1) @Max(12)`）。
   - 預設值：全環境統一預設為 `12`。
2. **計算引擎改造（`MortgageCalculator.java`）**：
   - 改造 `getOverrideEarlyRepaymentAmount`（或傳入 `SimulationContext`）：
   ```java
   int logicalMonth = context.getConfig() != null && context.getConfig().getSimulationEarlyRepaymentMonth() != null
           ? context.getConfig().getSimulationEarlyRepaymentMonth()
           : 12;
   ```
   - 徹底刪除原先對 `m.getBonusPayments()` 的任何讀取。

---

## 實作進度追蹤（Implementation Progress）

- [x] **Phase 1: 領域實體與預設配置（Domain & Default Properties）**
  - [x] `FinanceHubConfig.java`（新增欄位、常數、getter/setter 與 `@Min(1) @Max(12)` 驗證）
  - [x] `FinanceHubDefaultProperties.java`（`SystemConfig` 新增屬性與預設值 12）
  - [x] `DefaultConfigFactory.java`（`create` 方法映射新屬性至實體）
- [x] **Phase 2: 部署環境變數與設定檔（Deployment & Environment Profiles）**
  - [x] `application.properties`（新增 `finance.hub.system-config.simulation-early-repayment-month=12`）
  - [x] `finance-hub/.env.base`
  - [x] `finance-hub/.env.dev`
  - [x] `finance-hub/.env.prod`
  - [x] `finance-hub/.env.example`
- [x] **Phase 3: 管理員 REST API 全鏈路（Admin API & CQRS / DTO / Service）**
  - [x] DTO: `CreateFinanceHubConfigRequest` / `UpdateFinanceHubConfigRequest` / `FinanceHubConfigResponse`
  - [x] Mapper: `FinanceHubConfigMapper`
  - [x] Command & Result: `CreateFinanceHubConfigCommand` / `UpdateFinanceHubConfigCommand` / `FinanceHubConfigResult`
  - [x] Service: `FinanceHubConfigServiceImpl`
- [x] **Phase 4: 核心計算層改造（Core Calculator）**
  - [x] `MortgageCalculator.java`（改自 `context.getConfig()` 讀取，徹底拔除 `bonusPayments`）
- [x] **Phase 5: 測試更新與回歸驗證（Tests & Verification）**
  - [x] `FinanceHubConfigTest`
  - [x] `FinanceHubDefaultPropertiesTest`
  - [x] `DefaultConfigFactoryTest`
  - [x] `FinanceHubConfigServiceImplTest`
  - [x] `FinanceHubConfigControllerTest`
- [x] **Phase 6: 前端管理介面適配（Frontend Admin UI）**
  - [x] `hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts`（`systemConfigSchema` 新增 `simulationEarlyRepaymentMonth` 驗證與預設值）
  - [x] `hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx`（`SystemConfigPanel` 新增 Simulation Settings 區塊與月份輸入框）

---

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10008  12.68G   7.71    17:23:52  
diff --git a/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx b/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
index 74b267a..d613bf1 100644
--- a/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
+++ b/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
@@ -51,6 +51,7 @@ export function SystemConfigPanel({ configsHook }: Props) {
     resolver: zodResolver(systemConfigSchema) as Resolver<SystemConfigFormValues>,
     defaultValues: {
       fileProcessingTimeoutSeconds: 120,
+      simulationEarlyRepaymentMonth: 12,
     },
   });
 
@@ -247,6 +248,22 @@ export function SystemConfigPanel({ configsHook }: Props) {
               {errors.gaEliteRatio && <p className="text-xs text-destructive mt-1">{errors.gaEliteRatio.message}</p>}
             </div>
 
+            <div className="md:col-span-2 mt-6 mb-2">
+              <h3 className="text-lg font-medium border-b pb-2">Simulation Settings</h3>
+            </div>
+
+            <div className="space-y-2 pt-2 md:col-span-1">
+              <Label htmlFor="simulationEarlyRepaymentMonth">Early Repayment Month (1-12)</Label>
+              <Input 
+                id="simulationEarlyRepaymentMonth" 
+                type="number" 
+                min={1}
+                max={12}
+                {...register('simulationEarlyRepaymentMonth')}
+                placeholder="e.g. 12"
+              />
+              {errors.simulationEarlyRepaymentMonth && <p className="text-xs text-destructive mt-1">{errors.simulationEarlyRepaymentMonth.message}</p>}
+            </div>
 
           </div>
         )}
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
index 0610367..bc60d9a 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
@@ -28,6 +28,7 @@ export const systemConfigSchema = z.object({
   gaMaxGenerations: z.coerce.number().min(1, "Must be >= 1").default(20),
   gaMutationRate: z.coerce.number().min(0, "Must be >= 0").max(1, "Must be <= 1").default(0.2),
   gaEliteRatio: z.coerce.number().min(1, "Must be >= 1").max(50, "Must be <= 50").default(10),
+  simulationEarlyRepaymentMonth: z.coerce.number().min(1, "Must be between 1 and 12").max(12, "Must be between 1 and 12").default(12),
 });
 
 export type SystemConfigFormValues = z.infer<typeof systemConfigSchema>;
@@ -322,6 +323,7 @@ export function useFinanceConfigs() {
       gaMaxGenerations: 20,
       gaMutationRate: 0.2,
       gaEliteRatio: 10,
+      simulationEarlyRepaymentMonth: 12,
     });
   };
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10009  12.84G   7.54    17:24:10  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10010  12.83G   7.41    17:24:16  
diff --git a/.env.base b/.env.base
index 2f142130..e94e804e 100644
--- a/.env.base
+++ b/.env.base
@@ -67,6 +67,7 @@ FINANCE_HUB_SYSTEM_CONFIG_PENSION_EXPECTED_MONTHLY_SALARY="500000"
 FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
+FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/.env.dev b/.env.dev
index 0154a4b2..c40cfd30 100644
--- a/.env.dev
+++ b/.env.dev
@@ -66,6 +66,7 @@ FINANCE_HUB_SYSTEM_CONFIG_PENSION_EXPECTED_MONTHLY_SALARY="500000"
 FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
+FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/.env.prod b/.env.prod
index dd1cbcbf..a38a6c01 100644
--- a/.env.prod
+++ b/.env.prod
@@ -68,6 +68,7 @@ FINANCE_HUB_SYSTEM_CONFIG_PENSION_EXPECTED_MONTHLY_SALARY="500000"
 FINANCE_HUB_SYSTEM_CONFIG_LIFE_EXPECTANCY="85"
 FINANCE_HUB_SYSTEM_CONFIG_EXPENSE_CALCULATION_PAST_MONTHS="12"
 FINANCE_HUB_SYSTEM_CONFIG_INFLATION_RATE="0.0"
+FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"
 
 # =============================================================================
 # Tax Rule Defaults
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
index 6d2ffb39..dd8ff7d4 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java
@@ -90,6 +90,7 @@ public class DefaultConfigFactory {
         config.setGaMaxGenerations(properties.getGaMaxGenerations());
         config.setGaMutationRate(properties.getGaMutationRate());
         config.setGaEliteRatio(properties.getGaEliteRatio());
+        config.setSimulationEarlyRepaymentMonth(properties.getSimulationEarlyRepaymentMonth());
 
         return config;
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
index 98106f64..da56afaa 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java
@@ -114,5 +114,8 @@ public class FinanceHubDefaultProperties {
         private Integer gaMaxGenerations = 20;
         private Double gaMutationRate = 0.2;
         private Integer gaEliteRatio = 10;
+
+        // Simulation
+        private Integer simulationEarlyRepaymentMonth = 12;
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
index 6640f7f1..373d0e1d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java
@@ -207,6 +207,9 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
         if (command.getGaEliteRatio() != null) {
             newConfig.setGaEliteRatio(command.getGaEliteRatio());
         }
+        if (command.getSimulationEarlyRepaymentMonth() != null) {
+            newConfig.setSimulationEarlyRepaymentMonth(command.getSimulationEarlyRepaymentMonth());
+        }
 
         FinanceHubConfig saved = repository.save(newConfig);
         eventPublisher.publishEvent(new ConfigUpdatedEvent(this));
@@ -303,6 +306,7 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
         command.getGaMaxGenerations().ifPresent(config::setGaMaxGenerations);
         command.getGaMutationRate().ifPresent(config::setGaMutationRate);
         command.getGaEliteRatio().ifPresent(config::setGaEliteRatio);
+        command.getSimulationEarlyRepaymentMonth().ifPresent(config::setSimulationEarlyRepaymentMonth);
 
         FinanceHubConfig savedConfig = repository.save(config);
 
@@ -383,6 +387,9 @@ public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {
             if (dbConfig.getGaEliteRatio() == null) {
                 dbConfig.setGaEliteRatio(defaultProps.getGaEliteRatio());
             }
+            if (dbConfig.getSimulationEarlyRepaymentMonth() == null) {
+                dbConfig.setSimulationEarlyRepaymentMonth(defaultProps.getSimulationEarlyRepaymentMonth());
+            }
             return dbConfig;
         }).orElseGet(this::findOrCreateDefaultConfig);
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
index 02dc06b9..431f4472 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java
@@ -105,4 +105,5 @@ public class CreateFinanceHubConfigCommand {
     Integer gaEliteRatio;
     Integer gaMaxGenerations;
     Double gaMutationRate;
+    Integer simulationEarlyRepaymentMonth;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
index c713eadd..d2619778 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java
@@ -120,4 +120,6 @@ public class UpdateFinanceHubConfigCommand {
     Optional<Double> gaMutationRate = Optional.empty();
     @Builder.Default
     Optional<Integer> gaEliteRatio = Optional.empty();
+    @Builder.Default
+    Optional<Integer> simulationEarlyRepaymentMonth = Optional.empty();
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
index 6ba23e82..e171c781 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java
@@ -52,6 +52,7 @@ public class FinanceHubConfigResult {
     private Integer gaMaxGenerations;
     private Double gaMutationRate;
     private Integer gaEliteRatio;
+    private Integer simulationEarlyRepaymentMonth;
 
     /**
      * Converts a FinanceHubConfig domain entity to a FinanceHubConfigResult.
@@ -86,6 +87,7 @@ public class FinanceHubConfigResult {
         result.setGaMaxGenerations(entity.getGaMaxGenerations());
         result.setGaMutationRate(entity.getGaMutationRate());
         result.setGaEliteRatio(entity.getGaEliteRatio());
+        result.setSimulationEarlyRepaymentMonth(entity.getSimulationEarlyRepaymentMonth());
 
         return result;
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index a143dcd6..bf599566 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -102,7 +102,8 @@ public class MortgageCalculator {
         return applicableRate;
     }
 
-    private long getOverrideEarlyRepaymentAmount(MortgageRecord m, YearMonth currentYm, SimulationOverrides overrides) {
+    private long getOverrideEarlyRepaymentAmount(MortgageRecord m, YearMonth currentYm,
+            SimulationOverrides overrides, SimulationContext context) {
         if (overrides == null || overrides.mortgageEarlyRepayments() == null) {
             return 0L;
         }
@@ -112,11 +113,10 @@ public class MortgageCalculator {
             return 0L;
         }
 
-        int logicalMonth = 12;
-        if (m.getBonusPayments() != null && !m.getBonusPayments().isEmpty()) {
-            Integer firstMonth = m.getBonusPayments().get(0).getMonth();
-            logicalMonth = firstMonth != null ? firstMonth : 12;
-        }
+        Integer configuredMonth = (context != null && context.getConfig() != null)
+                ? context.getConfig().getSimulationEarlyRepaymentMonth()
+                : null;
+        int logicalMonth = (configuredMonth != null) ? configuredMonth : 12;
 
         if (currentYm.getMonthValue() == logicalMonth) {
             long totalAmount = 0L;
@@ -379,7 +379,9 @@ public class MortgageCalculator {
 
                 double monthlyRate = currentRates[i] / 12.0;
 
-                long earlyPrincipal = isFutureMonth ? getOverrideEarlyRepaymentAmount(m, currentYm, overrides) : 0L;
+                long earlyPrincipal = isFutureMonth
+                        ? getOverrideEarlyRepaymentAmount(m, currentYm, overrides, context)
+                        : 0L;
                 long earlyFee = 0L;
 
                 if (isFutureMonth && m.getEarlyRepayments() != null) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactoryTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactoryTest.java
index 489eb343..d0a3f155 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactoryTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactoryTest.java
@@ -71,5 +71,6 @@ class DefaultConfigFactoryTest {
         assertEquals("sys-config", config.getName());
         assertTrue(config.getEnabled());
         assertEquals(60, config.getFileProcessingTimeoutSeconds());
+        assertEquals(12, config.getSimulationEarlyRepaymentMonth());
     }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultPropertiesTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultPropertiesTest.java
index 5e3ef4b0..4c62c17a 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultPropertiesTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultPropertiesTest.java
@@ -23,15 +23,16 @@
  */
 package ch.lin.finance.hub.backend.api.app.config.system;
 
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
+import static org.junit.jupiter.api.Assertions.assertNull;
 import org.junit.jupiter.api.Test;
 
 import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
 import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
 
-import java.util.Map;
-
-import static org.junit.jupiter.api.Assertions.*;
-
 class FinanceHubDefaultPropertiesTest {
 
     @Test
@@ -52,6 +53,7 @@ class FinanceHubDefaultPropertiesTest {
         assertEquals("default", config.getName());
         assertFalse(config.getEnabled());
         assertEquals(10, config.getFileProcessingTimeoutSeconds());
+        assertEquals(12, config.getSimulationEarlyRepaymentMonth());
     }
 
     @Test
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
index d395bb69..50da7437 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImplTest.java
@@ -217,12 +217,14 @@ class FinanceHubConfigServiceImplTest {
                 .nisaExpectedReturnRate(Optional.empty())
                 .idecoExpectedReturnRate(Optional.empty())
                 .pensionExpectedMonthlySalary(Optional.empty())
+                .simulationEarlyRepaymentMonth(Optional.of(6))
                 .build();
 
         FinanceHubConfig result = service.saveConfig(command);
 
         assertTrue(result.getEnabled());
         assertEquals(20, result.getFileProcessingTimeoutSeconds());
+        assertEquals(6, result.getSimulationEarlyRepaymentMonth());
         assertFalse(otherEnabled.getEnabled());
         verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
     }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
index dd73f767..feb45ad0 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
@@ -130,6 +130,7 @@ public class FinanceHubConfig extends AuditableEntity {
     public static final String GA_MAX_GENERATIONS_COLUMN = "ga_max_generations";
     public static final String GA_MUTATION_RATE_COLUMN = "ga_mutation_rate";
     public static final String GA_ELITE_RATIO_COLUMN = "ga_elite_ratio";
+    public static final String SIMULATION_EARLY_REPAYMENT_MONTH_COLUMN = "simulation_early_repayment_month";
     public static final String DEFAULT_CURRENCY_COLUMN = "default_currency";
 
     /**
@@ -285,6 +286,17 @@ public class FinanceHubConfig extends AuditableEntity {
     @Setter
     private Integer gaEliteRatio = 10;
 
+    /**
+     * The default month for simulation early repayment (1-12).
+     */
+    @NotNull
+    @Min(value = 1)
+    @Max(value = 12)
+    @ColumnDefault("12")
+    @Column(name = FinanceHubConfig.SIMULATION_EARLY_REPAYMENT_MONTH_COLUMN, nullable = false)
+    @Setter
+    private Integer simulationEarlyRepaymentMonth = 12;
+
     /**
      * Creates a new FinanceHubConfig with the specified name.
      *
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfigTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfigTest.java
index 03ce5ed6..1f357e6b 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfigTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfigTest.java
@@ -23,10 +23,10 @@
  */
 package ch.lin.finance.hub.backend.api.domain.model.config;
 
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
 import org.junit.jupiter.api.Test;
 
-import static org.junit.jupiter.api.Assertions.*;
-
 class FinanceHubConfigTest {
 
     @Test
@@ -36,5 +36,6 @@ class FinanceHubConfigTest {
         assertEquals("default", config.getName());
         assertFalse(config.getEnabled());
         assertEquals(10, config.getFileProcessingTimeoutSeconds());
+        assertEquals(12, config.getSimulationEarlyRepaymentMonth());
     }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
index 5e99f3db..fc4d8b7b 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java
@@ -131,6 +131,7 @@ public class FinanceHubConfigController {
                 .gaMaxGenerations(request.getGaMaxGenerations())
                 .gaMutationRate(request.getGaMutationRate())
                 .gaEliteRatio(request.getGaEliteRatio())
+                .simulationEarlyRepaymentMonth(request.getSimulationEarlyRepaymentMonth())
                 .build();
 
         FinanceHubConfigResponse createdConfig = configMapper.toResponse(configService.createConfigResult(command));
@@ -192,6 +193,7 @@ public class FinanceHubConfigController {
                 .gaMaxGenerations(Optional.ofNullable(request.getGaMaxGenerations()))
                 .gaMutationRate(Optional.ofNullable(request.getGaMutationRate()))
                 .gaEliteRatio(Optional.ofNullable(request.getGaEliteRatio()))
+                .simulationEarlyRepaymentMonth(Optional.ofNullable(request.getSimulationEarlyRepaymentMonth()))
                 .build();
 
         FinanceHubConfigResponse savedConfig = configMapper.toResponse(configService.saveConfigResult(command));
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
index ddea75e5..81f2ac1d 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java
@@ -137,4 +137,9 @@ public class CreateFinanceHubConfigRequest {
     @Min(value = 1, message = "Elite ratio must be at least 1%")
     @Max(value = 50, message = "Elite ratio cannot exceed 50%")
     private Integer gaEliteRatio;
+
+    @NotNull(message = "Simulation early repayment month cannot be null")
+    @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
+    @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
+    private Integer simulationEarlyRepaymentMonth = 12;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
index 05269737..cafc2223 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java
@@ -48,4 +48,5 @@ public class FinanceHubConfigResponse {
     private Integer gaMaxGenerations;
     private Double gaMutationRate;
     private Integer gaEliteRatio;
+    private Integer simulationEarlyRepaymentMonth;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
index 1850f9f2..e06186c4 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java
@@ -103,4 +103,8 @@ public class UpdateFinanceHubConfigRequest {
     @Min(value = 1, message = "Elite ratio must be at least 1%")
     @Max(value = 50, message = "Elite ratio cannot exceed 50%")
     private Integer gaEliteRatio;
+
+    @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
+    @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
+    private Integer simulationEarlyRepaymentMonth;
 }
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 76623f52..58a744ed 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -101,6 +101,9 @@ finance.hub.system-config.ga-max-generations=20
 finance.hub.system-config.ga-mutation-rate=0.2
 finance.hub.system-config.ga-elite-ratio=10
 
+# Simulation Defaults
+finance.hub.system-config.simulation-early-repayment-month=12
+
 # =============================================================================
 # Tax Rule Defaults
 # =============================================================================
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
index a1495dc1..c1e8b9e3 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java
@@ -117,6 +117,7 @@ class FinanceHubConfigControllerTest {
         createdConfig.setName("new-config");
         createdConfig.setEnabled(true);
         createdConfig.setFileProcessingTimeoutSeconds(60);
+        createdConfig.setSimulationEarlyRepaymentMonth(12);
 
         when(configService.createConfigResult(any(CreateFinanceHubConfigCommand.class))).thenReturn(createdConfig);
 
@@ -129,7 +130,8 @@ class FinanceHubConfigControllerTest {
                         Objects.requireNonNull(org.hamcrest.Matchers
                                 .containsString("/configs/system/new-config"))))
                 .andExpect(jsonPath("$.data.name").value("new-config"))
-                .andExpect(jsonPath("$.data.enabled").value(true));
+                .andExpect(jsonPath("$.data.enabled").value(true))
+                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(12));
 
         verify(configService, times(1)).createConfigResult(any(CreateFinanceHubConfigCommand.class));
     }
@@ -171,6 +173,7 @@ class FinanceHubConfigControllerTest {
         savedConfig.setName("test-config");
         savedConfig.setEnabled(false);
         savedConfig.setFileProcessingTimeoutSeconds(30);
+        savedConfig.setSimulationEarlyRepaymentMonth(6);
 
         when(configService.saveConfigResult(any(UpdateFinanceHubConfigCommand.class))).thenReturn(savedConfig);
 
@@ -179,7 +182,8 @@ class FinanceHubConfigControllerTest {
                 .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.name").value("test-config"))
-                .andExpect(jsonPath("$.data.enabled").value(false));
+                .andExpect(jsonPath("$.data.enabled").value(false))
+                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(6));
 
         verify(configService, times(1)).saveConfigResult(any(UpdateFinanceHubConfigCommand.class));
     }
diff --git a/finance-hub-backend/todo/pending fix/issue-106.md b/finance-hub-backend/todo/pending fix/issue-106.md
index 6b9747d5..82fdcd7a 100644
--- a/finance-hub-backend/todo/pending fix/issue-106.md
+++ b/finance-hub-backend/todo/pending fix/issue-106.md
@@ -35,3 +35,105 @@
 - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`（`getOverrideEarlyRepaymentAmount`，決定提前還款扣在哪個月的地方）
 - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:90-94`（`MortgageEarlyRepaymentOverride`，目前只有`year`、`amount`兩個欄位）
 - `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageRecord.java:108-110`（`bonusPayments`，房貸契約上的獎金還款月份清單，沒有排序規則）
+
+---
+
+## 最終決策與架構定案（Architecture Decision Record）
+
+### 決策結論
+**改由系統配置（Finance Hub Config）統一控制提前還款月份，預設值為 12 月。**  
+- **不寫死（No Magic Number）**：不在 `MortgageCalculator` 內部寫死數字 12。
+- **暫不開放使用者介面修改**：維持目前前端 Sandbox 簡潔直覺的 `(year, amount)` 輸入介面，不增加前端負擔與使用者認知成本。
+- **徹底解耦**：完全拔除 `m.getBonusPayments().get(0)` 偷借契約月份的邏輯。
+
+### 決策理由與好處
+1. **防止隱性技術債（Magic Number 遺忘症）**：
+   如果直接在計算器裡硬編碼 `12`，時間一久團隊將遺忘這個業務假設的存在。提升為系統設定值後，在配置檔與系統設定清單中清晰可見。
+2. **為未來擴充預留彈性**：
+   未來若想開放「讓使用者在設定頁自訂偏好還款月」或「個別模擬覆蓋自訂月」，底層資料流已經由 Config 串接好（`context.getConfig().getSimulationEarlyRepaymentMonth()`），擴充時只需在前端/API 加開欄位，底層引擎無需再次重構。
+3. **消除不確定性與無辜耦合**：
+   徹底解決 Hibernate `@ElementCollection` 集合無序導致 6 月/12 月隨機跳動的問題，也讓常態契約獎金還款（ボーナス払い）回歸原本單純的日常扣款職責。
+
+### 相關異動檔案（全鏈路實作範圍）
+
+本設定項（`simulationEarlyRepaymentMonth`）屬於系統全域設定，遵循系統既有的 `FinanceHubConfig` 完整生命週期架構，異動範圍分為以下四層：
+
+#### 1. 核心計算層（Core Calculator）
+- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131`（`getOverrideEarlyRepaymentAmount`，改由 `context.getConfig()` 取得月份，徹底拔除對 `bonusPayments` 的讀取）
+
+#### 2. 領域實體與預設配置（Domain & Default Properties）
+- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java`（新增實體欄位、常數與 `@Min(1) @Max(12)` 驗證）
+- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java`（`SystemConfig` 新增屬性）
+- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/DefaultConfigFactory.java`（`create` 方法映射新屬性至實體）
+
+#### 3. 管理員 REST API 全鏈路（Admin API & CQRS / DTO / Service）
+*因為系統設定可由管理者透過 API 進行 CRUD 管理，需同步擴充請求、回應與指令物件：*
+- **Controller & DTO**：
+  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java`
+  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java`
+  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java`
+  - `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigMapper.java`
+- **Application Command & Service**：
+  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java`
+  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java`
+  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java`
+  - `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/FinanceHubConfigServiceImpl.java`
+
+#### 4. 部署環境變數與設定檔（Deployment & Environment Profiles）
+- `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties`（新增 `finance.hub.system-config.simulation-early-repayment-month=12`）
+- `finance-hub/.env.base`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
+- `finance-hub/.env.dev`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
+- `finance-hub/.env.prod`（新增 `FINANCE_HUB_SYSTEM_CONFIG_SIMULATION_EARLY_REPAYMENT_MONTH="12"`）
+- `finance-hub/.env.example`（同步補齊範例環境變數）
+
+#### 5. 單元與整合測試同步更新（Tests）
+- `FinanceHubConfigTest.java`
+- `FinanceHubDefaultPropertiesTest.java`
+- `DefaultConfigFactoryTest.java`
+- `FinanceHubConfigServiceImplTest.java`
+- `FinanceHubConfigControllerTest.java`
+
+### 實作要點指引
+1. **設定項擴充與驗證**：
+   - 欄位限制：月份必須為 1～12 之間的整數（`@Min(1) @Max(12)`）。
+   - 預設值：全環境統一預設為 `12`。
+2. **計算引擎改造（`MortgageCalculator.java`）**：
+   - 改造 `getOverrideEarlyRepaymentAmount`（或傳入 `SimulationContext`）：
+   ```java
+   int logicalMonth = context.getConfig() != null && context.getConfig().getSimulationEarlyRepaymentMonth() != null
+           ? context.getConfig().getSimulationEarlyRepaymentMonth()
+           : 12;
+   ```
+   - 徹底刪除原先對 `m.getBonusPayments()` 的任何讀取。
+
+---
+
+## 實作進度追蹤（Implementation Progress）
+
+- [x] **Phase 1: 領域實體與預設配置（Domain & Default Properties）**
+  - [x] `FinanceHubConfig.java`（新增欄位、常數、getter/setter 與 `@Min(1) @Max(12)` 驗證）
+  - [x] `FinanceHubDefaultProperties.java`（`SystemConfig` 新增屬性與預設值 12）
+  - [x] `DefaultConfigFactory.java`（`create` 方法映射新屬性至實體）
+- [x] **Phase 2: 部署環境變數與設定檔（Deployment & Environment Profiles）**
+  - [x] `application.properties`（新增 `finance.hub.system-config.simulation-early-repayment-month=12`）
+  - [x] `finance-hub/.env.base`
+  - [x] `finance-hub/.env.dev`
+  - [x] `finance-hub/.env.prod`
+  - [x] `finance-hub/.env.example`
+- [x] **Phase 3: 管理員 REST API 全鏈路（Admin API & CQRS / DTO / Service）**
+  - [x] DTO: `CreateFinanceHubConfigRequest` / `UpdateFinanceHubConfigRequest` / `FinanceHubConfigResponse`
+  - [x] Mapper: `FinanceHubConfigMapper`
+  - [x] Command & Result: `CreateFinanceHubConfigCommand` / `UpdateFinanceHubConfigCommand` / `FinanceHubConfigResult`
+  - [x] Service: `FinanceHubConfigServiceImpl`
+- [x] **Phase 4: 核心計算層改造（Core Calculator）**
+  - [x] `MortgageCalculator.java`（改自 `context.getConfig()` 讀取，徹底拔除 `bonusPayments`）
+- [x] **Phase 5: 測試更新與回歸驗證（Tests & Verification）**
+  - [x] `FinanceHubConfigTest`
+  - [x] `FinanceHubDefaultPropertiesTest`
+  - [x] `DefaultConfigFactoryTest`
+  - [x] `FinanceHubConfigServiceImplTest`
+  - [x] `FinanceHubConfigControllerTest`
+- [x] **Phase 6: 前端管理介面適配（Frontend Admin UI）**
+  - [x] `hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts`（`systemConfigSchema` 新增 `simulationEarlyRepaymentMonth` 驗證與預設值）
+  - [x] `hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx`（`SystemConfigPanel` 新增 Simulation Settings 區塊與月份輸入框）
+  
\ No newline at end of file
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10011  12.84G   7.41    17:24:18  