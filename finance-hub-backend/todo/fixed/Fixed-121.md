# [Fixed] Question 121：令和7年新增的「特定親族特別控除」（19-22歲扶養親屬的過渡扣除）目前完全沒有實作

## 背景
`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java:49-108` 對19-22歲的扶養親屬（`SPECIFIC_DEPENDENT`），目前是二選一的判斷：所得沒超過門檻就給全額630,000（所得稅）／450,000（住民稅），超過門檻就整個歸零，沒有中間地帶。

查證到日本從令和7年12月起，新增了一個叫「特定親族特別控除」的制度：19-22歲的扶養親屬，所得在580,000~1,230,000之間時，不再是超過門檻就整個歸零，而是改用類似配偶特別控除的階梯式遞減設計，從630,000一路降到30,000（分成多個¥50,000一階的所得區間）。

## 不確定的地方
1. 這是2025年底才生效的全新制度，程式碼原本開發時如果早於這個時間點，本來就不會涵蓋到，這屬於法規變動追不上、不是原本邏輯寫錯。
2. 這個制度只影響19-22歲、且所得剛好落在580,000~1,230,000這個過渡區間的扶養親屬——是否要投入時間實作這個階梯遞減表，取決於使用者是否有、或未來會有符合這個年齡與所得區間的扶養親屬。

## 需要使用者確認的問題
1. 目前家庭成員裡，是否有（或近期規劃內會有）19-22歲、且有一定所得（例如打工、兼職）的扶養親屬？如果沒有，這個落差可以先不處理。
2. 如果決定要做，這是一個新增的獨立扣除類別，需要新增對應的階梯遞減表，技術上不困難，但屬於全新功能而不是修正既有邏輯，需要另外排時間規劃。

---

## 覆核結果與客觀事實查證（Review & Zero-Premise Legal Verification）

已全面查核日本國稅廳官方規範（[No.1177 特定親族特別控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1177.htm)、[No.1180 扶養控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1180.htm)、[No.1410 給与所得控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1410.htm)）、日本《所得稅法》第 84 條之 2、日本《地方稅法》（第 314 條之 2 第 1 項第 12 號、第 34 條第 1 項第 12 號）以及現行後端實作（[DependentDeductionEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java)），客觀事實、法規對照矩陣與架構分析如下：

### 一、Question 121 內文之客觀事實查證（國稅局官網查證與雙年度法規對齊）

1. **施行時程查證（撤銷前次誤判，確認 Claude 敘述完全客觀屬實）**：
   - **Claude 原敘述**：「日本從令和7年12月起，新增了一個叫『特定親族特別控除』的制度」。
   - **客觀事實查證結果**：**Claude 原敘述完全正確！**
     - 依據日本國稅廳官方公布之《令和７年度税制改正による所得税の基礎控除の見直し等について》（[源泉所得稅特設專頁](https://www.nta.go.jp/users/gensen/2025kiso/index.htm)），特定親族特別控除（所法第 84 條之 2）明確規定：**「令和 7 年 12 月 1 日に施行され、令和 7 年分以後の所得税について適用されます」**，於令和 7 年 12 月年末調整即正式實施。
     - 國稅廳 TaxAnswer [No.1177](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1177.htm)（令和 8 年 4 月 1 日法令版）備註（注1）載明之「令和 8 年 12 月 1 日施行」，實為**令和 8 年度改正（配合最低薪資扣除由 65 萬提高至 74 萬、扶養門檻由 58 萬提升至 62 萬之第二階段調升）之生效期日**，並非制度創設期日。
     - 因此，前次覆核指稱「令和7年底並未生效」實屬查證遺漏造成的誤判（False Correction），Claude 原文時間點完全精確。

2. **適用所得區間查證（雙年度法定數值對齊）**：
   - **Claude 原敘述**：「所得在 580,000～1,230,000 之間」。
   - **客觀事實查證結果**：**兩者皆為正式法定數值，依年度精確適用**。
     - **令和 7 年分（2025 年）**：法定給與所得控除最低額為 65 萬，一般扶養門檻為 58 萬（給與收入等價 123 萬之壁）。特定親族特別控除適用區間為：
       - **特定親族合計所得金額：58 萬超 ～ 123 萬以下**
       - **純薪資打工收入等價區間：123 萬超 ～ 188 萬以下**（$123\text{萬} + 65\text{萬} = 188\text{萬}$）。
       - 控除額階梯：58萬超～85萬為 63 萬滿額，其後每 5 萬一階遞減至 3 萬。
     - **令和 8 年分以後（2026 年起）**：最低給與所得控除進一步調升至 74 萬，一般扶養門檻調升至 62 萬（給與收入等價 136 萬之壁）。國稅廳 No.1177 適用區間隨之調整為：
       - **特定親族合計所得金額：62 萬超 ～ 123 萬以下**
       - **純薪資打工收入等價區間：136 萬超 ～ 197 萬以下**（$123\text{萬} + 74\text{萬} = 197\text{萬}$）。
       - 控除額階梯：62萬超～85萬為 63 萬滿額，其後每 5 萬一階遞減至 3 萬。

3. **納稅義務人本人所得限制（本人所得制限）檢證**：
   - **客觀事實確認**：**納稅義務人本人（父母）「完全無所得限制（所得制限なし）」**。
   - 經核對國稅廳 No.1177 與《所得稅法》第 84 條之 2，特定親族特別控除與配偶控除（本人所得超過 900 萬/950 萬打折、超過 1,000 萬歸零之 2D 矩陣）截然不同，特定親族特別控除**不論納稅人年薪多高（即使年薪 2,000 萬以上），皆可享有一模一樣的扣除額**。
   - 軟體架構上僅為**單純的一維階梯查表（1D Lookup Table）**，無需本人所得交叉矩陣。

4. **適用親族之法定年齡要件查證**：
   - **客觀事實確認**：依《所得稅法》第 84 條之 2 第 1 項（及同法第 2 條第 1 項第 34 號之 3 等親族定義規定），法定精準要件為**「その年の12月31日の現況で、年齢19歳以上23歳未満」**。
   - Claude 原文使用之「19-22歲」在實務稅務情境與日語習慣中，係指當年度達 19 歲且未滿 23 歲（即實歲 19、20、21、22 歲）之大學生世代，兩者實質完全等價。於系統規格與法規審核時，以「その年の12月31日現況で年齢19歳以上23歳未満」為精準判定基準。

---

### 二、法定級距對照表（所得稅 vs 住民稅精確法定數值與生效時間點）

#### 1. 令和 7 年分法定對照表（2025 年制度創設基準：門檻 58 萬／打工年薪等價 123 萬）

- **法定施行期日**：**2025 年 12 月 1 日（令和 7 年 12 月 1 日）施行**
- **所得稅適用時間點**：**2025 年分（令和 7 年分）所得稅**（自 2025 年 12 月年末調整及 2026 年初申報之確定申告正式適用）
- **住民稅適用時間點**：**2026 年度（令和 8 年度）個人住民稅**（對應 2025 年所得，自 2026 年 6 月起課徵）
- **法規依據**：日本國稅廳《令和７年度税制改正による所得税の基礎控除の見直し等について》（所法第 84 條之 2）與《地方稅法》（第 314 條之 2 第 1 項第 12 號、第 34 條第 1 項第 12 號）
- **換算前提說明**：「純薪資年收等價（打工毛收入）」係依《所得稅法》別表第五，假定特定親族**僅有單一給與收入（給与所得のみ，扣除額 65 萬）**之換算結果。若親屬兼具事業或雜所得，母法唯一法定適用標準為第一欄「合計所得金額」。

| 特定親族合計所得金額 | 純薪資年收等價（打工毛收入） | 所得稅特別控除額（所法第 84 條之 2） | 住民稅特別控除額（地方稅法） | 級距說明與特性分析 |
| :--- | :--- | :---: | :---: | :--- |
| **$\le$ 58 萬元** | $\le$ 123 萬元 | **63 萬元** | **45 萬元** | 適用一般特定扶養親族控除（所法第 84 條） |
| **58萬超 ～ 85萬以下** | **123萬超 ～ 150 萬元** | **63 萬元** | **45 萬元** | **新法緩衝帶：依法依然享有 100% 滿額扣除！** |
| **85萬超 ～ 90萬以下** | 150萬超 ～ 155 萬元 | **61 萬元** | **45 萬元** | 所得稅第 1 階遞減；**住民稅依法維持滿額 45 萬** |
| **90萬超 ～ 95萬以下** | 155萬超 ～ 160 萬元 | **51 萬元** | **45 萬元** | 所得稅第 2 階遞減；**住民稅依法維持滿額 45 萬** |
| **95萬超 ～ 100萬以下** | 160萬超 ～ 165 萬元 | **41 萬元** | **41 萬元** | **自此階起雙稅金額完全同步**（41 萬） |
| **100萬超 ～ 105萬以下** | 165萬超 ～ 170 萬元 | **31 萬元** | **31 萬元** | 雙稅同步遞減第 4 階 |
| **105萬超 ～ 110萬以下** | 170萬超 ～ 175 萬元 | **21 萬元** | **21 萬元** | 雙稅同步遞減第 5 階 |
| **110萬超 ～ 115萬以下** | 175萬超 ～ 180 萬元 | **11 萬元** | **11 萬元** | 雙稅同步遞減第 6 階 |
| **115萬超 ～ 120萬以下** | 180萬超 ～ 185 萬元 | **6 萬元** | **6 萬元** | 雙稅同步遞減第 7 階 |
| **120萬超 ～ 123萬以下** | 185萬超 ～ 188 萬元 | **3 萬元** | **3 萬元** | 雙稅同步遞減最後一階 |
| **> 123 萬元** | **> 188 萬元** | **0** | **0** | 完全脫離扶養與特別控除 |

#### 2. 令和 8 年真實法定級距對照表（2026 年起門檻調升基準：門檻 62 萬／打工年薪等價 136 萬）

- **法定施行期日**：**2026 年 12 月 1 日（令和 8 年 12 月 1 日）施行**
- **所得稅適用時間點**：**2026 年分（令和 8 年分）以後の所得税**（自 2026 年 12 月年末調整及 2027 年初申報之確定申告正式適用）
- **住民稅適用時間點**：**2027 年度（令和 9 年度）以後の個人住民稅**（對應 2026 年所得，自 2027 年 6 月起課徵）
- **法規依據**：日本國稅廳 TaxAnswer No.1177（所法第 84 條之 2 令和8年改正版）與《地方稅法》（第 314 條之 2 第 1 項第 12 號、第 34 條第 1 項第 12 號）
- **換算前提說明**：「純薪資年收等價（打工毛收入）」係依《所得稅法》別表第五，假定特定親族**僅有單一給與收入（給与所得のみ，扣除額 74 萬）**之換算結果。若親屬兼具事業或雜所得，母法唯一法定適用標準為第一欄「合計所得金額」。

| 特定親族合計所得金額 | 純薪資年收等價（打工毛收入） | 所得稅特別控除額（國稅廳 No.1177） | 住民稅特別控除額（地方稅法） | 級距說明與特性分析 |
| :--- | :--- | :---: | :---: | :--- |
| **$\le$ 62 萬元** | $\le$ 136 萬元 | **63 萬元** | **45 萬元** | 適用一般特定扶養親族控除（所法第 84 條） |
| **62萬超 ～ 85萬以下** | **136萬超 ～ 159 萬元** | **63 萬元** | **45 萬元** | **新法緩衝帶：依法依然享有 100% 滿額扣除！** |
| **85萬超 ～ 90萬以下** | 159萬超 ～ 164 萬元 | **61 萬元** | **45 萬元** | 所得稅第 1 階遞減；**住民稅依法維持滿額 45 萬** |
| **90萬超 ～ 95萬以下** | 164萬超 ～ 169 萬元 | **51 萬元** | **45 萬元** | 所得稅第 2 階遞減；**住民稅依法維持滿額 45 萬** |
| **95萬超 ～ 100萬以下** | 169萬超 ～ 174 萬元 | **41 萬元** | **41 萬元** | **自此階起雙稅金額完全同步**（41 萬） |
| **100萬超 ～ 105萬以下** | 174萬超 ～ 179 萬元 | **31 萬元** | **31 萬元** | 雙稅同步遞減第 4 階 |
| **105萬超 ～ 110萬以下** | 179萬超 ～ 184 萬元 | **21 萬元** | **21 萬元** | 雙稅同步遞減第 5 階 |
| **110萬超 ～ 115萬以下** | 184萬超 ～ 189 萬元 | **11 萬元** | **11 萬元** | 雙稅同步遞減第 6 階 |
| **115萬超 ～ 120萬以下** | 189萬超 ～ 194 萬元 | **6 萬元** | **6 萬元** | 雙稅同步遞減第 7 階 |
| **120萬超 ～ 123萬以下** | 194萬超 ～ 197 萬元 | **3 萬元** | **3 萬元** | 雙稅同步遞減最後一階 |
| **> 123 萬元** | **> 197 萬元** | **0** | **0** | 完全脫離扶養與特別控除 |

> **關鍵法規事實澄清（避免審查退件）**：
> 1. **住民稅並非等比例縮小**：地方稅法中，特定親族所得在 95 萬以下（打工年收 169 萬以下）時，住民稅扣除額**一律維持 45 萬滿額**，並無 43 萬或 36 萬等中間過渡數字。
> 2. **雙稅高階完全重合**：所得在 95 萬 ～ 123 萬（打工年收 169 萬 ～ 197 萬）時，住民稅扣除額與所得稅扣除額**完全一致**（41萬、31萬、21萬、11萬、6萬、3萬）。

---

### 三、現行實作相對於真實法規之落差分析

現行 [DependentDeductionEngine.java:54-60](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java#L54-L60) 的判斷邏輯為：
```java
long dependentIncome = reqIncome != null ? reqIncome : 0L;
long threshold = context.getTaxConfig().getDependentIncomeThreshold();
if (dependentIncome > threshold) {
    return 0L;
}
```
經 Question 119 校正後，`dependentIncomeThreshold` 已更新為令和 8 年標準 `1,360,000`（136 萬）。在此基礎上，系統與真實稅法存在以下兩大層次的行為落差：

1. **區間 A：打工年薪 136 萬 ～ 159 萬（最嚴重的誤殺區間）**
   - **業務情境**：在學大學生每月打工約 11 萬 ～ 13 萬日圓（年收 136 萬 ～ 159 萬）。
   - **稅法規定**：屬於新法創設之「滿額緩衝帶」，父母依法享有 **所得稅 63 萬 / 住民稅 45 萬之全額扣除**。
   - **現行實作**：因 `dependentIncome > 1,360,000`，系統直接判定歸零。
   - **偏差金額**：所得稅扣除額短少 63 萬，住民稅扣除額短少 45 萬。依家庭所得級距換算實質稅負偏差如下：
     - **一般受薪中產家庭**（所得稅率 10% + 住民稅率 10%）：稅負直接高估 **¥108,000 日圓**（所得稅短少 6.3 萬 + 住民稅短少 4.5 萬）。
     - **較高所得受薪家庭**（年薪約 900 萬以上，所得稅率 20%～23% + 住民稅率 10%）：稅負直接高估約 **¥171,000 ～ ¥193,000 日圓**（若邊際所得稅率達 33% 則高估達 ¥250,000 以上）。

2. **區間 B：打工年薪 159 萬 ～ 197 萬（階梯遞減區間）**
   - **業務情境**：大學生打工收入較高（年收 159 萬 ～ 197 萬）。
   - **稅法規定**：提供 61 萬 ～ 3 萬的平緩遞減扣除。
   - **現行實作**：一律歸零。
   - **偏差金額**：扣除額短少 61 萬 ～ 3 萬不等。

3. **區間 C：打工年薪 $\le 136$ 萬 或 $> 197$ 萬**
   - 現行實作與真實稅法完全一致，**誤差為 0**。

---

### 四、處理解法選項（供 PM 與架構決策）

為使後續 Source Code Review 與架構規劃具備清晰客觀的衡量標準，提供以下兩種處理解法：

#### 選項 A：引入策略模式（Strategy Pattern）實作特定親族特別控除（高嚴謹度，對齊 Q120）
- **技術規格**：
  - 新建套件 `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.dependent`。
  - 定義 `DependentDeductionStrategy` 介面，依模擬年份由 `DependentDeductionStrategyFactory` 依 `simulationYearEnd` 精準判定派發：
    - `LegacyDependentDeductionStrategy`（`simulationYearEnd` < 2025-12-01，即 <= 2024 年分）：維持歷史標準扶養判定（所得門檻 48 萬 / 給與 103 萬，無特定親族特別控除）。
    - `Reiwa7DependentDeductionStrategy`（2025-12-01 <= `simulationYearEnd` < 2026-12-01，即 2025 年分）：特定親族（年齡 19～22 歲，法定定義：19歲以上23歲未満）年收介於 123 萬 ～ 188 萬（所得 58萬超～123萬）時，查表派發所得稅（63萬～3萬）與住民稅（45萬～3萬）之階梯扣除額。
    - `Reiwa8DependentDeductionStrategy`（`simulationYearEnd` >= 2026-12-01，即 >= 2026 年分起）：特定親族（年齡 19～22 歲，法定定義：19歲以上23歲未満）年收介於 136 萬 ～ 197 萬（所得 62萬超～123萬）時，查表派發所得稅（63萬～3萬）與住民稅（45萬～3萬）之階梯扣除額。
  - 將 `DependentDeductionEngine.java` 改為委託 Strategy 計算。
- **優點**：
  1. 100% 精準對齊日本新舊稅制交替時程，徹底消除大學生兼職家庭年稅被誤算 ¥10.8 萬 ～ ¥19.3 萬以上之偏差。
  2. 因無本人排富限制，為單純 1D 階梯查表，程式碼複雜度遠低於配偶扣除之 2D 矩陣。
  3. 與 `BasicDeductionStrategy`、`SpouseDeductionStrategy` 保持統一的策略模式架構風格，符合 OCP。
- **成本**：需實作策略介面、工廠、實作類別與完整單元測試。

#### 選項 B：維持現行二元邏輯（超過門檻歸零），記錄為已知限制（Known Limitation / Backlog）
- **技術規格**：程式碼完全不改動，在待辦清單中將「19~22 歲打工年薪 136 萬~197 萬之過渡階梯」標記為已知限制。
- **優點**：零開發工時與零測試成本。
- **已知限制與業務影響**：若使用者的家庭成員模型中設定有 19～22 歲子女，且該子女在學兼職打工年薪卡在 136 萬 ～ 197 萬之間，模擬計算中該家庭的稅負將被高估。

---

### 五、權限與決策邊界聲明（Notice on Authority & Decision Boundary）

- **架構調查員聲明**：
  本調查已全面排除任何預設立場，完成了日本國稅廳法定法條、施行時程、所得稅與住民稅精確級距之客觀檢證，並提供了完整的架構選項與量化影響分析。
- **決策權屬**：
  AI 助理不具備專案進度管理與功能排程之權限。是否實作「特定親族特別控除」、採行選項 A 或選項 B、以及是否需要分階段執行，**完全由 Product Manager（使用者兼 Scrum Master）全權裁定**。未經 PM 明確裁定指示前，不擅自推動下一步或修改程式碼。

---

### 六、實作執行記錄與落地總結（Execution Record & Verification）

- **執行狀態**：✅ 已完成修正（Status: Fixed 2026-10-07）
- **處理解法採行**：**選項 A（導入特定親族特別控除 1D 階梯查表與策略派發）**

#### 1. 核心落地架構與法規對齊
1. **策略基底擴充（AbstractDependentDeductionStrategy）**：
   - 於 [AbstractDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java) 中，精準鎖定 `age >= 19 && age <= 22`（法定 19 歲以上 23 歲未滿）。
   - 當薪資超過一般門檻時，不再直接歸零，而是委派鉤子方法 `calculateSpecificDependentSpecialIncomeTaxDeduction` 與 `calculateSpecificDependentSpecialInhabitantTaxDeduction`。
   - 基類預設回傳 `0L`，確保令和 6 年以前（Legacy）與非 19～22 歲族群嚴格維持舊制行為。
2. **令和 7 年分過渡查表（Reiwa7DependentDeductionStrategy）**：
   - [Reiwa7DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java) 實作 123 萬至 188 萬打工年收之法定階梯。
   - 支援 123 萬～150 萬所得稅滿額緩衝帶（63 萬）與 123 萬～160 萬住民稅滿額緩衝帶（45 萬），其後逐階遞減至 188 萬（3 萬）。
3. **令和 8～9 年分新制特例查表（Reiwa8And9DependentDeductionStrategy）**：
   - [Reiwa8And9DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java) 依日本國稅廳 No.1177 官方級距，實作 136 萬至 197 萬之法定階梯。
   - 支援 136 萬～159 萬所得稅滿額緩衝帶（63 萬）與 136 萬～169 萬住民稅滿額緩衝帶（45 萬），其後自 169 萬起雙稅同步逐階遞減至 197 萬（3 萬），超過 197 萬歸零。
   - 納稅義務人本人無所得限制（所得制限なし），為單純 1D 查表。
4. **令和 10 年分起本則落日查表（Reiwa10OnwardsDependentDeductionStrategy）**：
   - [Reiwa10OnwardsDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java) 忠實反映給與所得控除 5 萬特例落日（最低保證由 74 萬回歸 69 萬，基準由 136 萬移至 131 萬，緩衝帶至 154 萬，上限至 192 萬）。
5. **課稅時差與工廠整合（Strategy Factory & Engine）**：
   - [DependentDeductionStrategyFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyFactory.java) 與 [DependentDeductionEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java) 完全無縫調度：2026 模擬首年所得稅走 Reiwa 8-9（現年），住民稅走 Reiwa 7（前年）。

#### 2. 異動檔案清單
- [AbstractDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java)
- [Reiwa7DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java)
- [Reiwa8And9DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java)
- [Reiwa10OnwardsDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java)
- [DependentDeductionStrategyTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java)
- [DependentDeductionEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java)

#### 3. 測試驗證矩陣
- **Reiwa 8 & 9 特例梯次驗證**：
  - 打工收入 $\le$ 1.59M（緩衝帶）：所得稅 630K / 住民稅 450K。
  - 打工收入 1.59M~1.64M（Step 1）：所得稅 610K / 住民稅 450K。
  - 打工收入 1.64M~1.69M（Step 2）：所得稅 510K / 住民稅 450K。
  - 打工收入 1.69M~1.74M（Step 3）：雙稅同步 410K / 410K。
  - 打工收入 1.74M~1.79M（Step 4）：雙稅同步 310K / 310K。
  - 打工收入 1.79M~1.84M（Step 5）：雙稅同步 210K / 210K。
  - 打工收入 1.84M~1.89M（Step 6）：雙稅同步 110K / 110K。
  - 打工收入 1.89M~1.94M（Step 7）：雙稅同步 60K / 60K。
  - 打工收入 1.94M~1.97M（Final Step）：雙稅同步 30K / 30K。
  - 打工收入 $>$ 1.97M：歸零（0L）。
- **Reiwa 7 梯次驗證**：緩衝帶 $\le$ 1.50M（630K / 450K），末階 1.88M（30K / 30K），超過 1.88M 歸零。
- **Reiwa 10+ 本則落日梯次驗證**：緩衝帶 $\le$ 1.54M（630K / 450K），末階 1.92M（30K / 30K），超過 1.92M 歸零。
- **邊界與非特定年齡防護驗證**：
  - 令和 6 年以前舊制（Legacy）：打工收入超過 1.03M 一律歸零，不適用特別控除。
  - 17 歲（一般扶養親族）與 31 歲（一般成年親族）：收入超標時一律歸零，絕不誤用特定親族特別控除。
- **引擎層時差整合驗證**：2026 年進行模擬時，20 歲在學打工子女年收 1.36M，所得稅給予 63 萬、住民稅課徵 2025 年所得享 45 萬；超過 1.88M 住民稅歸零。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                                                       10004  12.26G   5.55    16:07:59  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
index 9776a072..058e769d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
@@ -74,6 +74,13 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
         Long age = calculateAge(birthDate, simulationYearEnd);
 
+        if (age != null && age >= 19 && age <= 22) {
+            if (isEligible(income, age)) {
+                return SPECIFIC_DEPENDENT_INCOME_TAX;
+            }
+            return calculateSpecificDependentSpecialIncomeTaxDeduction(income);
+        }
+
         if (!isEligible(income, age)) {
             return 0L;
         }
@@ -84,8 +91,6 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
 
         if (age < 16) {
             return 0L;
-        } else if (age >= 19 && age <= 22) {
-            return SPECIFIC_DEPENDENT_INCOME_TAX;
         } else if (age >= 70) {
             return ELDERLY_DEPENDENT_INCOME_TAX;
         } else {
@@ -98,6 +103,13 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
         Long age = calculateAge(birthDate, simulationYearEnd);
 
+        if (age != null && age >= 19 && age <= 22) {
+            if (isEligible(income, age)) {
+                return SPECIFIC_DEPENDENT_INHABITANT_TAX;
+            }
+            return calculateSpecificDependentSpecialInhabitantTaxDeduction(income);
+        }
+
         if (!isEligible(income, age)) {
             return 0L;
         }
@@ -108,8 +120,6 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
 
         if (age < 16) {
             return 0L;
-        } else if (age >= 19 && age <= 22) {
-            return SPECIFIC_DEPENDENT_INHABITANT_TAX;
         } else if (age >= 70) {
             return ELDERLY_DEPENDENT_INHABITANT_TAX;
         } else {
@@ -117,6 +127,30 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         }
     }
 
+    /**
+     * Calculates the special income tax deduction for specific dependents (age 19-22)
+     * whose wage income exceeds the standard eligibility threshold.
+     * Default implementation returns 0L (legacy behavior).
+     *
+     * @param wageIncome annual wage income in JPY
+     * @return income tax deduction in JPY
+     */
+    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
+        return 0L;
+    }
+
+    /**
+     * Calculates the special personal inhabitant tax deduction for specific dependents (age 19-22)
+     * whose wage income exceeds the standard eligibility threshold.
+     * Default implementation returns 0L (legacy behavior).
+     *
+     * @param wageIncome annual wage income in JPY
+     * @return inhabitant tax deduction in JPY
+     */
+    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
+        return 0L;
+    }
+
     /**
      * Checks whether the dependent's annual income is within the statutory eligibility threshold.
      * Elderly relatives (age >= 65) are eligible up to the pension threshold (公的年金等控除 110 萬 + 所得門檻),
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java
index 732150f4..b7ab417e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java
@@ -55,4 +55,50 @@ public class Reiwa10OnwardsDependentDeductionStrategy extends AbstractDependentD
     public long getElderlyPensionThreshold() {
         return ELDERLY_PENSION_THRESHOLD;
     }
+
+    @Override
+    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_540_000L) {
+            return 630_000L;
+        } else if (wageIncome <= 1_590_000L) {
+            return 610_000L;
+        } else if (wageIncome <= 1_640_000L) {
+            return 510_000L;
+        } else if (wageIncome <= 1_690_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_740_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_790_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_840_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_890_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_920_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
+
+    @Override
+    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_640_000L) {
+            return 450_000L;
+        } else if (wageIncome <= 1_690_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_740_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_790_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_840_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_890_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_920_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java
index 9eae219c..f1e48cee 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java
@@ -54,4 +54,50 @@ public class Reiwa7DependentDeductionStrategy extends AbstractDependentDeduction
     public long getElderlyPensionThreshold() {
         return ELDERLY_PENSION_THRESHOLD;
     }
+
+    @Override
+    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_500_000L) {
+            return 630_000L;
+        } else if (wageIncome <= 1_550_000L) {
+            return 610_000L;
+        } else if (wageIncome <= 1_600_000L) {
+            return 510_000L;
+        } else if (wageIncome <= 1_650_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_700_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_750_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_800_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_850_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_880_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
+
+    @Override
+    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_600_000L) {
+            return 450_000L;
+        } else if (wageIncome <= 1_650_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_700_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_750_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_800_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_850_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_880_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java
index e0a964b1..1fc7a2f8 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java
@@ -55,4 +55,50 @@ public class Reiwa8And9DependentDeductionStrategy extends AbstractDependentDeduc
     public long getElderlyPensionThreshold() {
         return ELDERLY_PENSION_THRESHOLD;
     }
+
+    @Override
+    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_590_000L) {
+            return 630_000L;
+        } else if (wageIncome <= 1_640_000L) {
+            return 610_000L;
+        } else if (wageIncome <= 1_690_000L) {
+            return 510_000L;
+        } else if (wageIncome <= 1_740_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_790_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_840_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_890_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_940_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_970_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
+
+    @Override
+    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
+        if (wageIncome <= 1_690_000L) {
+            return 450_000L;
+        } else if (wageIncome <= 1_740_000L) {
+            return 410_000L;
+        } else if (wageIncome <= 1_790_000L) {
+            return 310_000L;
+        } else if (wageIncome <= 1_840_000L) {
+            return 210_000L;
+        } else if (wageIncome <= 1_890_000L) {
+            return 110_000L;
+        } else if (wageIncome <= 1_940_000L) {
+            return 60_000L;
+        } else if (wageIncome <= 1_970_000L) {
+            return 30_000L;
+        } else {
+            return 0L;
+        }
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
index 1a51a312..e4b2b3b0 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
@@ -250,4 +250,128 @@ class DependentDeductionStrategyTest {
             assertThat(reiwa10.calculateIncomeTaxDeduction(1_720_001L, birth1955, yearEnd2026)).isZero();
         }
     }
+
+    @Nested
+    @DisplayName("Specific Dependent (Age 19-22) Special Deduction Step Tests")
+    @SuppressWarnings("unused")
+    class SpecificDependentSpecialDeductionTests {
+
+        private final LocalDate yearEnd2026 = LocalDate.of(2026, 12, 31);
+        private final LocalDate studentBirth2006 = LocalDate.of(2006, 5, 20); // Age 20 (19 <= age <= 22)
+        private final LocalDate teenBirth2009 = LocalDate.of(2009, 5, 20); // Age 17 (not specific)
+        private final LocalDate adultBirth1995 = LocalDate.of(1995, 5, 20); // Age 31 (not specific)
+
+        @Test
+        void shouldCalculateReiwa8And9SteppedDeductions() {
+            DependentDeductionStrategy reiwa8 = new Reiwa8And9DependentDeductionStrategy();
+
+            // Standard eligible: <= 1.36M -> Full 630K / 450K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_360_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_360_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Buffer zone: 1.36M < wage <= 1.59M -> 100% full deduction 630K / 450K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_590_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_590_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Step 1: 1.59M < wage <= 1.64M -> Income tax 610K / Inhabitant tax 450K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_600_000L, studentBirth2006, yearEnd2026)).isEqualTo(610_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_600_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Step 2: 1.64M < wage <= 1.69M -> Income tax 510K / Inhabitant tax 450K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_650_000L, studentBirth2006, yearEnd2026)).isEqualTo(510_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_650_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Step 3: 1.69M < wage <= 1.74M -> Income tax 410K / Inhabitant tax 410K (Synchronized!)
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_700_000L, studentBirth2006, yearEnd2026)).isEqualTo(410_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_700_000L, studentBirth2006, yearEnd2026)).isEqualTo(410_000L);
+
+            // Step 4: 1.74M < wage <= 1.79M -> 310K / 310K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_750_000L, studentBirth2006, yearEnd2026)).isEqualTo(310_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_750_000L, studentBirth2006, yearEnd2026)).isEqualTo(310_000L);
+
+            // Step 5: 1.79M < wage <= 1.84M -> 210K / 210K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_800_000L, studentBirth2006, yearEnd2026)).isEqualTo(210_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_800_000L, studentBirth2006, yearEnd2026)).isEqualTo(210_000L);
+
+            // Step 6: 1.84M < wage <= 1.89M -> 110K / 110K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_850_000L, studentBirth2006, yearEnd2026)).isEqualTo(110_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_850_000L, studentBirth2006, yearEnd2026)).isEqualTo(110_000L);
+
+            // Step 7: 1.89M < wage <= 1.94M -> 60K / 60K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_900_000L, studentBirth2006, yearEnd2026)).isEqualTo(60_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_900_000L, studentBirth2006, yearEnd2026)).isEqualTo(60_000L);
+
+            // Final Step: 1.94M < wage <= 1.97M -> 30K / 30K
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_970_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_970_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+
+            // Beyond 1.97M -> 0
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_970_001L, studentBirth2006, yearEnd2026)).isZero();
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_970_001L, studentBirth2006, yearEnd2026)).isZero();
+        }
+
+        @Test
+        void shouldCalculateReiwa7SteppedDeductions() {
+            DependentDeductionStrategy reiwa7 = new Reiwa7DependentDeductionStrategy();
+
+            // Standard eligible: <= 1.23M -> Full 630K / 450K
+            assertThat(reiwa7.calculateIncomeTaxDeduction(1_230_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_230_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Buffer zone: 1.23M < wage <= 1.50M -> 630K / 450K
+            assertThat(reiwa7.calculateIncomeTaxDeduction(1_400_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_400_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Step 1: 1.50M < wage <= 1.55M -> 610K / 450K
+            assertThat(reiwa7.calculateIncomeTaxDeduction(1_520_000L, studentBirth2006, yearEnd2026)).isEqualTo(610_000L);
+            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_520_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Final Step: 1.85M < wage <= 1.88M -> 30K / 30K
+            assertThat(reiwa7.calculateIncomeTaxDeduction(1_880_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_880_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+
+            // Beyond 1.88M -> 0
+            assertThat(reiwa7.calculateIncomeTaxDeduction(1_880_001L, studentBirth2006, yearEnd2026)).isZero();
+            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_880_001L, studentBirth2006, yearEnd2026)).isZero();
+        }
+
+        @Test
+        void shouldCalculateReiwa10OnwardsSteppedDeductions() {
+            DependentDeductionStrategy reiwa10 = new Reiwa10OnwardsDependentDeductionStrategy();
+
+            // Standard eligible: <= 1.31M -> Full 630K / 450K
+            assertThat(reiwa10.calculateIncomeTaxDeduction(1_310_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_310_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Buffer zone: 1.31M < wage <= 1.54M -> 630K / 450K
+            assertThat(reiwa10.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
+            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
+
+            // Final Step: 1.89M < wage <= 1.92M -> 30K / 30K
+            assertThat(reiwa10.calculateIncomeTaxDeduction(1_920_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_920_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
+
+            // Beyond 1.92M -> 0
+            assertThat(reiwa10.calculateIncomeTaxDeduction(1_920_001L, studentBirth2006, yearEnd2026)).isZero();
+            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_920_001L, studentBirth2006, yearEnd2026)).isZero();
+        }
+
+        @Test
+        void shouldNotApplySpecialDeductionToLegacyOrNonSpecificAge() {
+            DependentDeductionStrategy legacy = new LegacyDependentDeductionStrategy();
+            DependentDeductionStrategy reiwa8 = new Reiwa8And9DependentDeductionStrategy();
+
+            // Legacy has no special deduction: > 1.03M is always 0 even for student
+            assertThat(legacy.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isZero();
+            assertThat(legacy.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isZero();
+
+            // Reiwa 8: non-specific age (teen 17 or adult 31) exceeding threshold gets 0
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, teenBirth2009, yearEnd2026)).isZero();
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, teenBirth2009, yearEnd2026)).isZero();
+            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, adultBirth1995, yearEnd2026)).isZero();
+            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, adultBirth1995, yearEnd2026)).isZero();
+        }
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
index fcf8ed0e..52ee0d3b 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
@@ -73,9 +73,14 @@ class DependentDeductionEngineTest {
         long incomeTaxDeduction = engine.calculateIncomeTaxDeduction(context, dependent, yearEnd2026);
         assertThat(incomeTaxDeduction).isEqualTo(630_000L);
 
-        // 2026 Inhabitant Tax: assesses 2025 (Reiwa 7) income where threshold is 1.23M -> 1.36M exceeds 1.23M, so 0 JPY!
+        // 2026 Inhabitant Tax: assesses 2025 (Reiwa 7) income. Although exceeding standard 1.23M threshold,
+        // 1.36M falls within the Reiwa 7 buffer zone (<= 1.50M) for 特定親族特別控除 -> full 450,000 JPY
         long inhabitantTaxDeduction = engine.calculateInhabitantTaxDeduction(context, dependent, yearEnd2026);
-        assertThat(inhabitantTaxDeduction).isZero();
+        assertThat(inhabitantTaxDeduction).isEqualTo(450_000L);
+
+        // Beyond Reiwa 7 cutoff (> 1.88M, e.g. 1.89M) -> 0 JPY for 2026 inhabitant tax
+        dependent.setEstimatedAnnualIncome(1_890_000L);
+        assertThat(engine.calculateInhabitantTaxDeduction(context, dependent, yearEnd2026)).isZero();
     }
 
     @Test
diff --git a/finance-hub-backend/todo/pending fix/question-121.md b/finance-hub-backend/todo/pending fix/question-121.md
index e7d0a3ff..7d43e7f5 100644
--- a/finance-hub-backend/todo/pending fix/question-121.md
+++ b/finance-hub-backend/todo/pending fix/question-121.md
@@ -165,3 +165,55 @@ if (dependentIncome > threshold) {
   本調查已全面排除任何預設立場，完成了日本國稅廳法定法條、施行時程、所得稅與住民稅精確級距之客觀檢證，並提供了完整的架構選項與量化影響分析。
 - **決策權屬**：
   AI 助理不具備專案進度管理與功能排程之權限。是否實作「特定親族特別控除」、採行選項 A 或選項 B、以及是否需要分階段執行，**完全由 Product Manager（使用者兼 Scrum Master）全權裁定**。未經 PM 明確裁定指示前，不擅自推動下一步或修改程式碼。
+
+---
+
+### 六、實作執行記錄與落地總結（Execution Record & Verification）
+
+- **執行狀態**：✅ 已完成修正（Status: Fixed 2026-10-07）
+- **處理解法採行**：**選項 A（導入特定親族特別控除 1D 階梯查表與策略派發）**
+
+#### 1. 核心落地架構與法規對齊
+1. **策略基底擴充（AbstractDependentDeductionStrategy）**：
+   - 於 [AbstractDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java) 中，精準鎖定 `age >= 19 && age <= 22`（法定 19 歲以上 23 歲未滿）。
+   - 當薪資超過一般門檻時，不再直接歸零，而是委派鉤子方法 `calculateSpecificDependentSpecialIncomeTaxDeduction` 與 `calculateSpecificDependentSpecialInhabitantTaxDeduction`。
+   - 基類預設回傳 `0L`，確保令和 6 年以前（Legacy）與非 19～22 歲族群嚴格維持舊制行為。
+2. **令和 7 年分過渡查表（Reiwa7DependentDeductionStrategy）**：
+   - [Reiwa7DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java) 實作 123 萬至 188 萬打工年收之法定階梯。
+   - 支援 123 萬～150 萬所得稅滿額緩衝帶（63 萬）與 123 萬～160 萬住民稅滿額緩衝帶（45 萬），其後逐階遞減至 188 萬（3 萬）。
+3. **令和 8～9 年分新制特例查表（Reiwa8And9DependentDeductionStrategy）**：
+   - [Reiwa8And9DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java) 依日本國稅廳 No.1177 官方級距，實作 136 萬至 197 萬之法定階梯。
+   - 支援 136 萬～159 萬所得稅滿額緩衝帶（63 萬）與 136 萬～169 萬住民稅滿額緩衝帶（45 萬），其後自 169 萬起雙稅同步逐階遞減至 197 萬（3 萬），超過 197 萬歸零。
+   - 納稅義務人本人無所得限制（所得制限なし），為單純 1D 查表。
+4. **令和 10 年分起本則落日查表（Reiwa10OnwardsDependentDeductionStrategy）**：
+   - [Reiwa10OnwardsDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java) 忠實反映給與所得控除 5 萬特例落日（最低保證由 74 萬回歸 69 萬，基準由 136 萬移至 131 萬，緩衝帶至 154 萬，上限至 192 萬）。
+5. **課稅時差與工廠整合（Strategy Factory & Engine）**：
+   - [DependentDeductionStrategyFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyFactory.java) 與 [DependentDeductionEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java) 完全無縫調度：2026 模擬首年所得稅走 Reiwa 8-9（現年），住民稅走 Reiwa 7（前年）。
+
+#### 2. 異動檔案清單
+- [AbstractDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java)
+- [Reiwa7DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa7DependentDeductionStrategy.java)
+- [Reiwa8And9DependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa8And9DependentDeductionStrategy.java)
+- [Reiwa10OnwardsDependentDeductionStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/Reiwa10OnwardsDependentDeductionStrategy.java)
+- [DependentDeductionStrategyTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java)
+- [DependentDeductionEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java)
+
+#### 3. 測試驗證矩陣
+- **Reiwa 8 & 9 特例梯次驗證**：
+  - 打工收入 $\le$ 1.59M（緩衝帶）：所得稅 630K / 住民稅 450K。
+  - 打工收入 1.59M~1.64M（Step 1）：所得稅 610K / 住民稅 450K。
+  - 打工收入 1.64M~1.69M（Step 2）：所得稅 510K / 住民稅 450K。
+  - 打工收入 1.69M~1.74M（Step 3）：雙稅同步 410K / 410K。
+  - 打工收入 1.74M~1.79M（Step 4）：雙稅同步 310K / 310K。
+  - 打工收入 1.79M~1.84M（Step 5）：雙稅同步 210K / 210K。
+  - 打工收入 1.84M~1.89M（Step 6）：雙稅同步 110K / 110K。
+  - 打工收入 1.89M~1.94M（Step 7）：雙稅同步 60K / 60K。
+  - 打工收入 1.94M~1.97M（Final Step）：雙稅同步 30K / 30K。
+  - 打工收入 $>$ 1.97M：歸零（0L）。
+- **Reiwa 7 梯次驗證**：緩衝帶 $\le$ 1.50M（630K / 450K），末階 1.88M（30K / 30K），超過 1.88M 歸零。
+- **Reiwa 10+ 本則落日梯次驗證**：緩衝帶 $\le$ 1.54M（630K / 450K），末階 1.92M（30K / 30K），超過 1.92M 歸零。
+- **邊界與非特定年齡防護驗證**：
+  - 令和 6 年以前舊制（Legacy）：打工收入超過 1.03M 一律歸零，不適用特別控除。
+  - 17 歲（一般扶養親族）與 31 歲（一般成年親族）：收入超標時一律歸零，絕不誤用特定親族特別控除。
+- **引擎層時差整合驗證**：2026 年進行模擬時，20 歲在學打工子女年收 1.36M，所得稅給予 63 萬、住民稅課徵 2025 年所得享 45 萬；超過 1.88M 住民稅歸零。
+
diff --git a/finance-hub-backend/todo/planned/plan.md b/finance-hub-backend/todo/planned/plan.md
index b98b34de..81cd25c9 100644
--- a/finance-hub-backend/todo/planned/plan.md
+++ b/finance-hub-backend/todo/planned/plan.md
@@ -1,10 +1,7 @@
 # Financial Planning (FP) & AI Categorization Roadmap
 
 
-## 🎯 Epic: Phase 3 - Tax Management & Reporting
-
-> **為什麼是 Phase 3？**  
-> 稅務計算不影響「貸款可不可行」的即時判斷，但直接影響 Net Worth 的長期預測準確性，有了 Phase 2 的精確模擬引擎後，稅務才有意義做進去。
+## 🎯 Epic 1 - Tax Management & Reporting
 
 **Epic Scope**:
 處理複雜的稅務計算，包含現役時代的所得稅與住民稅推算、ESPP/RSU 確定申告與未來提領年金 (DC/iDeCo) 的稅務折損。同時也包含一般課稅投資帳戶的稅務影響。
@@ -51,9 +48,9 @@
 
 ---
 
-## 🎯 Epic: Phase 4 - Historical Actuals vs Future Budgeting (Insights & Suggestions)
+## 🎯 Epic 2 - Historical Actuals vs Future Budgeting (Insights & Suggestions)
 
-> **為什麼是 Phase 4？**  
+> **為什麼是 Epic 2？**  
 > 這個功能對「要不要買房」的決策沒有直接幫助，但對長期的財務規劃品質非常有價值。需要在 Phase 2-3 的模擬引擎穩定後再疊加此層洞察。
 
 **Epic Scope**:
@@ -75,7 +72,12 @@
 
 > 以下功能因為「已有 local import 機制可以手動匯入資料」而降低緊迫性，或者屬於純 UX 便利性改善，暫時移入 Backlog 不排入固定 Phase。
 
-### B1 多筆房貸獨立還款參數 (Per-Mortgage Override)
+### B1 多筆房貸獨立還款參數 (Per-Mortgage Override) [✅ 已由 GA 引擎實作完成]
+
+> **實作與演進狀態 (2026-10-07)**：
+> - **核心已完成**：本功能的核心理念已在 GA 最佳化求解器與 `SimulationOverrides.mortgageEarlyRepayments` 實作完成。舊版單一純量 `mortgageEarlyRepaymentYear` 已在 Fixed-44 廢除，改為每筆房貸獨立之 `Map<Long, List<MortgageEarlyRepaymentOverride>>`；GA 求解器（`Chromosome.mortgageRepayments`）已全面支援多筆房貸各自獨立演化尋優，並於 Fixed-124 延伸支援「縮短年限」與「減少月付金額」雙策略。
+> - **Grid Search 延伸**：原構想第 4、5 點提及的 Grid Search 局部搜尋，因多維組合數指數爆炸風險，已轉由 GA 基因演算法全域求解替代；Grid Search 引擎之多房貸維度延伸評估則獨立收錄於 [Plan 04](Plan-04-grid-search-multi-mortgage-dimension.md) 供未來參考。
+
 - **概念**：目前 `SimulationOverrides.mortgageEarlyRepaymentYear` 是一個全域變數，強制將所有房貸視為單一資金池。若使用者同時持有主宅房貸 (低利) 與投資物件房貸 (高利)，系統無法支援「僅提前清償高利貸款」的決策。
 - **功能**：
   1. 將全域的 `SimulationOverrides.mortgageEarlyRepaymentYear: Integer` 擴充為 `mortgageEarlyRepaymentYears: Map<String, Integer>`（以 `mortgageId` 為鍵）。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                                            10005  11.93G   5.49    16:08:16  