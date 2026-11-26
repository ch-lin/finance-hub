# Fixed 62：所得稅住民稅的基礎控除等三項稅制數字沒有隨模擬年度更新，還在用2024年以前的舊制

## 嚴重程度
High——這不是邊緣情境，而是影響「每一位使用者、每一次模擬、2025年以後的每一個模擬年度」的問題。今天的日期是2026年9月15日，這已經是一個「現在正在發生」的問題，不是未來才會出現的風險。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/tax/ActiveTaxCalculationEngine.java`（基礎控除常數、薪資所得扣除計算方法）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/tax/DependentDeductionEngine.java`（受扶養親屬年收入門檻常數）

## 現狀
以下三個日本稅制數字，程式碼裡目前是寫死的固定值，完全沒有隨模擬所在的年度切換：

1. **基礎控除**：程式碼寫死480,000元，不論所得高低都用同一個金額。但真實法律從令和7年度（2025年度）起，已經改成依所得高低分級的金額，令和8-9年度（2026-2027年度）還有進一步調整。
2. **薪資所得扣除最低金額**：程式碼寫死550,000元。但真實法律已經上調到650,000元（令和7年度），令和8年度（2026年度）再上調到740,000元，且整張級距表的內容也已經改變。
3. **受扶養親屬年收入門檻**：程式碼寫死1,030,000元。但真實法律已經上調到1,230,000元，之後再上調到1,360,000元。

## 原因
`ActiveTaxCalculationEngine.java` 裡（第46-48行）：

```java
// NTA Constants for 2024
private static final long BASIC_DEDUCTION_INCOME_TAX = 480_000L;
private static final long BASIC_DEDUCTION_INHABITANT_TAX = 430_000L;
```

薪資所得扣除的計算方法（第208-222行）：

```java
private long calculateEmploymentIncomeDeduction(long grossSalary) {
    if (grossSalary <= 1_625_000) {
        return 550_000;
    } else if (grossSalary <= 1_800_000) {
        return (long) (grossSalary * 0.4) - 100_000;
    } else if (grossSalary <= 3_600_000) {
        return (long) (grossSalary * 0.3) + 80_000;
    } else if (grossSalary <= 6_600_000) {
        return (long) (grossSalary * 0.2) + 440_000;
    } else if (grossSalary <= 8_500_000) {
        return (long) (grossSalary * 0.1) + 1_100_000;
    } else {
        return 1_950_000; // Cap
    }
}
```

程式碼裡的註解自己就寫著「NTA Constants for 2024」，等於承認這是2024年度的舊制數字，但完全沒有機制依照模擬所在的年度切換成正確年度的數字。

值得注意的是：`calculate()` 這個方法（第50-54行）本身確實有接收 `simulationYear`（模擬年度）這個參數，而且這個參數在同一個方法裡其他地方確實有被拿來使用——例如第125、127行用它判斷保單是否已到期，第93、108、110行用它計算家庭成員在模擬年度年底時的年齡。也就是說，系統明明已經知道目前模擬的是哪一年，卻完全沒有拿這個已經正確傳入的年度資訊，去切換這兩組稅制常數。

同樣的根本原因，也出現在 `DependentDeductionEngine.java` 第55行與第84行：

```java
if (dependentIncome > 1_030_000L) return 0L;
```

這個方法同樣有接收 `simulationYearEnd`（模擬年度年底日期）參數，但這個參數只被用於計算家庭成員年齡（第64、92行），從未被用於選擇正確年度的收入門檻數字。

## 具體錯誤情境
2026年度的模擬，家庭成員（例如就讀大學的小孩）打工年收入120萬元。依真實2026年度法律，這已經在放寬後的136萬元受扶養門檻之內，應該還能算作受扶養親屬，享有扶養扣除。但系統用舊的103萬元門檻去判斷，錯誤地認定這個小孩已經不能算作受扶養親屬，導致這筆合法的扶養扣除被漏算。同時，使用者自己的基礎控除跟薪資所得扣除，也都是用比較低（也就是比較不利）的舊數字去計算，導致應稅所得被高估。這三者加總起來的效果，會讓2026年以後每一個模擬年度的可支配現金都被低估，讓長期現金流的推算結果比真實情況更悲觀。

## 建議修正
以下三個方向各有取捨：

**方向A：建立一個「依模擬年度查表」的機制（例如2025年、2026-2027年、2028年以後分別對應各自的數字），讓已經正確傳入的 `simulationYear` 真正被使用**
- 優點：最準確，未來法律再調整時只需要在表格裡新增一列，不需要改動計算邏輯本身。
- 缺點：因為日本這幾項數字幾乎每年都會調整，需要有人定期查證最新公告並維護這張表，否則同樣的過期問題會再次發生。

**方向B：直接把常數更新成目前（2026年）生效的最新數字，不建立年度切換機制，未來法律再變動時再次手動改程式碼**
- 優點：改動範圍小，可以馬上出貨修正目前確定存在的落差。
- 缺點：因為這個工具的模擬時間範圍常常長達20-30年，未來法律再調整時，模擬中途又會重新出現同樣的過期問題；而且這個作法也沒辦法正確回溯模擬2025年以前、適用更舊數字的年度。

**方向C：把這幾項稅制參數改成可設定的使用者／管理員設定值，不寫死在程式碼裡**
- 優點：未來法律變動時不需要改程式碼或重新部署。
- 缺點：一般使用者不一定知道正確的數字該填多少，需要額外設計預設值跟說明文字，否則可能造成使用者自己填錯資料。

## 與已知項目的區別
已檢查 `todo/planned/` 與 `todo/wontfix/`，沒有任何現有項目提到基礎控除、薪資所得扣除、受扶養親屬門檻這三項數字過期未更新的問題——`Plan-45`（個人年金保險保費扣除完全沒被扣除）討論的是完全不同的3個數字與程式路徑；`Plan-31`（iDeCo/DC沒有法定投入上限的檢查）與本問題無關；`Plan-37`（DC/iDeCo解耦後可能重複計算退休扣除額的風險）討論的是退休一次性提領扣除額，跟本問題討論的在職期間所得稅／住民稅常數完全無關。屬於全新發現的問題。

## 最新討論與架構共識（混合方向 A 與 C）
經過進一步的架構評估，決定結合 Claude 提議的「方向A」與「方向C」，得出以下最佳實踐：

1. **結合「方向C (可設定設定值)」與「方向A (最新年度)」的精神**：
   - 採用 **方向C**：將基礎控除、薪資所得扣除級距、受扶養親屬門檻等數字從程式碼抽離，改為「系統管理員可設定的全域參數（存於 DB 或 Config，類似現有的 `FinanceHubConfig`）」，徹底解決寫死在程式碼裡的問題。
   - 簡化 **方向A**：因為模擬器永遠是面向未來的預測，不需要回溯相容舊年份（例如 2024、2025）的稅制。所以我們不需要真的實作龐大複雜的「依年度查表」或策略模式（Strategy Pattern），而是只要在 DB 裡維護「唯一一組最新生效的稅制設定」即可。這等同於 A 加上 C 的精簡版。
2. **計算機絕對不能碰 Repo（效能與 DDD 原則）**：為避免在幾十萬次的模擬迴圈中呼叫 DB 導致效能崩潰，這包設定必須在最外層（API 或 Pipeline 初始化時）撈取，轉成唯讀的 DTO 或 POJO 後，透過 `SimulationContext` 傳入 `ActiveTaxCalculationEngine`。計算機必須保持為純函數（Pure Function），避免在 Transaction 內意外修改 JPA Entity 觸發 Dirty Checking。
3. **資料驅動設計（Table-Driven Design）解決 if-else 僵化**：針對薪資所得扣除的級距（Brackets），不只數字會變，**「級距的層數」也可能會變**。因此不能單純把孤立的變數抽出，而是要把級距設計成 `List<TaxBracket>` 資料結構（包含 `maxSalary`, `multiplier`, `offset`）。計算引擎內部要消滅寫死的 `if-else`，改用動態的 `for` 迴圈來遍歷這張由外部傳入的級距表。這樣未來不管稅法改成幾個級距，引擎程式碼都無需再做任何修改。

## 藍圖
以下是基於領域驅動設計（DDD）重新梳理的具體架構設計藍圖，將稅務設定獨立，避免 `FinanceHubConfig` 成為 God Object：

### 1. Domain (實體) 與 Properties 層
- **獨立建立 `TaxRuleConfig` (Entity)**：新增專門存放稅務常數的 Entity，加入 `name` (設定檔名稱) 與 `enabled` (是否啟用) 欄位以支援多 Profile 設定。同時包含 `basicDeductionIncomeTax`, `basicDeductionHabitantTax`, `dependentIncomeThreshold` 等純量欄位。
- **建立具備型別的 `TaxBracket` (Entity)**：與 `TaxRuleConfig` 建立 `@OneToMany` 關聯。加入 `bracketType` 欄位（例如 Enum：`EMPLOYMENT_INCOME_DEDUCTION` 薪資所得扣除、`INCOME_TAX_RATE` 所得稅率），以便區分不同的級距表。
- **`TaxRuleDefaultProperties`**：在 Properties 配置檔中建立對應的屬性結構，確保系統啟動時能從 `application.properties` 載入預設的稅制與各類別的級距表，並支援透過 Spring Boot 的環境變數機制（如 `.env` 或 OS 環境變數）動態覆蓋這些設定。

### 2. Service 與 Controller 層 (Clean Architecture)
- **Controller 層端點 (Endpoints)**：參考 `FinanceHubConfigController` 的 Profile 設計，建立 `TaxRuleConfigController`，掛載於 `/configs/tax-rule`，支援完整的 CRUD：
  - `GET /configs/tax-rule`：取得所有設定檔名稱與目前啟用的設定檔。
  - `GET /configs/tax-rule/{name}`：取得特定名稱的稅制設定與所有級距表。
  - `POST /configs/tax-rule`：建立新的稅制設定檔。
  - `PATCH /configs/tax-rule/{name}`：更新特定名稱的稅制設定。
  - `DELETE /configs/tax-rule/{name}` / `DELETE /configs/tax-rule`：刪除特定或所有稅制設定檔。
- **Controller 層 DTO**：建立 API 專用的 `UpdateTaxRuleConfigRequest` 與 `TaxRuleConfigResponse`，處理外部傳入與輸出的資料格式。
- **Service 層 DTO**：建立 `TaxRuleConfigService` 來專責處理稅制，並定義專屬的 `UpdateTaxRuleConfigCommand` 以及 `TaxRuleConfigResult` 作為跨層傳遞的結構。
- **Mapper 機制**：建立對應的 Mapper 介面與實作，嚴格落實 `Request -> Command` (Controller)、`Command -> Entity` (Service)、`Entity -> Result` (Service)、`Result -> Response` (Controller) 的分層轉換，確保各層邊界不被污染。

### 3. Simulation Context 隔離層 (確保計算機不碰 Repo)
- **`TaxConfigDto`**：建立不可變的 DTO。在 Mapping 時，根據 `bracketType` 將級距表分類成不同的 List（例如 `List<TaxBracketDto> employmentDeductionBrackets`），以便引擎直接使用。
- **`SimulationContext`**：新增 `TaxConfigDto` 屬性，不讓 Engine 接觸 JPA Entity。
- **`SimulationContextFactory`**：在模擬初始化的最外層，呼叫 `TaxRuleConfigService` 取得最新稅制，將其整理並分類為 `TaxConfigDto` 後，塞入 Context 中。

### 4. 稅務計算引擎 (資料驅動實作)
- **`ActiveTaxCalculationEngine`**：完全不注入任何 Repository。方法一開始從 `context.getTaxConfig()` 取得分類好的資料。將原本寫死的 `if-else` 邏輯改為針對特定分類（如 `employmentDeductionBrackets`）進行的動態 `for` 迴圈遍歷。
- **`DependentDeductionEngine`**：將原本寫死的 `1030000L` 替換為 `context.getTaxConfig().getDependentIncomeThreshold()`。

### 5. 前端 UI 實作 (hub-ui)
- **自訂 Hook (`use-tax-rule-configs.ts`)**：參考 `use-finance-configs.ts` 建立專屬的 React Hook，負責呼叫 `/api/finance/configs/tax-rule` 進行資料的 Fetch 與 Mutation，同樣支援 Profile（`name`）的切換與 CRUD。
  - **盲點防護 (Cache Invalidation)**：當啟用某個配置（PATCH `enabled: true`）時，後端會自動禁用其他配置，Hook 必須觸發全局的 Invalidate / Refetch，讓畫面上其他配置的「啟用中」狀態能同步消失。
- **UI 元件 (`tax-rule-config-panel.tsx`)**：
  - 建立 `TaxRuleConfigPanel` 元件，負責顯示稅制相關純量常數（如基礎控除、受扶養親屬門檻）的表單。
    - **盲點防護 (Thousand Separators)**：純量常數與上限金額應導入支援千分位 Mask 的 Input 元件（例如 `1,950,000`），避免使用者因數字過大而輸入錯誤。
    - **盲點防護 (預設值刪除保護)**：當判斷當前載入的 Config name 是 `default` 時，主動 Disable 或隱藏 Delete 按鈕，避免呼叫後端才拋出錯誤。
  - **級距表編輯器 (Tax Bracket Editor)**：由於級距表為動態的 `List<TaxBracket>` 結構，在 UI 上需要實作表格功能，允許使用者對特定類別（如 `EMPLOYMENT_INCOME_DEDUCTION`）的級距進行「新增」、「刪除」與「編輯」。
    - **盲點防護 (全量更新資料遺失風險)**：後端 API 接收的是單一扁平的 Array（`List<TaxBracketRequest>`）。若 UI 將不同類別的級距拆分成不同區塊編輯，送出 Request 前必須將所有區塊的資料合併（Merge）回同一個 Array，並補上對應的 `bracketType`，否則後端的 `clearTaxBrackets()` 會將未送出的類別全數刪除。
    - **盲點防護 (Null 處理)**：後端以 `maxSalary == null` 作為無上限最高級距。前端表單必須將空字串或「無上限」選項正確對映為 JSON 的 `null`。
    - **盲點防護 (Multiplier 單位)**：後端 `multiplier` 為小數（如 `0.10` 代表 10%），前端 UI 若讓使用者輸入 `%`（如輸入 `10`），在提交前必須除以 100，讀取時乘以 100。
- **頁面整合 (`page.tsx`) 改用 Tabs 介面**：為避免頁面過長，將 `app/finance/configs/general/page.tsx` 改寫為使用 `Tabs` 元件（如 shadcn/ui 的 Tabs）。建立三個獨立的分頁：「System Config」、「Transaction Source Config」與「Tax Rule Config」。如此可讓 `SystemConfigPanel`、`TransactionSourceConfigPanel` 與新的 `TaxRuleConfigPanel` 分別在獨立的 Tab 中渲染，確保設定畫面乾淨且 API 呼叫互不干擾。

#### Note

6 個必須在 Phase 5 實作前補上的周延考量（Blind Spots）：

### 1. 級距表「全量更新 (Payload Overwrite)」導致的資料遺失風險 (最嚴重)
* **盲點**：目前的 Backend API (`UpdateTaxRuleConfigRequest`) 接收的是一個扁平的 `List<TaxBracketRequest> brackets`，且在 Service 層的實作是 `config.clearTaxBrackets()` 然後全部重新 Insert。
* **UI 必須解決**：如果在 UI 上，我們把 `薪資所得扣除`、`所得稅率`、`住民稅率` 分成三個獨立的編輯區塊（或是 Tabs），當使用者只編輯並按下「儲存」其中一個區塊時，**前端絕對不能只送出該區塊的 List**。前端必須在送出 Request 前，把所有區塊的資料「合併（Merge）」回同一個 Array，否則後端會直接把沒有送過來的另外兩個級距表全部刪除，造成災難性的資料遺失。

### 2. 無上限級距 (Null Value) 在表單中的表達與轉換
* **盲點**：後端引擎利用 `maxSalary == null` 來代表「無上限」的最高級距（並透過 `Long.MAX_VALUE` 防呆）。但 HTML 的 `<input type="number">` 在空白時通常是空字串 `""`。
* **UI 必須解決**：前端在實作 Bracket Editor 時，必須對 `maxSalary` 進行攔截。當使用者留空、或是勾選一個「無上限 (Unlimited)」的 Checkbox 時，前端 Payload 必須明確轉成 JSON `null` 傳給後端。若前端沒有處理好，可能會送出 `0` 或是卡在表單必填驗證，導致永遠無法建立最高級距。

### 3. Multiplier 的單位認知落差 (百分比 vs 小數)
* **盲點**：後端的 `multiplier` 是存 `BigDecimal` (例如 `0.10` 代表 10%)。但在 UI 編輯器上，使用者習慣輸入的是 `10`。
* **UI 必須解決**：`Tax Bracket Editor` 的欄位如果是用 % 來標示，前端在讀取時必須 `* 100` 以供顯示，在 Submit 給 `PATCH` API 前必須 `/ 100`。如果不做這層轉換，使用者輸入 `10` 會讓稅率變成 1000%，模擬結果會直接算出天文數字的稅額。

### 4. 千分位 (Thousand Separators) 顯示與輸入
* **盲點**：日本稅制的數字通常很大（例如 `1,950,000`）。若純粹用原生的 `<input type="number">`，數字會擠在一起 (`1950000`)，非常容易少打或多打一個零。
* **UI 必須解決**：表單元件不能只用普通的 Number Input，對於純量常數（如基礎控除 `480,000`）與級距上限，應導入支援千分位 Mask 的 Input 元件，提升財經系統的防錯體驗。

### 5. `default` 設定檔的刪除防護
* **盲點**：後端 API 已經有寫好防護，不允許刪除名稱為 `default` 的設定檔，否則會丟出 Exception。
* **UI 必須解決**：不能等使用者按了刪除鍵才跳紅底錯誤。當 UI 判斷當前載入的 Config name 是 `default` 時，就應該主動 Disable 或隱藏 Delete 按鈕。

### 6. Enabled 狀態切換的畫面同步 (Cache Invalidation)
* **盲點**：後端邏輯是「只要某個 Config 被設為 `enabled: true`，後端會自動把其他所有 Config 設為 `false`」。
* **UI 必須解決**：React Hook (`use-tax-rule-configs.ts`) 在打完 `PATCH` API 將某個配置啟用後，**不能只更新自己這個配置的狀態**，必須觸發全局的 Invalidate / Refetch，讓畫面上其他被暗中禁用的配置，也能把「啟用中」的 Badge 或狀態正確拿掉。

---

> [!IMPORTANT]
> 前人的藍圖大方向是正確的，但完全沒有交代資料在 Client/Server 之間的這六層轉換細節。我們如果在 Phase 5 要實作時，必須把這幾點直接寫進 React Hooks 和 Zod Schema 的邏輯裡。

---

### 6. 移除 InsurancePolicyRepository (純化 Tax Engine 與 Calculator)
- **`InsurancePolicyDto`**：建立唯讀的 DTO (包含 `policyType`, `annualPremium`, `endDate`)，避免將 JPA Entity 帶入計算引擎。
- **`SimulationContext`**：新增 `List<InsurancePolicyDto> insurancePolicies` 屬性。
- **`SimulationContextFactory`**：在此處（最外層）注入 `InsurancePolicyRepository`，呼叫 `findAll()` 後轉為 DTO 存入 Context 中。
- **`ActiveTaxCalculationEngine`**：徹底移除 `InsurancePolicyRepository` 的依賴與注入，改由 `context.getInsurancePolicies()` 讀取保險設定，完成計算機的純粹化（Pure Function）。
- **`IncomeExpenseCalculator`**：同樣徹底移除 `InsurancePolicyRepository` 的依賴與注入，改由 `context.getInsurancePolicies()` 讀取保險設定，完成費用計算機的純粹化。

## 階段一實作報告 (Domain 與 Properties)
- **Entities**：建立了 `TaxRuleConfig`（包含 `name`、`enabled`、與稅制常數）、`TaxBracket`，與 `TaxBracketType` (Enum)，並維持頂層 import 的 Clean Architecture 規範。
- **Properties**：實作了 `TaxRuleDefaultProperties`（預設 `name="default"`, `enabled=true`）將 `application.properties` 中的稅制常數綁定到物件中。
- **環境變數**：同步將稅制預設值更新至 `.env.base`、`.env.dev`、`.env.prod`。
- **測試**：完成 `TaxRuleDefaultPropertiesTest` 並通過斷言驗證。

## 階段二實作報告 (Service 與 Controller 層)
- **Controller**：建立了 `TaxRuleConfigController`，提供完整的 CRUD Endpoints (`GET`, `POST`, `PATCH`, `DELETE`) 來管理稅制設定與級距表，並嚴格將 HTTP 請求封裝至 API 專用的 Request/Response DTO。
- **Service**：建立了 `TaxRuleConfigService` 與 `TaxRuleConfigServiceImpl`。
  - 實作了維持唯一啟用狀態的邏輯（確保 `enabled = true` 只有一筆）。
  - 加入了 `TaxRuleConfigFactory`，負責將 `application.properties` 的設定轉換為 Entity。
  - 利用 `@EventListener(ApplicationReadyEvent.class)` 在系統啟動且資料庫為空時，自動建立並啟用預設的 `TaxRuleConfig`。
- **Mapper 機制**：透過 `TaxRuleConfigMapper` 嚴格落實各層邊界間的資料轉換 (Request -> Command, Entity -> Result -> Response)。
- **測試**：新增並完善 `TaxRuleConfigServiceImplTest` 及 `TaxRuleConfigControllerTest`，涵蓋 Repository 查詢、啟動預設值寫入、Null Type Safety 以及異常處理 (ConfigNotFoundException, InvalidRequestException) 等單元測試。

## 階段三實作報告 (Simulation Context 隔離層)
- **Simulation Core Models (DTO)**：建立了不可變的 `TaxConfigDto` 與 `TaxBracketDto`，並成功針對不同的 `TaxBracketType` 先分類好三個 List（`employmentIncomeDeductionBrackets`、`incomeTaxRateBrackets`、`inhabitantTaxRateBrackets`）。
- **Simulation Context (Model)**：更新了 `SimulationContext`，加入 `taxConfig` 屬性，並實作 Shallow Copy 機制，隔離引擎對 Entity 的依賴。
- **Simulation Factory**：在 `SimulationContextFactory` 的 `createContext` 初始化時，呼叫 `TaxRuleConfigService` 取得最新啟用的稅制。同時精準實作了級距轉換與 `maxSalary` 的無上限防呆排序（當 `maxSalary` 為 null 時視為 `Long.MAX_VALUE`），確保後續引擎迴圈安全。
- **測試**：完成 `SimulationContextFactoryTest` 的更新，確保 Factory 能正確從 Service 層取得 Result 並映射綁定為 Context 內的 DTO 分類。

## 階段四實作報告 (稅務計算引擎 - 資料驅動實作)
- **ActiveTaxCalculationEngine 重構**：
  - 成功移除了寫死的基礎控除常數，全面改為讀取 `context.getTaxConfig().getBasicDeductionIncomeTax()` 與 `getBasicDeductionInhabitantTax()`。
  - **消滅 if-else (Table-Driven Design)**：`calculateEmploymentIncomeDeduction`、`calculateIncomeTax` 以及住民稅率計算，皆已改為傳入 `List<TaxBracketDto> brackets`，透過動態的 `for` 迴圈來計算，徹底解決級距層數僵化的問題。
  - 更新方法簽名（包含 `calculate`、`calculateRetirementTax`、`calculatePensionTax`），將 `SimulationContext` 作為第一個參數傳入，精簡了冗餘變數。
- **DependentDeductionEngine 重構**：更新方法簽名傳入 `SimulationContext`，並成功將 `1_030_000L` 寫死常數替換為動態的 `context.getTaxConfig().getDependentIncomeThreshold()`。
- **Pipeline 與測試修正**：配合參數精簡，成功調整了 `YearlySimulationPipeline` 中的呼叫方法；並一併修正 `ActiveTaxCalculationEngineTest` 及 `DependentDeductionEngineTest` 的 mock setup，確保邊界測試結果正確無誤。

## 階段五實作報告 (前端 UI 實作)
- **Shared UI Components**：手動實作了 `ThousandSeparatorInput`，標準化應用程式中的千分位數字輸入邏輯，並橋接前端字串顯示與後端 API 數字欄位。
- **React Hooks & Cache Invalidation**：建立了 `use-tax-rule-configs.ts` 管理稅務狀態與 CRUD，處理小數點與百分比轉換，並在 Save/Delete 後觸發重新取得 Profile 清單，確保畫面各配置「啟用中」的標籤狀態即時同步。
- **UI Panels & Editor**：
  - 實作了 `TaxRuleConfigPanel`，整合 Zod 與 React Hook Form，處理基本扣除額等常數設定，並實作 Default 設定檔的刪除防呆保護。
  - 實作了 `TaxBracketEditor` 管理級距表（包含 Null Value / Unlimited 的勾選邏輯），送出時將不同類別的級距全量合併，避免資料遺失。
- **Page Integration**：重構 `app/finance/configs/general/page.tsx`，導入 Tabs 介面將系統設定、稅務設定、交易來源三個設定區塊分離，提升 UI 可讀性與組織性。

## 階段六實作報告 (移除 InsurancePolicyRepository)
- **Model 與 Factory**：建立了唯讀的 `InsurancePolicyDto`；並在 `SimulationContext` 增加其列表（加入淺層拷貝）；同時於 `SimulationContextFactory` 注入 Repository，將實體轉換為 DTO。
- **計算機純化**：從 `ActiveTaxCalculationEngine` 與 `IncomeExpenseCalculator` 中徹底拔除 `InsurancePolicyRepository`，改由 Context 中讀取保險設定，達成完全隔離資料庫的 Pure Function 重構。
- **Unit Tests 修正**：全面移除了相關測試中的 `InsurancePolicyRepository` Mock 設定，並確保 `ActiveTaxCalculationEngineTest` 等改由注入 DTO 完成資料隔離測試。
