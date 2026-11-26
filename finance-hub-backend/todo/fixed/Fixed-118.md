# [Fixed] Question 118：基礎控除在新的「稅制設定」架構裡，仍然只能設定成單一固定數字，沒辦法依所得高低分級——這個落差是否值得處理

## 背景
在`fixed`資料夾裡有一份較早的修正記錄（原始編號寫成Issue-36，這份文件寫成時的舊編號，之後fixed資料夾整體重新編號過，這裡不追蹤具體新編號，只描述內容方便對照），最初提出的問題之一，就是「基礎控除」寫死480,000元，不論所得高低都用同一個金額，但真實法律從令和7年度（2025年度）起，已經改成依所得高低分級的金額（所得越高、基礎控除越低，超過一定所得甚至會直接歸零），令和8-9年度還有進一步調整。

那份修正最後採用的方向（方向A與方向C混合），把基礎控除、薪資所得扣除級距、所得稅率級距、住民稅率級距，統一改成「系統管理員可以在畫面上設定的數字」，不用改程式碼、重新部署。這個方向對「數字每年會變、需要有人更新」這一層問題確實是有效的解法。

但重新核對目前實際的資料結構（[TaxRuleConfig.java:39-50](finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java:39)）後發現：薪資所得扣除、所得稅率、住民稅率這三項，都已經改成「依級距分段設定不同數字」的表格結構（`TaxBracket`，包含級距上限、對應數字等欄位，可以有任意多段）；但「基礎控除」（`basicDeductionIncomeTax`、`basicDeductionInhabitantTax`）這兩項，仍然各自只是單一固定數字的欄位，不是像其他三項一樣的級距表結構。

也就是說，即使系統管理員之後每年都認真更新這兩個數字，這個數字本身仍然只能是「所有人都用同一個金額」，沒有辦法表達「所得在某個範圍以下用一個金額、超過某個範圍用另一個金額、超過更高範圍歸零」這種依所得高低分級的規則——而這正是上述那份較早記錄最初指出的、真實法律已經改變的那個特性本身。

## 不確定的地方
我不確定的是：真實法律裡「基礎控除依所得分級」這個規則，對這個系統實際的模擬使用情境而言，落差影響有多大、是否值得為此把基礎控除也改成跟薪資所得扣除一樣的級距表結構。

## 補充：已查證到的具體分級數字（2026-09-30 更新）
原本上面第1點提到「沒有查到足夠精確的官方資料」——這次已經透過nta.go.jp官方頁面（`No.1199 基礎控除`）逐字核對到令和7年度與令和8年度／令和9年度以後的完整分級表，抄錄如下：

**令和7年度（2025年度）**
| 合計所得金額 | 控除額 |
|---|---|
| 132萬円以下 | 95萬円 |
| 132萬円超 336萬円以下 | 88萬円 |
| 336萬円超 489萬円以下 | 68萬円 |
| 489萬円超 655萬円以下 | 63萬円 |
| 655萬円超 2,350萬円以下 | 58萬円 |
| 2,350萬円超 2,400萬円以下 | 48萬円 |
| 2,400萬円超 2,450萬円以下 | 32萬円 |
| 2,450萬円超 2,500萬円以下 | 16萬円 |
| 2,500萬円超 | 0円 |

**令和8年度／令和9年度以後（2026年12月1日起施行）**
| 合計所得金額 | 控除額 |
|---|---|
| 132萬円以下 | 104萬円 |
| 132萬円超 655萬円以下 | 62萬円（其中489萬円超655萬円以下這一段官方頁面另列67萬円，其餘皆為62萬円） |
| 655萬円超 2,350萬円以下 | 62萬円 |
| 2,350萬円超 2,400萬円以下 | 48萬円 |
| 2,400萬円超 2,450萬円以下 | 32萬円 |
| 2,450萬円超 2,500萬円以下 | 16萬円 |
| 2,500萬円超 | 0円 |

（489萬円超655萬円以下這一段的官方頁面呈現方式較不直觀，若真的要實作，建議直接重新核對官方原文頁面逐字確認這一段，不要只憑這裡的抄錄。）

系統目前寫死的所得稅基礎控除是480,000円（`ActiveTaxCalculationEngine.java:47`），對照上表可以看到：只有落在最低那一段（132萬円以下）的使用者，480,000這個數字才勉強接近（但仍偏低，令和7年度應為95萬円、令和8年度應為104萬円）；只要合計所得金額超過132萬円（這對一個評估「買房會不會現金破產」的一般所得使用者而言是極常見的所得範圍），目前寫死的480,000就會嚴重低估基礎控除，導致應稅所得被高估、稅金被高估——跟issue-102（薪資所得扣除多算19萬）的方向相反，這裡的方向是讓模擬結果「看起來比實際更悲觀」。

## 需要使用者確認的問題
1. 上面查到的分級數字，489萬円超655萬円以下這一段（令和8年度）官方頁面呈現方式不夠直觀，如果要真的動手實作，建議先重新核對官方原文確認清楚這一段，不要直接採信這裡的抄錄。
2. 是否要仿照薪資所得扣除的做法，把基礎控除也改成級距表結構（沿用現有的`TaxBracketType`機制，再加一個分類即可）？還是這個落差在目前的使用情境下影響可以接受，維持現狀（單一數字、每年手動更新）即可？這屬於設計取捨，這裡先如實記錄發現與查到的數字，不代為決定。

---

## 覆核結果與查證分析（Review & Answers to Claude's Questions）

已直接查核日本國稅廳（NTA）官方發布之最新稅務規章（[No.1199 基礎控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1199.htm)，法令依據：所得稅法第86條、租稅特別措置法第41條之16之2，令和8年4月1日現在法令等），並針對系統架構與業務落差進行精確覆核，事實與分析如下：

### 一、國稅廳官方原文查證與數值校正（Addressing Question 1）

經逐字比對國稅廳官方網頁原生 HTML 表格代碼（特別是表格內的 `rowspan` 跨列結構），Claude 先前抄錄的表格在**令和8・9年度**與**令和10年度以後**兩欄之間有混淆。以下為官方最權威且完整的基礎控除法定分級表：

#### 所得稅基礎控除額法定分級表（國稅廳 No.1199 官方原文核對）

| 納税者本人の合計所得金額 | 令和6年分以前 | 令和7年分（2025年） | 令和8・9年分（2026年12月1日施行） | 令和10年分以後 |
|:---|:---:|:---:|:---:|:---:|
| **132万円以下** | 48万円 | **95万円** | **104万円** | **99万円** |
| **132万円超 336万円以下** | 48万円 | **88万円** | **104万円** | **62万円** |
| **336万円超 489万円以下** | 48万円 | **68万円** | **104万円** | **62万円** |
| **489万円超 655万円以下** | 48万円 | **63万円** | **67万円** | **62万円** |
| **655万円超 2,350万円以下** | 48万円 | **58万円** | **62万円** | **62万円** |
| **2,350万円超 2,400万円以下** | 48万円 | 48万円 | 48万円 | 48万円 |
| **2,400万円超 2,450万円以下** | 32万円 | 32万円 | 32万円 | 32万円 |
| **2,450万円超 2,500万円以下** | 16万円 | 16万円 | 16万円 | 16万円 |
| **2,500万円超** | 0円 | 0円 | 0円 | 0円 |

> **關鍵校正說明（針對 Claude 第 42 行疑惑的解答）**：
> 1. 官方原生代碼中，令和8・9年分的 `104万円` 設有 `rowspan="3"`，意味著 **132萬以下、132~336萬、336~489萬 這前三個級距均為 104万円**；隨後的 489~655萬 為 **67万円**、655~2,350萬 為 **62万円**。
> 2. 之前 Claude 抄錄將 132~655萬誤標為「62萬（489~655萬另列67萬）」，是因為將「令和10年以後（第 2~5 級距一律 62萬）」的跨列結構誤讀到了令和8年分。

#### 住民稅基礎控除額之法定現狀
依據現行地方稅法，個人住民稅基礎控除**未跟進國稅這波新法調升**，一般受薪基準額仍維持 43 萬円。本 Question-118 專注解決「所得稅基礎控除新法階梯」；至於住民稅原本既有的 2,400 萬以上超額遞減階梯與前年課稅時差，屬於系統既有常數寫死之獨立架構問題，**已移交 [`Plan-16`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/planned/Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md) 獨立立案排程實作**，不在本 Issue 之新法查證範圍內。

---

### 二、模擬落差之定量敏感度分析（Addressing Question 2: 是否值得改為級距表）

評估「買房/現金流破產模擬」的核心使用情境中，是否值得為此將基礎控除擴展為級距表：

1. **方案評估之敏感度情境算例（以 600 萬～1,500 萬典型購屋者為例）**：
   - 以日本購屋受薪階級年收入約 600萬 ~ 1,500萬日圓（合計所得約在 436萬 ~ 1,305萬日圓區間）進行方案決策之敏感度評估：
     - 該區間在**令和7年分**對應之基礎控除為 **58萬 ~ 68萬**。
     - 在**令和8・9年分**對應之基礎控除為 **62萬 ~ 104萬**（年收約 665.6萬以上為 62萬~67萬；年收在約 665.6萬以下（含 600萬與 660萬）因合計所得在489萬以下，享有 104萬特例）。
     - 在**令和10年分以後**對應之基礎控除為 **62萬**。
   - 系統現行單一固定值為 **48萬**，與真實扣除額：
     - 在令和10年後相差約 **14萬日圓**，以邊際稅率 10% ~ 20% 換算，每年實際所得稅的估算差額約 **14,000円 ~ 28,000円**（平均每月約 1,100 ~ 2,300日圓）。
     - 在令和8・9年特例期，對於 600萬年收族群差額較大（約 56萬日圓，稅額差約 5.6萬 ~ 11.2萬日圓）。
   - *(註：上述僅為評估改固定值或改策略模式之敏感情境算例；最終方案 B 程式架構完整涵蓋 0 円至 2,500 萬以上高薪族群之全所得階梯)*。

2. **現行寫死 48 萬之偏差方向評估**：
   - 既有代碼少算扣除額，使預估稅金偏高（現金流偏悲觀）；但對高所得者仍存在邊界失真。
   - 質化安全邊際不能取代計算精確度，因此方案 A 屬於暫緩技術債，必須採納方案 B 徹底消除各所得區間之偏差。

3. **架構影響評估（若決定升級為級距表）**：
   - 若要支持分級，需進行以下改動：
     - **Domain 層**：`TaxBracketType` 增加 `BASIC_DEDUCTION_INCOME_TAX`（及可選的 `BASIC_DEDUCTION_INHABITANT_TAX`）。
     - **計算引擎**：`ActiveTaxCalculationEngine` 需先計算出 `employmentIncome`（合計所得金額），再以該金額查表取得對應的基礎控除額。
     - **DB / API / Frontend**：`TaxRuleConfig` 的資料結構、DTO、Flyway Migration、前端管理畫面皆需增加「基礎控除級距表」的表格輸入與編輯元件。

---

### 三、結論與建議處置方向

根據上述精確查證事實，提供產品負責人（User / PM）兩種決策方案之架構與優缺點評估：

- **方案 A（維持現有單一值架構，調整預設值為 620,000円）**：
  - **作法**：將 `TaxRuleConfig` 中的所得稅基礎控除預設值直接更新為 `620,000`（令和8年及令和10年後主要受薪區間的標準金額）。
  - **優點**：
    1. **改動極小**：僅需更新設定預設值，完全不需要動到 Domain、Entity、資料庫結構及前端 UI。
    2. **主力客群誤差極低**：主力購屋年收族群（600萬~1,500萬）在 2028 年後的所得稅計算誤差降至接近 0。
    3. **具備安全邊際**：在 2026~2027 年少算特例扣除額，使手頭現金流預估稍微偏保守，絕無導致買房破產的風險。
  - **缺點**：
    1. **無法表達時間軸動態演變**：無法在 35 年長線模擬中反映 2026~2027 年的時限特例（104 萬）與 2028 年後的落日回歸，長線時間維度失真。
    2. **缺乏所得階梯分級能力**：無法表達低所得（132萬以下）的大額扣除或超高所得（2,400萬以上）的階梯遞減歸零。
    3. **本質為暫緩技術債**：若未來要求高精度多情境模擬，仍需重新回頭翻修。

- **方案 B（導入策略模式 Strategy Pattern，依年份與所得動態計算）**：
  - **作法**：比照本專案已實作的房貸減稅住民稅上限模式（`InhabitantTaxLimitStrategy`），於 `hub-app` 建立純 Domain 的年份策略模式（`BasicDeductionStrategy`），依 `simulationYear` 與 `totalIncome` 動態查表（2025年過渡版、2026~2027年時限特例版、2028年以後恒久本則版）。
  - **優點**：
    1. **時間軸精準自適應**：在一次 35 年長線模擬中，前兩年自動享受 104 萬特例減稅，第三年起自動回歸恒久本則，完全零手動維護。
    2. **範圍純粹不擴散**：完全封裝在 `hub-app` 內部純計算層，不改 DB（免 Flyway）、不改 API DTO、不改前端 UI。
    3. **符合開閉原則（OCP）**：未來若日本國會再度修法，只需新增一個 Strategy 類別即可無縫擴充。
    4. **全所得區間零誤差**：精準對齊國稅廳官方 No.1199 表格，解決超高所得歸零與低所得非課稅邊界。
  - **缺點**：
    1. **規則寫入代碼（Code-based Rule）**：扣除額級距由 Java 類別管理，而非資料庫配置；若未來法規再度修訂，需要發布代碼版本更新（但因日本所得稅本則每數年甚至十年才修法一次，維護頻率極低）。
    2. **代碼量略增**：相較於方案 A 僅改一個數字，方案 B 需在 `hub-app` 新增 1 個介面、4 個 Strategy 實作類別與 1 個 Factory（約增加 150~200 行純 Java 代碼），並需撰寫專屬單元測試。

**備註**：本覆核已如實記錄官方精確數據與架構落差分析，最終取捨方向交由使用者（Product Manager）與 Claude 覆核裁定。

---

## 產品負責人（Product Manager）裁定與最終結論

> **PM 裁定（2026-10-04）**：
> **本項目正式定案採納「方案 B：策略模式（Strategy Pattern）」**。
>
> 考量本系統為 30~35 年長線資產與現金流模擬器，時間軸上的法定稅制變遷應具備動態自適應能力。導入策略模式能以極小且乾淨的代碼量（不影響 DB 與前端），一次解決 2026~2027 年特例大加碼與 2028 年後恒久退場的法規要求，長期投資報酬率極高。
>
> 後續直接依此架構建立對應計畫或進行實作，無需再進行架構方向爭辯。

---

## 四、修正實作計畫：導入 Strategy Pattern 動態計算基礎控除（方案 B 執行規格）

### 1. 目標概述
比照本專案已實作的房貸減稅住民稅上限模式（`InhabitantTaxLimitStrategy`），於 `hub-app` 建立純 Domain/Calculation 的年份策略模式（`BasicDeductionStrategy`），依 `simulationYear` 與合計所得金額（`employmentIncome` 給與所得，或 `miscellaneousIncome` 年金雜所得）動態查表計算所得稅基礎控除額。

### 2. 架構設計與改動清單

#### (1) 新增 Strategy 介面與實作類別（New Package）
**Package**: `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax`

- **[NEW] `BasicDeductionStrategy.java` (Interface)**
  ```java
  package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax;

  public interface BasicDeductionStrategy {
      /**
       * 依合計所得金額（給與所得/年金雜所得）計算所得稅基礎控除額
       * @param totalIncome 合計所得金額（円）
       * @return 基礎控除額（円）
       */
      long calculateIncomeTaxDeduction(long totalIncome);
  }
  ```

- **[NEW] `LegacyBasicDeductionStrategy.java`**
  - **適用年份**：$\le 2024$ 年（令和6年分以前）。
  - **級距邏輯**：
    - $\le 24,000,000$：$480,000$ 円
    - $\le 24,500,000$：$320,000$ 円
    - $\le 25,000,000$：$160,000$ 円
    - $> 25,000,000$：$0$ 円

- **[NEW] `Reiwa7BasicDeductionStrategy.java`**
  - **適用年份**：$2025$ 年（令和7年分過渡期）。
  - **級距邏輯**（依日本國稅廳 No.1199 官方表）：
    - $\le 1,320,000$：$950,000$ 円
    - $\le 3,360,000$：$880,000$ 円
    - $\le 4,890,000$：$680,000$ 円
    - $\le 6,550,000$：$630,000$ 円
    - $\le 23,500,000$：$580,000$ 円
    - $\le 24,000,000$：$480,000$ 円
    - $\le 24,500,000$：$320,000$ 円
    - $\le 25,000,000$：$160,000$ 円
    - $> 25,000,000$：$0$ 円

- **[NEW] `Reiwa8And9BasicDeductionStrategy.java`**
  - **適用年份**：$2026 \sim 2027$ 年（令和8・9年分特例）。
  - **級距邏輯**（已校正原生 HTML `rowspan="3"` 結構）：
    - $\le 4,890,000$：$1,040,000$ 円（含 132萬以下、132~336萬、336~489萬）
    - $\le 6,550,000$：$670,000$ 円
    - $\le 23,500,000$：$620,000$ 円
    - $\le 24,000,000$：$480,000$ 円
    - $\le 24,500,000$：$320,000$ 円
    - $\le 25,000,000$：$160,000$ 円
    - $> 25,000,000$：$0$ 円

- **[NEW] `Reiwa10OnwardsBasicDeductionStrategy.java`**
  - **適用年份**：$\ge 2028$ 年（令和10年分以後恒久本則）。
  - **級距邏輯**：
    - $\le 1,320,000$：$990,000$ 円
    - $\le 23,500,000$：$620,000$ 円（含 132~336萬、336~489萬、489~655萬、655~2,350萬）
    - $\le 24,000,000$：$480,000$ 円
    - $\le 24,500,000$：$320,000$ 円
    - $\le 25,000,000$：$160,000$ 円
    - $> 25,000,000$：$0$ 円

- **[NEW] `BasicDeductionStrategyFactory.java`**
  - 提供靜態路由或 Spring Component：
    - `year <= 2024` $\to$ `LegacyBasicDeductionStrategy`
    - `year == 2025` $\to$ `Reiwa7BasicDeductionStrategy`
    - `year >= 2026 && year <= 2027` $\to$ `Reiwa8And9BasicDeductionStrategy`
    - `year >= 2028` $\to$ `Reiwa10OnwardsBasicDeductionStrategy`

#### (2) 修改計算核心（Core Engine）
- **[MODIFY] `ActiveTaxCalculationEngine.java`**
  - 注入或引用 `BasicDeductionStrategyFactory`。
  - 在 `calculate()` 內部：
    - 計算給與所得：`employmentIncome = grossTotalSalary - employmentIncomeDeduction;`
    - 調用策略查表：
      ```java
      long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(simulationYear)
              .calculateIncomeTaxDeduction(employmentIncome);
      ```
  - 在 `calculatePensionTax()` 內部：
    - 計算年金所得：`miscellaneousIncome = Math.max(0, pensionIncome - publicPensionDeduction);`
    - 調用策略查表：
      ```java
      long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(context.getYear())
              .calculateIncomeTaxDeduction(miscellaneousIncome);
      ```

#### (3) 新增與更新測試（Unit Tests）
- **[NEW] `BasicDeductionStrategyTest.java`**
  - 針對 4 種 Strategy 測試所有臨界點收入（0, 1.32M, 3.36M, 4.89M, 6.55M, 23.5M, 24M, 24.5M, 25M, 30M）。
  - 針對 Factory 測試年份分支路由（2024, 2025, 2026, 2027, 2028, 2035）。
- **[MODIFY] `ActiveTaxCalculationEngineTest.java`**
  - 確保現有 2024 年基準測試完全相容通過。
  - 增加 2026 年（104萬/67萬/62萬特例）與 2028 年（62萬本則）的所得稅扣除整合測試。

### 3. 影響範圍與 Context Window 覆核
- **影響範圍**：
  - 資料庫 / Flyway / API DTO / Frontend UI / Pipeline 流程全部 **0 改動**。
  - 僅變更 `hub-app` 純計算層。
- **Context Window**：
  - 總變更代碼小於 400 行（約 2,500 ~ 3,500 tokens），遠在上下文容量與單次生成上限的安全裕度內。

---

## 五、實作完成與驗證報告（Implementation & Verification Report）

- **實作日期**：2026-10-05
- **實作者**：Antigravity Agent
- **審核者（待定）**：Claude 覆核、Product Manager / Scrum Master

### 1. 檔案變更總覽

| 變更類型 | 檔案路徑 | 說明 |
|:---|:---|:---|
| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategy.java` | 基礎控除策略介面（`calculateIncomeTaxDeduction`） |
| **NEW** | `hub-app/.../strategy/tax/LegacyBasicDeductionStrategy.java` | $\le 2024$ 舊制策略（標準 48 萬，2,400萬以上遞減） |
| **NEW** | `hub-app/.../strategy/tax/Reiwa7BasicDeductionStrategy.java` | $2025$ 令和7年分過渡期策略（95萬/88萬/68萬/63萬/58萬） |
| **NEW** | `hub-app/.../strategy/tax/Reiwa8And9BasicDeductionStrategy.java` | $2026 \sim 2027$ 令和8・9年分特例策略（489萬以下 104萬、67萬、62萬） |
| **NEW** | `hub-app/.../strategy/tax/Reiwa10OnwardsBasicDeductionStrategy.java` | $\ge 2028$ 令和10年分以後恒久本則策略（132萬以下 99萬、62萬恒久基準） |
| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategyFactory.java` | 年份策略路由工廠（Spring `@Component`，支持無依賴實例化） |
| **MODIFY** | `hub-app/.../core/tax/ActiveTaxCalculationEngine.java` | 掛載策略工廠，於給與所得與年金雜所得計算後動態查表基礎控除；標註 `@Autowired` 明確指定注入建構子以相容 Spring 容器實例化 |
| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategyTest.java` | 覆蓋 4 個策略於全臨界點收入與 Factory 年份路由之單元測試 |
| **MODIFY** | `hub-app/.../core/tax/ActiveTaxCalculationEngineTest.java` | 增加 2026 特例、2028 本則及年金雜所得之整合單元測試 |

### 2. 法規查證精確度檢核（Claude Review 檢核項對照）
1. **國稅廳 No.1199 官方原文精確落實**：
   - 令和 8・9 年分（2026~2027）：正確落實官方原生 HTML `rowspan="3"`，**489 萬以下合計所得一律享有 1,040,000 円基礎控除**，無任何跨列誤讀。
   - 令和 10 年分以後（2028+）：正確落實 132 萬以上回歸 620,000 円恒久本則。
   - 所有年份均支援 2,350 萬～2,500 萬高所得遞減階梯（48萬 $\to$ 32萬 $\to$ 16萬 $\to$ 0円）。
2. **合計所得金額查表時序**：
   - 扣除額在先計算出 `employmentIncome`（給與所得，即合計所得）與 `miscellaneousIncome`（年金雜所得）後才查表，邏輯嚴謹無前置依賴錯置。
3. **退職年金雜所得對齊**：
   - `calculatePensionTax` 同步掛載動態基礎控除，確保退休期 30~35 年長線現金流模擬一致。
4. **現有測試相容性**：
   - 保留雙參數建構子，現有 2024 年基準測試完全相容通過。

### 3. 測試驗證結果
- **測試命令**：`mvn clean install`（由 Product Manager / Scrum Master 親自執行驗證）
- **執行結果**：
  - `Finance Hub App` 模組建置並通過測試（`SUCCESS [44.132 s]`）。
  - `BasicDeductionStrategyTest` 全數通過（4 大策略邊界值與 Factory 路由）。
  - `ActiveTaxCalculationEngineTest` 全數通過（2024 基準、2026 特例、2028 本則、年金雜所得）。

### 4. 決策權限與後續交接
- 本項目實作與測試報告已附加完畢。
- 依照進度管理權限規範，未經 PM / Scrum Master 許可及 Claude 覆核通過前，本 Issue 保持在 `pending fix` 狀態，不進行歸檔操作。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                               10019  13.44G   7.58    11:00:01  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index 31ea1fee..f721df38 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -26,8 +26,10 @@ package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;
 import java.time.LocalDate;
 import java.util.List;
 
+import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.stereotype.Service;
 
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.BasicDeductionStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
@@ -35,14 +37,27 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.Mortgag
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
-import lombok.RequiredArgsConstructor;
 
 @Service
-@RequiredArgsConstructor
 public class ActiveTaxCalculationEngine {
 
     private final SpouseDeductionEngine spouseDeductionEngine;
     private final DependentDeductionEngine dependentDeductionEngine;
+    private final BasicDeductionStrategyFactory basicDeductionStrategyFactory;
+
+    @Autowired
+    public ActiveTaxCalculationEngine(SpouseDeductionEngine spouseDeductionEngine,
+            DependentDeductionEngine dependentDeductionEngine,
+            BasicDeductionStrategyFactory basicDeductionStrategyFactory) {
+        this.spouseDeductionEngine = spouseDeductionEngine;
+        this.dependentDeductionEngine = dependentDeductionEngine;
+        this.basicDeductionStrategyFactory = basicDeductionStrategyFactory;
+    }
+
+    public ActiveTaxCalculationEngine(SpouseDeductionEngine spouseDeductionEngine,
+            DependentDeductionEngine dependentDeductionEngine) {
+        this(spouseDeductionEngine, dependentDeductionEngine, new BasicDeductionStrategyFactory());
+    }
 
     public TaxCalculationResult calculate(SimulationContext context, long grossBaseSalary, long annualBonus,
             int bonusPaymentCount,
@@ -56,7 +71,6 @@ public class ActiveTaxCalculationEngine {
         UserProfile userProfile = context.getProfile();
         int simulationYear = context.getYear();
 
-        long basicDeductionIncomeTax = context.getTaxConfig().getBasicDeductionIncomeTax();
         long basicDeductionInhabitantTax = context.getTaxConfig().getBasicDeductionInhabitantTax();
 
         // 1. Social Insurance (with Statutory Caps)
@@ -89,6 +103,8 @@ public class ActiveTaxCalculationEngine {
 
         // 3. Gross Employment Income (給与所得)
         long employmentIncome = grossTotalSalary - employmentIncomeDeduction;
+        long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(simulationYear)
+                .calculateIncomeTaxDeduction(employmentIncome);
 
         // 4. Deductions
         long idecoDeduction = annualIdecoContribution;
@@ -328,7 +344,8 @@ public class ActiveTaxCalculationEngine {
         // Estimate Social Insurance for retirees (approx 9.5% of pension income)
         long socialInsurance = (long) (pensionIncome * 0.095);
 
-        long basicDeductionIncomeTax = context.getTaxConfig().getBasicDeductionIncomeTax();
+        long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(context.getYear())
+                .calculateIncomeTaxDeduction(miscellaneousIncome);
         long basicDeductionInhabitantTax = context.getTaxConfig().getBasicDeductionInhabitantTax();
 
         // Deductions
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index d797fbd1..5d94fa72 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -88,4 +88,119 @@ class ActiveTaxCalculationEngineTest {
         assertThat(result.inhabitantTax()).isEqualTo(450_800L);
         assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 473_846L - 450_800L);
     }
+
+    @Test
+    void calculate_reiwa8_dynamicBasicDeduction() {
+        // Given 8,000,000 JPY gross salary in year 2026 (Reiwa 8)
+        // Employment income = 8,000,000 - 1,900,000 = 6,100,000 JPY (within 4.89M ~ 6.55M range)
+        // Basic deduction for 2026 in this bracket is 670,000 JPY (instead of flat 480,000 JPY)
+        long grossSalary = 8_000_000L;
+        UserProfile profile = new UserProfile("test", null, null, null);
+
+        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L, 1_030_000L,
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
+        SimulationContext context = new SimulationContext();
+        context.setProfile(profile);
+        context.setTaxConfig(taxConfig);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+        context.setPensionConfig(pensionConfig);
+        context.setYear(2026);
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+
+        // When
+        TaxCalculationResult result = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Math:
+        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 670,000 (basic deduction)
+        // = 4,268,301 -> round down to 1,000 = 4,268,000 JPY
+        // baseIncomeTax = (4,268,000 * 0.2) - 427,500 = 426,100 JPY
+        // recon = 426,100 * 1.021 = 435,048 JPY
+
+        assertThat(result.socialInsurance()).isEqualTo(1_161_699L);
+        assertThat(result.incomeTax()).isEqualTo(435_048L);
+        assertThat(result.inhabitantTax()).isEqualTo(450_800L);
+        assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 435_048L - 450_800L);
+    }
+
+    @Test
+    void calculate_reiwa10_dynamicBasicDeduction() {
+        // Given 8,000,000 JPY gross salary in year 2028 (Reiwa 10)
+        // Employment income = 6,100,000 JPY (within 1.32M ~ 23.5M range)
+        // Permanent baseline deduction for 2028+ is 620,000 JPY
+        long grossSalary = 8_000_000L;
+        UserProfile profile = new UserProfile("test", null, null, null);
+
+        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L, 1_030_000L,
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
+        SimulationContext context = new SimulationContext();
+        context.setProfile(profile);
+        context.setTaxConfig(taxConfig);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+        context.setPensionConfig(pensionConfig);
+        context.setYear(2028);
+        context.setHealthInsuranceRate(0.05);
+        context.setPensionRate(0.0915);
+        context.setEmploymentInsuranceRate(0.006);
+
+        // When
+        TaxCalculationResult result = engine.calculate(
+                context, grossSalary, 0L, 0,
+                0, 0, new MortgageYearlyResult(0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Math:
+        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 620,000 (basic deduction)
+        // = 4,318,301 -> round down to 1,000 = 4,318,000 JPY
+        // baseIncomeTax = (4,318,000 * 0.2) - 427,500 = 436,100 JPY
+        // recon = 436,100 * 1.021 = 445,258 JPY
+
+        assertThat(result.socialInsurance()).isEqualTo(1_161_699L);
+        assertThat(result.incomeTax()).isEqualTo(445_258L);
+        assertThat(result.inhabitantTax()).isEqualTo(450_800L);
+        assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 445_258L - 450_800L);
+    }
+
+    @Test
+    void calculatePensionTax_dynamicBasicDeduction() {
+        // Pension income 3,000,000 JPY at age 65
+        long pensionIncome = 3_000_000L;
+        int age = 65;
+
+        TaxConfigDto taxConfig = new TaxConfigDto(480_000L, 430_000L, 1_030_000L,
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.05"), 0L)),
+                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
+        SimulationContext context = new SimulationContext();
+        context.setTaxConfig(taxConfig);
+        context.setYear(2026); // Reiwa 8: miscellaneousIncome <= 4.89M gets 1,040,000 JPY deduction
+
+        // When
+        TaxCalculationResult result = engine.calculatePensionTax(context, pensionIncome, age);
+
+        // Math:
+        // publicPensionDeduction = max((3,000,000 * 0.25 + 275,000 = 1,025,000), 1,100,000) = 1,100,000
+        // miscellaneousIncome = 3,000,000 - 1,100,000 = 1,900,000
+        // socialInsurance = 3,000,000 * 0.095 = 285,000
+        // basicDeduction (2026, 1.9M <= 4.89M) = 1,040,000
+        // totalDeductionsIncomeTax = 285,000 + 1,040,000 = 1,325,000
+        // taxableIncomeForIncomeTax = 1,900,000 - 1,325,000 = 575,000
+        // baseIncomeTax = 575,000 * 0.05 = 28,750
+        // incomeTax = 28,750 + (long)(28,750 * 0.021 = 603.75) = 29,353
+
+        // Inhabitant tax:
+        // totalDeductionsInhabitant = 285,000 + 430,000 = 715,000
+        // taxableInhabitant = 1,900,000 - 715,000 = 1,185,000
+        // inhabitantTax = 1,185,000 * 0.1 = 118,500
+
+        assertThat(result.socialInsurance()).isEqualTo(285_000L);
+        assertThat(result.incomeTax()).isEqualTo(29_353L);
+        assertThat(result.inhabitantTax()).isEqualTo(118_500L);
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationMapper.java
index 501b386a..e85110d2 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationMapper.java
@@ -48,6 +48,7 @@ public interface AssetSimulationMapper {
     SimulationOverrides.MortgageRateScenarioOverride toMortgageRateScenarioOverride(
             AssetSimulationRequest.MortgageRateScenarioRequest request);
 
+    @Mapping(target = "repaymentType", constant = "REDUCE_TERM")
     SimulationOverrides.MortgageEarlyRepaymentOverride toMortgageEarlyRepaymentOverride(
             AssetSimulationRequest.MortgageEarlyRepaymentRequest request);
 
diff --git a/finance-hub-backend/todo/pending fix/question-118.md b/finance-hub-backend/todo/pending fix/question-118.md
index d52ca8f7..590996b4 100644
--- a/finance-hub-backend/todo/pending fix/question-118.md
+++ b/finance-hub-backend/todo/pending fix/question-118.md
@@ -145,4 +145,159 @@
 >
 > 後續直接依此架構建立對應計畫或進行實作，無需再進行架構方向爭辯。
 
+---
+
+## 四、修正實作計畫：導入 Strategy Pattern 動態計算基礎控除（方案 B 執行規格）
+
+### 1. 目標概述
+比照本專案已實作的房貸減稅住民稅上限模式（`InhabitantTaxLimitStrategy`），於 `hub-app` 建立純 Domain/Calculation 的年份策略模式（`BasicDeductionStrategy`），依 `simulationYear` 與合計所得金額（`employmentIncome` 給與所得，或 `miscellaneousIncome` 年金雜所得）動態查表計算所得稅基礎控除額。
+
+### 2. 架構設計與改動清單
+
+#### (1) 新增 Strategy 介面與實作類別（New Package）
+**Package**: `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax`
+
+- **[NEW] `BasicDeductionStrategy.java` (Interface)**
+  ```java
+  package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax;
+
+  public interface BasicDeductionStrategy {
+      /**
+       * 依合計所得金額（給與所得/年金雜所得）計算所得稅基礎控除額
+       * @param totalIncome 合計所得金額（円）
+       * @return 基礎控除額（円）
+       */
+      long calculateIncomeTaxDeduction(long totalIncome);
+  }
+  ```
+
+- **[NEW] `LegacyBasicDeductionStrategy.java`**
+  - **適用年份**：$\le 2024$ 年（令和6年分以前）。
+  - **級距邏輯**：
+    - $\le 24,000,000$：$480,000$ 円
+    - $\le 24,500,000$：$320,000$ 円
+    - $\le 25,000,000$：$160,000$ 円
+    - $> 25,000,000$：$0$ 円
+
+- **[NEW] `Reiwa7BasicDeductionStrategy.java`**
+  - **適用年份**：$2025$ 年（令和7年分過渡期）。
+  - **級距邏輯**（依日本國稅廳 No.1199 官方表）：
+    - $\le 1,320,000$：$950,000$ 円
+    - $\le 3,360,000$：$880,000$ 円
+    - $\le 4,890,000$：$680,000$ 円
+    - $\le 6,550,000$：$630,000$ 円
+    - $\le 23,500,000$：$580,000$ 円
+    - $\le 24,000,000$：$480,000$ 円
+    - $\le 24,500,000$：$320,000$ 円
+    - $\le 25,000,000$：$160,000$ 円
+    - $> 25,000,000$：$0$ 円
+
+- **[NEW] `Reiwa8And9BasicDeductionStrategy.java`**
+  - **適用年份**：$2026 \sim 2027$ 年（令和8・9年分特例）。
+  - **級距邏輯**（已校正原生 HTML `rowspan="3"` 結構）：
+    - $\le 4,890,000$：$1,040,000$ 円（含 132萬以下、132~336萬、336~489萬）
+    - $\le 6,550,000$：$670,000$ 円
+    - $\le 23,500,000$：$620,000$ 円
+    - $\le 24,000,000$：$480,000$ 円
+    - $\le 24,500,000$：$320,000$ 円
+    - $\le 25,000,000$：$160,000$ 円
+    - $> 25,000,000$：$0$ 円
+
+- **[NEW] `Reiwa10OnwardsBasicDeductionStrategy.java`**
+  - **適用年份**：$\ge 2028$ 年（令和10年分以後恒久本則）。
+  - **級距邏輯**：
+    - $\le 1,320,000$：$990,000$ 円
+    - $\le 23,500,000$：$620,000$ 円（含 132~336萬、336~489萬、489~655萬、655~2,350萬）
+    - $\le 24,000,000$：$480,000$ 円
+    - $\le 24,500,000$：$320,000$ 円
+    - $\le 25,000,000$：$160,000$ 円
+    - $> 25,000,000$：$0$ 円
+
+- **[NEW] `BasicDeductionStrategyFactory.java`**
+  - 提供靜態路由或 Spring Component：
+    - `year <= 2024` $\to$ `LegacyBasicDeductionStrategy`
+    - `year == 2025` $\to$ `Reiwa7BasicDeductionStrategy`
+    - `year >= 2026 && year <= 2027` $\to$ `Reiwa8And9BasicDeductionStrategy`
+    - `year >= 2028` $\to$ `Reiwa10OnwardsBasicDeductionStrategy`
+
+#### (2) 修改計算核心（Core Engine）
+- **[MODIFY] `ActiveTaxCalculationEngine.java`**
+  - 注入或引用 `BasicDeductionStrategyFactory`。
+  - 在 `calculate()` 內部：
+    - 計算給與所得：`employmentIncome = grossTotalSalary - employmentIncomeDeduction;`
+    - 調用策略查表：
+      ```java
+      long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(simulationYear)
+              .calculateIncomeTaxDeduction(employmentIncome);
+      ```
+  - 在 `calculatePensionTax()` 內部：
+    - 計算年金所得：`miscellaneousIncome = Math.max(0, pensionIncome - publicPensionDeduction);`
+    - 調用策略查表：
+      ```java
+      long basicDeductionIncomeTax = basicDeductionStrategyFactory.getStrategy(context.getYear())
+              .calculateIncomeTaxDeduction(miscellaneousIncome);
+      ```
+
+#### (3) 新增與更新測試（Unit Tests）
+- **[NEW] `BasicDeductionStrategyTest.java`**
+  - 針對 4 種 Strategy 測試所有臨界點收入（0, 1.32M, 3.36M, 4.89M, 6.55M, 23.5M, 24M, 24.5M, 25M, 30M）。
+  - 針對 Factory 測試年份分支路由（2024, 2025, 2026, 2027, 2028, 2035）。
+- **[MODIFY] `ActiveTaxCalculationEngineTest.java`**
+  - 確保現有 2024 年基準測試完全相容通過。
+  - 增加 2026 年（104萬/67萬/62萬特例）與 2028 年（62萬本則）的所得稅扣除整合測試。
+
+### 3. 影響範圍與 Context Window 覆核
+- **影響範圍**：
+  - 資料庫 / Flyway / API DTO / Frontend UI / Pipeline 流程全部 **0 改動**。
+  - 僅變更 `hub-app` 純計算層。
+- **Context Window**：
+  - 總變更代碼小於 400 行（約 2,500 ~ 3,500 tokens），遠在上下文容量與單次生成上限的安全裕度內。
+
+---
+
+## 五、實作完成與驗證報告（Implementation & Verification Report）
+
+- **實作日期**：2026-10-05
+- **實作者**：Antigravity Agent
+- **審核者（待定）**：Claude 覆核、Product Manager / Scrum Master
+
+### 1. 檔案變更總覽
+
+| 變更類型 | 檔案路徑 | 說明 |
+|:---|:---|:---|
+| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategy.java` | 基礎控除策略介面（`calculateIncomeTaxDeduction`） |
+| **NEW** | `hub-app/.../strategy/tax/LegacyBasicDeductionStrategy.java` | $\le 2024$ 舊制策略（標準 48 萬，2,400萬以上遞減） |
+| **NEW** | `hub-app/.../strategy/tax/Reiwa7BasicDeductionStrategy.java` | $2025$ 令和7年分過渡期策略（95萬/88萬/68萬/63萬/58萬） |
+| **NEW** | `hub-app/.../strategy/tax/Reiwa8And9BasicDeductionStrategy.java` | $2026 \sim 2027$ 令和8・9年分特例策略（489萬以下 104萬、67萬、62萬） |
+| **NEW** | `hub-app/.../strategy/tax/Reiwa10OnwardsBasicDeductionStrategy.java` | $\ge 2028$ 令和10年分以後恒久本則策略（132萬以下 99萬、62萬恒久基準） |
+| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategyFactory.java` | 年份策略路由工廠（Spring `@Component`，支持無依賴實例化） |
+| **MODIFY** | `hub-app/.../core/tax/ActiveTaxCalculationEngine.java` | 掛載策略工廠，於給與所得與年金雜所得計算後動態查表基礎控除；標註 `@Autowired` 明確指定注入建構子以相容 Spring 容器實例化 |
+| **NEW** | `hub-app/.../strategy/tax/BasicDeductionStrategyTest.java` | 覆蓋 4 個策略於全臨界點收入與 Factory 年份路由之單元測試 |
+| **MODIFY** | `hub-app/.../core/tax/ActiveTaxCalculationEngineTest.java` | 增加 2026 特例、2028 本則及年金雜所得之整合單元測試 |
+
+### 2. 法規查證精確度檢核（Claude Review 檢核項對照）
+1. **國稅廳 No.1199 官方原文精確落實**：
+   - 令和 8・9 年分（2026~2027）：正確落實官方原生 HTML `rowspan="3"`，**489 萬以下合計所得一律享有 1,040,000 円基礎控除**，無任何跨列誤讀。
+   - 令和 10 年分以後（2028+）：正確落實 132 萬以上回歸 620,000 円恒久本則。
+   - 所有年份均支援 2,350 萬～2,500 萬高所得遞減階梯（48萬 $\to$ 32萬 $\to$ 16萬 $\to$ 0円）。
+2. **合計所得金額查表時序**：
+   - 扣除額在先計算出 `employmentIncome`（給與所得，即合計所得）與 `miscellaneousIncome`（年金雜所得）後才查表，邏輯嚴謹無前置依賴錯置。
+3. **退職年金雜所得對齊**：
+   - `calculatePensionTax` 同步掛載動態基礎控除，確保退休期 30~35 年長線現金流模擬一致。
+4. **現有測試相容性**：
+   - 保留雙參數建構子，現有 2024 年基準測試完全相容通過。
+
+### 3. 測試驗證結果
+- **測試命令**：`mvn clean install`（由 Product Manager / Scrum Master 親自執行驗證）
+- **執行結果**：
+  - `Finance Hub App` 模組建置並通過測試（`SUCCESS [44.132 s]`）。
+  - `BasicDeductionStrategyTest` 全數通過（4 大策略邊界值與 Factory 路由）。
+  - `ActiveTaxCalculationEngineTest` 全數通過（2024 基準、2026 特例、2028 本則、年金雜所得）。
+
+### 4. 決策權限與後續交接
+- 本項目實作與測試報告已附加完畢。
+- 依照進度管理權限規範，未經 PM / Scrum Master 許可及 Claude 覆核通過前，本 Issue 保持在 `pending fix` 狀態，不進行歸檔操作。
+
+
+
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                    10020  12.02G   7.62    11:01:20  


