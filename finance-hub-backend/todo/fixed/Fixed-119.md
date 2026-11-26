# [Fixed] Question 119：扶養親屬所得門檻寫死¥1,780,000，對不上2026年9月現在的官方法定門檻

## 背景
系統目前把「扶養親屬所得門檻」寫死成¥1,780,000（`finance-hub/finance-hub-backend/hub/src/main/resources/application.properties:109`，`application-h2.properties:118` 同步設定）。這個數字由 `DependentDeductionEngine.java:57-60, 88-91` 讀取，用來判斷扶養親屬（例如就讀大學的小孩、退休的長輩）當年度預估所得是否還在門檻內、可以套用扶養扣除額；超過門檻就直接歸零，沒有任何過渡處理。

已經向日本國稅廳官方頁面（`No.1180 扶養控除`）查證，這個門檻本身這幾年一直在滾動調整：

| 適用年度 | 合計所得金額門檻 | 純薪資收入等價門檻 |
|---|---|---|
| 令和2年～令和6年 | 480,000円 | 1,030,000円 |
| 令和7年（現行，即2026年9月現在適用） | 580,000円 | 1,230,000円 |
| 令和8年起 | 620,000円 | （尚未查到對應的薪資等價數字） |

不管拿哪一年、拿「合計所得金額」還是「純薪資收入等價」這兩種寫法去對照，2026年9月現在都對不上系統目前寫死的1,780,000——這個數字比現行任何一種對照方式都高出至少550,000。前端畫面上這個欄位標示為「Estimated Annual Income (¥)」，範例填寫「1,000,000」，看起來是設計成要跟「純薪資收入等價」門檻比較，而不是跟「合計所得金額」比較。

## 不確定的地方
1. 這個門檻本身幾乎每年都在調整（且令和8年12月起還會再變一次），過去也已經記錄過類似情況（住民稅基礎控除是否跟著調整——已判定WONTFIX，記錄在`todo/wontfix`資料夾裡；所得稅基礎控除分級細節——見`question-118`）——這類「維護速度追不上滾動修法」的落差，跟「程式邏輯本身寫錯、憑空多算或少算」的bug性質不同，是否值得投入時間追著每次修法更新，還是維持現狀等之後有需要再處理，交由使用者自行判斷。
2. 就算決定要修正，這個欄位的設計意圖到底是要跟「純薪資收入等價」（現行1,230,000）比較，還是「合計所得金額」（現行580,000）比較，需要先確認清楚——如果選錯對照基準，改了數字也不會是正確答案。

## 具體影響情境（供判斷是否值得處理時參考）
使用者有一位扶養親屬（例如就讀大學、有打工收入的小孩），當年度打工收入為¥1,500,000。真實法律下這個收入已經超過現行1,230,000的門檻，理論上不再符合完整扶養親屬資格；但系統因為門檻寫死在1,780,000，會誤判這位親屬仍然完全符合資格，給予¥380,000~630,000（依年齡分類）的完整扣除額，讓這位使用者的稅金被系統性低估。

## 需要使用者確認的問題
1. 是否要修正這個門檻？還是維持現狀，之後有實際用到扶養親屬扣除、且金額接近門檻的情境再處理？
2. 如果決定要修正，這個欄位該對照「純薪資收入等價」（1,230,000）還是「合計所得金額」（580,000）？

---

## 覆核結果與查證分析（Review & Answers to Claude's Questions）

已直接查核日本國稅廳（NTA）官方發布之最新稅務規章（[No.1180 扶養控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1180.htm) 與 [No.1410 給与所得控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1410.htm)，法令依據：所得稅法第2條、第84條、第85條、租稅特別措置法第41條之16，令和8年4月1日現在法令等），事實查證與架構分析如下：

### 一、國稅廳官方原文查證與數值補齊

經直接核對日本國稅廳公布之最新所得稅法規（No.1180 與 No.1410），補齊 Claude 於第 12 行未查得之令和8年薪資等價數字，完整法定對照表如下：

#### 扶養親屬資格所得門檻法定標準表（國稅廳 No.1180 & No.1410）

| 適用年度 | 合計所得金額門檻（法定母法標準） | 最低給與所得控除 | 純薪資收入等價門檻（打工年薪上限） | 說明 |
|:---|:---:|:---:|:---:|:---|
| **令和2年～令和6年** | 480,000円 | 550,000円 | **1,030,000円** | 歷史上著名的「103 万円の壁」 |
| **令和7年（2025年）** | 580,000円 | 650,000円 | **1,230,000円** | 現行過渡期門檻（123 万円の壁） |
| **令和8年～令和9年（2026年12月施行）** | 620,000円 | 740,000円（本則69萬+特例5萬） | **1,360,000円** | 令和8年度稅改新門檻（136 万円の壁，時限特例期） |
| **令和10年起（2028年起，本則）** | 620,000円 | 690,000円（回歸本則） | **1,310,000円** | 特例落日後回歸之恒久本則門檻 |

> **公式與法律構造**：
> 1. **法定母法基準**：所得稅法第2條第1項第34號明定，扶養親族資格之唯一法定標準為「年間の合計所得金額が62万円以下」（令和8年12月1日施行，適用令和8年分及以後）。
> 2. **純薪資等價換算**：`純薪資收入等價門檻 = 合計所得金額門檻 + 最低給與所得控除`。
>    依國稅廳 No.1410，給與收入 220 萬以下最低給與所得控除額為 74 萬（本則 69 萬＋租稅特別措置法特例加計 5 萬，適用於令和8年分及令和9年分）。兩者相加即為法定親屬薪資上限：`62萬 + 74萬 = 136 萬日圓`。
> 3. **非受薪親屬之法規區分**：
>    上述 136 萬僅適用於「純薪資所得（打工/兼職）」之親屬。若親屬為**領取公的年金之退休長輩（老人扶養親族）**，法定扣除為「公的年金等控除」（65歲以上為 110 萬，65歲未満為 60 萬），其法定免稅扶養年金上限實為 `62萬 + 110萬 = 172 萬日圓`（65歲以上）。

---

### 二、「178 萬」的由來與根本誤解剖析

**核心發現：系統內的 1,780,000 並非單純的滾動修法時間差，而是概念誤植（概念混淆）！**

1. **什麼是 178 萬？**
   - 178 萬是日本 2026 年（令和8年）稅改後，**「受薪者本人（納税者本人）」的所得稅非課稅門檻**（基礎控除 104萬 [本則62萬+特例42萬] + 給與所得控除 74萬 [本則69萬+特例5萬] = 178 萬）。亦即受薪納稅人本人年薪在 178 萬以下時，其個人應納所得稅額為 0。
2. **為什麼不能當作扶養親屬門檻？**
   - 稅法中「受薪者本人免稅」與「能否被父母或配偶列為扶養親屬」是完全獨立的兩套要件。
   - 日本稅制改革中，日本國會**從未將「扶養親屬資格上限」提高到 178 萬**，而是嚴格訂定在合計所得 62 萬（受薪等價 136 萬 / 令和10年落日後 131 萬）。
   - 在先前 `Fixed-102` 的修訂中，開發者在調整 2026 稅改參數時，誤將「本人免稅上限（178萬）」當作「扶養親屬所得門檻（`dependentIncomeThreshold`）」，因此將該參數寫成了 `1780000`。

---

### 三、針對 Claude 所提問題的明確答覆

#### 問題 2：這個欄位該對照「純薪資收入等價」還是「合計所得金額」？
- **明確回答：必須對照「純薪資收入等價」！**
- **理由與實務分析**：
  1. **前端使用者認知**：前端畫面（`estimatedAnnualIncome`）標示為「Estimated Annual Income (¥)」，提示範例為 `1,000,000`。一般受薪家庭輸入親屬（如打工的大學生小孩）時，直覺填寫的是「打工毛收入（Gross Wage）」，絕不會自行計算扣除額後填寫合計所得金額。
  2. **計算引擎比對邏輯**：在 [DependentDeductionEngine.java:55-58](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java#L55-L58) 中，程式碼直接取 `dependent.getEstimatedAnnualIncome()` 與 `threshold` 比較。若將門檻設為合計所得金額（62萬），一位正常打工賺 100 萬的小孩（合法符合扶養）會因為 `100萬 > 62萬` 被系統**全面誤殺取消扶養扣除**，嚴重高估納稅人稅金。
  3. **結論與適用邊界**：比對基準必須是**「純薪資收入等價門檻」**（令和7年為 123萬，令和8～9年為 136萬）。
     - *重要法規邊界備忘*：此衍生門檻專門適用於打工受薪親屬。依所得稅法第2條第1項第34號，**配偶依法排除於扶養親屬之外**（專屬適用「配偶者控除」）；領取年金之退休長輩法定上限則為 172 萬，此為單一純量門檻之業務邊界。

#### 問題 1：是否要修正這個門檻？建議如何處理？
- **明確建議：建議修正！**
- **理由**：
  1. **本質為 Bug**：178 萬並非真實法律數字，而是將「本人免稅額」誤植為「扶養資格門檻」，會導致打工年收 136萬~178萬的親屬被誤判享有 38萬~63萬的扣除額。
  2. **修改成本趨近於零**：該數值已在 `Fixed-62` 中參數化，收納於 `application.properties`、`application-h2.properties` 與 `TaxRuleConfig` 實體中，**完全不需要修改任何 Java 計算程式碼，亦不需要改動資料庫 Schema**。
  3. **建議設定值**：
     - 為與系統現行 `Fixed-102` 所採納之 2026 年（令和8年）稅改時間線（給與所得控除 220萬分段、74萬起徵扣除額）保持架構一致，**建議將設定值修正為 `1,360,000`（136 萬円）**。
     - *時效性備忘*：136 萬適用於令和8年及令和9年（2026～2027年）；令和10年（2028年）起若 5 萬加計特例落日回歸本則（131 萬），後續若進行時序多版本策略重構時可再行平滑銜接。

---

### 四、兩種處理解法選項與優缺點評估（供 PM / Scrum Master 決策）

針對本扶養親屬所得門檻之問題，系統架構層面存在以下兩種獨立的處理解法選項（二選一）。以下客觀列出兩者之法規背景、具體做法、優點與代價/局限，供產品經理（PM）與團隊評估決策：

#### 1. 法規背景與時序構造
在評估架構前，先釐清令和8年度（2026年）稅改之法令構造特徵：
- **恒久本則**：合計所得金額門檻定為 62 萬，最低給與所得控除定為 69 萬（等價薪資門檻為 **131 萬**）。
- **時限特例**：依租稅特別措置法，令和8年分及令和9年分最低給與所得控除加計 5 萬至 74 萬（等價薪資門檻為 **136 萬**），為期 2 年。
- **特定親屬階梯**：新法針對 19～22 歲大學生親屬創設了「136萬～159萬全額扣除、159萬～197萬階梯遞減」之緩衝機制（詳見 Question 121）。
- **老人年金控除**：65 歲以上退休長輩適用公的年金等控除（110 萬），其法定免稅年金門檻為 172 萬。

#### 2. 解法選項深度評估

##### 【解法一：設定檔參數校正】
- **具體做法**：
  維持現行 `Fixed-62` 的純量設定架構，僅將設定檔（`application.properties`、`application-h2.properties`、`.env.*`）中誤植的數值修正為 **`1,360,000`**。
- **優點**：
  1. **零程式碼異動風險**：完全不需修改任何 Java 計算程式碼，亦不改動資料庫 Schema。
  2. **立即修復主要 Bug**：立即排除打工年收 136 萬～178 萬親屬被錯誤給予扣除的重大逃漏稅與低估問題。
  3. **當期精確度高**：針對 2026～2027 年（令和8～9年）純打工受薪親屬，計算結果完全符合官方現行規定。
- **缺點與局限**：
  1. **落日條款時序偏差**：令和10年（2028年）特例落日回歸 131 萬後，35 年長線模擬後續年份存在 5 萬日圓之門檻偏差。
  2. **無法涵蓋特殊親屬**：單一純量無法自動區分領取公的年金之退休長輩（法定免稅年金上限為 172 萬），亦無法表達 Question 121 之大學生階梯緩衝。
  3. **前端需配套更新**：前端稅務管理面板（`tax-rule-config-panel.tsx`）與 Zod schema 需將預設值同步校正為 136 萬。

##### 【解法二：策略模式重構（`DependentDeductionStrategy`）】
- **具體做法**：
  將門檻判定從 `TaxRuleConfig` 純量設定中移除（包含後端 Entity、DTO、DB 欄位與前端管理面板），改由後端計算策略依模擬年度（2026-2027: 136萬 vs 2028+: 131萬）、親屬類別（一般親屬、老人年金 172萬、特定親屬階梯）動態判定。
- **優點**：
  1. **法規完整度高**：精確表達時序演變與落日條款，消除 35 年長線模擬中 2028 年後的 5 萬日圓偏差。
  2. **原生支援階梯機制**：可直接無縫支援令和8年大學生（特定親屬）「136萬～159萬全額緩衝、159萬～197萬階梯遞減」之平緩過渡機制（Question 121）。
  3. **多所得型態精確計算**：能分別對打工薪資（給與控除）與退休長輩（公的年金等控除 110 萬）套用正確公式，避免單一純量誤殺。
- **缺點與成本**：
  1. **架構破壞性變更（Breaking Change）**：需修改後端 Domain Entity、DB Schema、DTO、API Controller。
  2. **前端連鎖修改**：前端管理面板（`tax-rule-config-panel.tsx`）需同步刪除該欄位與對應驗證。
  3. **工時與測試成本較高**：需重構 `DependentDeductionEngine` 並更新所有相關單元測試與模擬整合測試。

#### 3. 方案綜合對照表

| 評估維度 | 解法一：設定檔參數校正 | 解法二：策略模式重構 |
|:---|:---|:---|
| **修改範疇** | 僅環境變數與設定檔（零程式碼異動） | 後端 Domain、Engine、API、DB Schema 及前端 Panel |
| **短期精確度 (2026~2027)** | 純薪資打工族 100% 精確 | 全親屬、全收入類型 100% 精確 |
| **長線精確度 (2028~2060)** | 存在 5 萬日圓落日偏差（136萬 vs 131萬） | 100% 精確（自動落日回歸 131 萬） |
| **Question 121 階梯支援度** | 不支援（單一門檻二元判定，超過歸零） | 原生支援（可表達階梯緩衝） |
| **退休長輩年金支援度** | 不支援（視同受薪者，高於 136 萬即被排除） | 原生支援（按 172 萬年金門檻計算） |
| **實作成本與風險** | 極低（修改成本趨近於零） | 較高（涉及多模組 Breaking Change） |

---

## 產品負責人（Product Manager）裁定與最終結論

> **PM 裁定（2026-10-07）**：
> 經產品負責人（Product Owner）與 Scrum 團隊共同評估上述法規查證與架構選項之優缺點，**本項目正式定案採納「解法二：策略模式重構（`DependentDeductionStrategy`）」**。
>
> **裁定理由與架構考量**：
> 1. **稅法精確度與長線模擬要求**：本系統核心目標為支援 30～40 年之買房防破產長線模擬，無法接受 2028 年（令和10年）特例落日回歸 131 萬後持續產生之 5 萬日圓系統性偏差。
> 2. **原生整合特定親屬與多元所得**：解法二能直接承接 Question 121 所規範之大學生特定扶養親屬階梯緩衝機制（136萬～159萬滿額、159萬～197萬階梯遞減），並精確支援退休長輩公的年金控除（172 萬門檻），徹底消除單一純量門檻造成的誤殺。
> 3. **架構標準化**：全面對齊系統既有之基礎控除（Question 118）與配偶控除（Question 120）之策略模式體系，將稅務計算責任完全收攏至領域計算策略，達成系統架構之一致性。
>
> 後續依此裁定展開策略模式之規格設計與實作計畫。

---

## 五、修正實作規格與多階段執行計畫（Implementation Plan）

### 1. 影響範圍評估（Blast Radius）

- **階段一與階段二（Phase 1 & 2）：完全向前相容（Zero Impact）**：
  - 資料庫結構維持不變（`TaxRuleConfig` Entity 暫時保留欄位以相容既有 DB Table Schema）
  - 前端 UI 與 API Controller/DTO 契約維持相容
  - `TaxRuleConfig` 實體保留欄位作為相容備援，計算引擎先行解除對其依賴
  - `ActiveTaxCalculationEngine` 外部呼叫簽章維持完全相容
- **階段三（Phase 3）：受控收斂清理（Clean-up & Purge）**：
  - 資料庫結構：自 Entity 移除 `@Column(name = "dependent_income_threshold")`（H2 記憶體資料庫自動重建生效；實體 DB 執行 `ALTER TABLE tax_rule_config DROP COLUMN dependent_income_threshold;` 或由 Hibernate 自動同步）
  - 後端架構：自 Entity、DTO、Controller、Properties 與環境變數徹底移除該欄位
  - 前端 UI（`hub-ui`）：自 `tax-rule-config-panel.tsx` 與 Zod schema 移除該設定項
- **新建立套件**：
  `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.dependent`
- **新增檔案清單（共 6 個檔案）**：
  1. `DependentDeductionStrategy.java`（介面：定義所得稅與住民稅扶養扣除額計算方法簽章，傳入 `Dependent` 與 `LocalDate simulationYearEnd`）
  2. `LegacyDependentDeductionStrategy.java`（2024 年以前所得舊制策略：薪資門檻 103 萬、所得門檻 48 萬、老人年金門檻 158 萬）
  3. `Reiwa7DependentDeductionStrategy.java`（2025 年所得過渡策略：薪資門檻 123 萬、所得門檻 58 萬、老人年金門檻 168 萬，兼負責 2026 年度住民稅）
  4. `Reiwa8And9DependentDeductionStrategy.java`（2026～2027 年時限特例策略：薪資門檻 136 萬、所得門檻 62 萬、老人年金門檻 172 萬）
  5. `Reiwa10OnwardsDependentDeductionStrategy.java`（2028 年起本則落日策略：薪資門檻 131 萬、所得門檻 62 萬、老人年金門檻 172 萬）
  6. `DependentDeductionStrategyFactory.java`（時序派發工廠：支援 `getIncomeTaxStrategy(year)` 與 `getInhabitantTaxStrategy(year)`，處理所得稅現年 vs 住民稅前年所得課稅之跨期時差分派）
- **修改檔案清單（跨階段彙整）**：
  1. `DependentDeductionEngine.java`（Phase 2：引入 `DependentDeductionStrategyFactory` 委託計算）
  2. `TaxRuleConfig.java` 及相關 DTO / Controller / Properties（Phase 3：徹底拔除純量設定欄位）
  3. 前端 `tax-rule-config-panel.tsx`、`use-tax-rule-configs.ts`（Phase 3：刪除 UI 欄位與驗證）

---

### 2. 避免上下文過載之三階段執行計畫（Three-Phase Execution Plan）

為避免在單一任務對話中同時進行「大量代碼生成 + 複雜引擎修改 + 多檔案測試校準 + 跨前後端清理」導致 Context Window 膨脹引發數值幻覺與非預期連鎖錯誤，嚴格拆分為三個獨立階段推進：

* **階段一（Phase 1）：策略模型、工廠與專屬單元測試（純新增，零破壞性）**
  - **任務**：
    1. 在 `strategy.tax.deduction.dependent` 套件下建立 `DependentDeductionStrategy` 介面、各時代 Strategy 實作類別（Legacy、Reiwa7、Reiwa8And9、Reiwa10Onwards）以及 `DependentDeductionStrategyFactory`。
    2. 建立 `DependentDeductionStrategyTest.java`，撰寫針對：
       - 各時代受薪親屬薪資門檻邊界（103萬、123萬、136萬、131萬）。
       - 65 歲以上長輩公的年金扣除門檻（158萬、168萬、172萬）。
       - 一般親屬（38萬/33萬）、特定親屬（63萬/45萬）、老人親屬（48萬/38萬）各級距之所得稅與住民稅法定金額。
       - 工廠跨期時差分派：2026 首年所得稅走 Reiwa 8-9、住民稅走 Reiwa 7；2027 年起住民稅切換至 Reiwa 8-9；2028 年所得稅切換至 Reiwa 10 Onwards 等完整單元測試。
  - **安全邊界**：本階段完全為新增獨立類別，**零破壞性**，完全不影響現有引擎運作。

* **階段二（Phase 2）：計算引擎整合委派與既有測試校準（整合與驗證）**
  - **任務**：
    1. 修改 `DependentDeductionEngine.java`，注入 `DependentDeductionStrategyFactory`（保留零參數建構子 `this(new DependentDeductionStrategyFactory())`），將所得稅與住民稅之計算責任分別委派給對應 Strategy。
    2. 解除對 `TaxRuleConfig.dependentIncomeThreshold` 純量之硬性依賴。
    3. 校準既有整合測試（如 `ActiveTaxCalculationEngineTest.java`），確保全專案測試 100% 通過。
  - **安全邊界**：在階段一策略已 100% 測試通過的基礎上進行微創委派，改動量極小（小於 35 行），確保既有系統無縫切換至策略模式。

* **階段三（Phase 3）：徹底拔除廢棄設定值與前後端架構收斂（Clean-up & 徹底清理）**
  - **任務**：
    1. **資料庫層**：自 `TaxRuleConfig.java` 移除 `@Column(name = "dependent_income_threshold")` 映射；若使用實體 DB 可執行 `ALTER TABLE tax_rule_config DROP COLUMN dependent_income_threshold;`（開發環境 H2 自動重建生效）。
    2. **後端領域與 API 層**：
       - `TaxRuleConfig.java` 移除 `dependentIncomeThreshold` 欄位與建構子參數。
       - 移除 `TaxRuleDefaultProperties.java`、`TaxConfigDto.java`、`SimulationContextFactory.java` 中對應欄位。
       - 移除 `CreateTaxRuleConfigCommand`、`UpdateTaxRuleConfigCommand`、`TaxRuleConfigResult` 中對應欄位。
       - 移除 `CreateTaxRuleConfigRequest`、`UpdateTaxRuleConfigRequest`、`TaxRuleConfigResponse` 及 Controller 映射。
       - 清理 `application.properties`、`application-h2.properties` 與 `.env.*` 環境變數中的 `DEPENDENT_INCOME_THRESHOLD`。
    3. **前端介面層（`hub-ui`）**：
       - 自 `tax-rule-config-panel.tsx` 刪除 `Dependent Income Threshold` 標籤與輸入元件。
       - 自 `use-tax-rule-configs.ts` 的 Zod schema 中移除 `dependentIncomeThreshold` 欄位與預設值。
    4. **測試套件清理**：
       - 更新 `TaxRuleDefaultPropertiesTest`、`TaxRuleConfigServiceImplTest`、`TaxRuleConfigControllerTest`、`SimulationContextFactoryTest`，移除已廢棄欄位之 mock 與 assertion。
  - **安全邊界**：在階段二計算引擎已完全不再讀取純量設定值的保障下進行清理，徹底杜絕死代碼（Dead Code）與概念混淆，完成系統架構之徹底純淨化。

---

### 3. 實作執行記錄（Execution Record）

- **Phase 1 執行狀態**：✅ 已完成（2026-10-07）
  - 新建套件：`ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.dependent`
  - 新建介面、抽象類別與實作：
    - [DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategy.java)（策略介面，定義雙稅簽章與門檻查詢方法）
    - [AbstractDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java)（抽象基底類別，封裝年齡級距判斷與長輩年金/受薪資格檢核）
    - [LegacyDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/LegacyDependentDeductionStrategy.java)（令和6年以前：薪資 103 萬、所得 48 萬、老人年金 158 萬）
    - [Reiwa7DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java)（令和7年：薪資 123 萬、所得 58 萬、老人年金 168 萬）
    - [Reiwa8And9DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java)（令和8~9年特例：薪資 136 萬、所得 62 萬、老人年金 172 萬）
    - [Reiwa10OnwardsDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java)（令和10年起落日：薪資 131 萬、所得 62 萬、老人年金 172 萬）
    - [DependentDeductionStrategyFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyFactory.java)（雙軌時差工廠：現年所得稅分派 vs 前年所得住民稅分派）
  - 新建專屬單元測試：
    - [DependentDeductionStrategyTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java)（涵蓋工廠時差派發、法定門檻常數、年齡分類級距、受薪與長輩年金邊界、配偶排除等完整測試案例）
- **Phase 2 執行狀態**：✅ 已完成（2026-10-07）
  - 修改計算引擎：
    - [DependentDeductionEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java)：注入 `DependentDeductionStrategyFactory`，保留零參數建構子以相容既有呼叫，徹底解除對 `TaxRuleConfig.dependentIncomeThreshold` 純量之依賴，所得稅與住民稅計算全面委派給時差策略工廠。
  - 新建引擎單元測試：
    - [DependentDeductionEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java)：驗證引擎委派機制、2026 首年雙稅時差分離（所得稅採 Reiwa 8-9 136 萬門檻、住民稅採 Reiwa 7 123 萬門檻）、舊制 2024 邊界、以及長輩公的年金 172 萬門檻。
  - 既有測試相容性：
    - [ActiveTaxCalculationEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：零破壞性切換，維持 100% 相容。
- **Phase 3 執行狀態**：✅ 已完成（2026-10-07）
  - **資料庫層與領域模型**：
    - [TaxRuleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)：移除 `@Column(name = "dependent_income_threshold")` 欄位映射與建構子參數。
  - **後端應用層與設定層**：
    - [TaxRuleDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java)：移除 `dependentIncomeThreshold` 欄位與 getter/setter。
    - [TaxRuleConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java)：移除建立 TaxRuleConfig 時對 properties 門檻的呼叫。
    - [CreateTaxRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java) & [UpdateTaxRuleConfigCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)：移除命令物件中的對應欄位。
    - [TaxRuleConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：移除對應欄位與 Builder 屬性。
    - [TaxRuleConfigServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java)：建構與更新邏輯中徹底拔除該欄位。
    - [TaxConfigDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：移除 `dependentIncomeThreshold` 純量欄位。
    - [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：組裝 TaxConfigDto 時解除對純量門檻的傳遞。
  - **後端 Web 控制器與 DTO 層**：
    - [CreateTaxRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)
    - [UpdateTaxRuleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)
    - [TaxRuleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)
    - [TaxRuleConfigController.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java)：解除對 Request 門檻欄位的綁定與 Command 建立。
  - **環境變數與 Properties 拔除**：
    - [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)
    - [application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)
    - [.env.base](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)
    - [.env.dev](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)
    - [.env.prod](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)
  - **前端 UI 層（`hub-ui`）**：
    - [use-tax-rule-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts)：Zod schema 與 defaultValues 移除 `dependentIncomeThreshold`。
    - [tax-rule-config-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx)：表單 defaultValues 與 UI 輸入元件徹底移除。
  - **測試套件清理與校準**：
    - [TaxRuleDefaultPropertiesTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)
    - [TaxRuleConfigServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java)
    - [SimulationContextFactoryTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
    - [AssetSimulationEngineImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)
    - [TaxRuleConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java)
    - [ActiveTaxCalculationEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)




