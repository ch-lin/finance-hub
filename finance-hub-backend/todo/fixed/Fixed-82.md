# Fixed 82：老年基礎年金滿額81.6萬元寫死未隨年度調整，已確認過期

## 嚴重程度
Medium（這個數字會直接影響模擬引擎試算「使用者退休後每月/每年能領到多少國民年金」，並進一步匯入退休後現金流、影響房貸破產模擬的長期現金流軌跡；但目前確認的落差方向是低估——使用者實際能領到的年金會比模擬顯示的更多，不會讓「會不會現金破產」的結論被誤導成過度樂觀。落差幅度目前約1.9%，還不到會讓破產結論反轉的量級，但因為完全沒有任何機制標記或提醒更新，這個落差只會隨著年度演進持續擴大，屬於真實存在、已確認過期、且會隨時間惡化的正確性問題。）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java:32`（老年基礎年金/國民年金滿額試算邏輯，`MAX_BASIC_PENSION_AMOUNT` 常數定義處，第40-45行的 `calculateProjectedBasicPension` 直接以此常數換算試算結果）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:210`（核心年度模擬管線，每一年的模擬都會呼叫這個方法算出當年度的年金收入，再匯入現金流試算）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java:127`（獨立的「年金推算」功能，同樣呼叫此方法顯示使用者未來可領到的年金金額）

## 現狀
模擬引擎在試算使用者退休後每月/每年能領到的「老年基礎年金（國民年金）」金額時，是用程式裡寫死的81萬6千元作為「滿額基準」去換算。但這個滿額基準，日本政府每年都會依照物價和薪資的變動幅度做小幅調整（不是那種久久才修一次法律才會變動的數字），程式裡卻完全沒有標明這個81萬6千元對應到哪一個年度，也沒有任何機制會隨年度更新它。結果是：只要使用者的模擬情境涵蓋到「退休後開始請領年金」這個階段（無論是在核心的房貸現金流模擬裡，還是在單獨的年金推算功能裡），畫面上顯示的年金金額，套用的都是一個已經確定過期、不是日本政府現行公告標準的舊數字，而且這個落差之後只會隨著年度演進持續擴大，不會自己修正。

## 原因
`PensionCalculator.java` 第32行：

```java
public static final long MAX_BASIC_PENSION_AMOUNT = 816000L;
```

這是一個寫死在程式碼裡的靜態常數，沒有任何欄位或註解標示這是哪個財政年度（令和幾年度）的官方公告數字，整個 calculator 目錄下的其他檔案也都是同樣「寫死常數、不按年度存值」的風格，沒有任何「隨財政年度切換基準值」的機制。

實際查證後確認：816,000円恰好精準對應到日本**令和6年度**（2024年4月起生效）的老齢基礎年金滿額；而**令和7年度**（2025年4月起生效）官方已經上調為**831,700円**。也就是說程式裡的數字，已經確定是至少落後一個財政年度以上的舊值（現在已進入令和8年度，令和8年度是否又已再次調整，目前沒能查到可靠的官方公告數字）。

由於這個常數在 `calculateProjectedBasicPension`（第40-45行）裡是直接乘進所有國民年金金額換算的基準值，且此方法同時被核心年度模擬管線（`YearlySimulationPipeline.java:210`）與獨立的年金推算功能（`PensionProjectionServiceImpl.java:127`）呼叫，只要模擬涵蓋到退休請領年金階段，這個過期數字就會被套用進每一筆試算結果。

## 具體錯誤情境
使用者設定退休模擬情境，試算65歲起請領國民年金（繳滿480個月、滿額）的每年年金收入。程式套用816,000円作為滿額基準去換算，但依照日本政府令和7年度（2025年4月起）已公告生效的最新標準，滿額應該是831,700円——也就是同樣繳滿480個月，模擬結果會比使用者依現行法規實際能領到的金額少算約15,700円/年（約1.9%）。這筆年金收入會逐年累加進退休後的現金流試算，使用者長期規劃「退休後現金夠不夠用」時，看到的年金收入會比真實情況略低；且因為令和8年度（現在）最新公告數字尚未查到，實際落差可能已經比1.9%更大。

## 建議修正
這個問題有兩種可行的修正方向，取捨不同：

**方向A：直接更新常數為最新官方數字，並加註對應財政年度與資料來源**
把 `MAX_BASIC_PENSION_AMOUNT` 更新為至少令和7年度的831,700円（若能查到令和8年度最新公告數字則直接採用），並在常數上方加上註解標明對應財政年度與資料來源，之後仰賴人工在每年度官方公告後手動更新。
- 優點：改動範圍最小，幾乎不影響現有程式架構，風險低，可以立即把目前確定存在的落差修正掉。
- 缺點：仍然仰賴「有人記得每年查最新公告並手動改程式碼、重新部署」，沒有任何提醒或強制機制——明年官方數字一調整，又會重新變成過期數字，無法根本解決「這個數字本質上每年都會變」這件事。

**方向B：把滿額金額改成可設定的外部設定值（依財政年度存值）**
例如改成設定檔或資料庫欄位，依財政年度存一筆對應金額，並在模擬結果中標示「目前試算套用的是哪個財政年度的年金滿額基準」，之後只要更新設定值，不需要改程式碼、重新部署。
- 優點：數字可追蹤、可即時更新，不會被鎖死在程式碼裡，對使用者也更透明（能看到自己試算依據的是哪一年度的官方基準）。
- 缺點：需要額外的設定/資料表與讀取邏輯，架構複雜度會提高；而且由於這個系統的模擬情境常常涵蓋到數十年後才開始請領年金，還會衍生出「模擬到的未來年度（官方尚未公告）的滿額金額該用什麼假設值」這個屬於另一層的設計問題——這已超出本問題「目前數字已過期」的範圍，建議另外開一個問題單獨討論，不在此一併下定論。

無論最終選哪個方向，都屬於實作方式的選擇、不涉及使用者操作介面的改動；但近期都應該先把目前確定過期的816,000更新為至少令和7年度的831,700円，避免持續套用一個已知落後至少一個財政年度的舊值。

## 與已知項目的區別
已檢查 `todo/planned/` 與 `todo/wontfix/` 目前列出的項目，沒有任何一項提到老年基礎年金滿額（或任何法定金額）被寫死、未隨年度更新而過期的問題，此為全新發現的問題。另外需特別說明：這個數字的性質，跟系統裡其他「久久才修一次法律才會變動」的法定常數（例如所得稅/住民稅的基礎控除金額）不同——老年基礎年金滿額是日本政府每年度都會依物價、薪資聯動幅度小幅調整的數字，兩者的維護頻率與性質不能混為一談，因此不宜比照那類「久久才需要review一次」的常數來處理。

## 藍圖
以下是比照 Issue 36，基於領域驅動設計（DDD）的架構藍圖，將年金設定抽離為獨立設定，以解決寫死的問題（方向B）：

### 1. Domain (實體) 與 Properties 層
- **獨立建立 `PensionRuleConfig` (Entity)**：新增專門存放年金常數的 Entity，加入 `name` (設定檔名稱) 與 `enabled` (是否啟用) 欄位以支援多 Profile 設定。包含 `maxBasicPensionAmount` (國民年金滿額基準) 等純量欄位。
- **`PensionRuleDefaultProperties`**：在 Properties 配置檔中建立對應的屬性結構，確保系統啟動時能從 `application.properties` 載入預設的年金滿額基準，並支援透過 Spring Boot 的環境變數機制（如 `.env`）動態覆蓋。

### 2. Service 與 Controller 層 (Clean Architecture)
- **Controller 層端點 (Endpoints)**：建立 `PensionRuleConfigController`，掛載於 `/configs/pension-rule`，支援完整的 CRUD 來管理不同的年金年度設定檔。
- **Controller 層 DTO**：建立 API 專用的 `UpdatePensionRuleConfigRequest` 與 `PensionRuleConfigResponse`。
- **Service 層 DTO**：建立 `PensionRuleConfigService`，並定義專屬的 `UpdatePensionRuleConfigCommand` 以及 `PensionRuleConfigResult` 作為跨層傳遞結構。
- **Mapper 機制**：建立對應的 Mapper 介面與實作，嚴格落實 Request -> Command -> Entity -> Result -> Response 的分層轉換，確保邊界不被污染。

### 3. Simulation Context 隔離層 (確保計算機不碰 Repo)
- **`PensionConfigDto`**：建立不可變的 DTO。
- **`SimulationContext`**：新增 `PensionConfigDto` 屬性，不讓 Engine 或 Calculator 接觸 JPA Entity。
- **`SimulationContextFactory`**：在模擬初始化的最外層，呼叫 `PensionRuleConfigService` 取得最新啟用的年金設定，轉為 `PensionConfigDto` 後塞入 Context 中。

### 4. 年金計算引擎 (資料驅動實作)
- **`PensionCalculator`**：徹底移除寫死的 `MAX_BASIC_PENSION_AMOUNT` 常數。改為從傳入的 `SimulationContext` (或其包含的 `PensionConfigDto`) 讀取動態的 `maxBasicPensionAmount`。
- **`PensionProjectionServiceImpl`**：同樣移除對靜態常數的依賴，改為注入或呼叫 Service 取得動態設定，確保獨立的年金推算功能也能吃到最新的法規滿額基準。

### 5. 前端 UI 實作 (hub-ui)
- **自訂 Hook (`use-pension-rule-configs.ts`)**：負責呼叫 `/api/finance/configs/pension-rule` 進行資料 Fetch 與 Mutation，支援 Profile 切換。包含 Cache Invalidation 防護機制（啟用某配置時同步刷新其他配置狀態）。
- **UI 元件 (`pension-rule-config-panel.tsx`)**：
  - 建立表單負責顯示並編輯年金滿額基準。
  - **盲點防護 (Thousand Separators)**：滿額金額（如 831,700）必須導入支援千分位 Mask 的 Input 元件，防範輸入錯誤。
  - **盲點防護 (預設值刪除保護)**：當判斷當前載入為 `default` 時，主動隱藏 Delete 按鈕。
- **頁面整合 (`page.tsx`)**：將 `PensionRuleConfigPanel` 加入到現有的系統設定 Tabs 介面中（例如放在 Tax Rule Config 旁邊），確保 API 呼叫互不干擾。

## 執行進度 (Progress)
- **2026-09-17 階段一完成**：
  - 已建立 `PensionRuleConfig` Entity (繼承 `AuditableEntity`)。
  - 已建立 `PensionRuleDefaultProperties` 與對應的測試，將 `finance-hub.pension-rule.max-basic-pension-amount` 預設值設為令和7年度的 `831700`。
  - 已建立 `PensionRuleConfigFactory` 處理屬性到實體的轉換。
  - 已更新 `application.properties`、`.env.base`、`.env.dev`、`.env.prod`，全面補齊 `831700` 的預設環境變數。
- **2026-09-17 階段二完成**：
  - 已建立 `PensionRuleConfigRepository` 介面。
  - 已建立 Service 層介面 `PensionRuleConfigService`、實作 `PensionRuleConfigServiceImpl` 及完整的單元測試。
  - 已建立 Service 層專用的 DTOs (`Result`、`Command`、`Data`)。
  - 已建立 `PensionRuleConfigController` 及完整的 CRUD 端點。
  - 已建立 Controller 層專用的 DTOs (`Request`、`Response`) 及 `PensionRuleConfigMapper` (MapStruct)。
  - 已建立 `PensionRuleConfigControllerTest` 完成 Controller 層的單元測試。
- **2026-09-17 階段三完成**：
  - 已建立 `PensionConfigDto` (不可變 DTO) 作為計算機與 Repo 的隔離層。
  - 已更新 `SimulationContext` 加入 `pensionConfig` 屬性與複製邏輯。
  - 已更新 `SimulationContextFactory`，在初始化時呼叫 `PensionRuleConfigService` 取得最新啟用設定，轉為 DTO 並注入 Context。
  - 已完成相關依賴的測試修正 (`SimulationContextFactoryTest`, `AssetSimulationEngineImplTest`)，確保編譯與單元測試順利通過。
- **2026-09-17 階段四完成**：
  - 已更新 `PensionCalculator`，徹底移除寫死的 `MAX_BASIC_PENSION_AMOUNT` 常數，改由傳入的 `PensionConfigDto` 參數動態取得滿額基準。
  - 已更新 `PensionProjectionServiceImpl`，注入 `PensionRuleConfigService` 以取得系統目前啟用的年金設定，並轉成 DTO 供推算時使用。
  - 已更新 `YearlySimulationPipeline`，於退休階段的計算 (`applyRetireeTaxes`) 中補上 `context.getPensionConfig()`。
  - 已完成相關單元測試的 Mock 修正 (`PensionProjectionServiceTest`)。
- **2026-09-17 階段五完成**：
  - 已建立自訂 React Hook `use-pension-rule-configs.ts`，實作完整的 CRUD 與 Cache Invalidation 機制。
  - 已建立表單元件 `pension-rule-config-panel.tsx`，並為 `maxBasicPensionAmount` 加上 `ThousandSeparatorInput` 防護，同時實作 `default` 設定檔的刪除防護。
  - 已於 `app/finance/configs/general/page.tsx` 中整合新增的 Pension Rules 分頁標籤與元件。
