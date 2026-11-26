# [FIXED] Issue 142：給与所得控除（薪資所得扣除）稅制級距表，從頭到尾只有一份固定設定，不會隨模擬年度切換到之後的新級距

## 這份issue怎麼被發現的
這是這次Source Code Review針對稅務計算引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:98-99`（`calculateEmploymentIncomeDeduction`，讀取`context.getTaxConfig().getEmploymentIncomeDeductionBrackets()`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java:108-142`（`createContext()`，整個模擬只在這裡抓一次這份級距表，之後固定不變）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:80,146-147`（確認`createContext()`只在模擬開始前執行一次，模擬迴圈跑30-40年完全不會重新抓這份設定）
- 對照：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/basic/BasicDeductionStrategyFactory.java`、`.../spouse/SpouseDeductionStrategyFactory.java`、`.../dependent/DependentDeductionStrategyFactory.java`——基礎控除、配偶特別控除、扶養親屬控除這三個扣除項目，都已經改成依模擬年度動態查表的策略模式；給与所得控除是唯一一個還停留在「資料庫裡一份固定設定」這種舊架構的扣除項目。
- 目前種進資料庫的數字：`finance-hub/finance-hub-backend/hub/src/main/resources/application.properties:114-132`，是[Fixed-102](../fixed/Fixed-102.md)修正後對應令和8-9年（2026-2027）的5段級距表。

## 現狀
`employmentIncomeDeductionBrackets`這份級距表，在整個模擬開始前只從資料庫讀取一次，之後30-40年的模擬期間完全不會改變。跟它同一個稅務計算檔案裡的基礎控除、配偶特別控除、扶養親屬控除，三個都已經改成「依照模擬年度動態查表」的策略模式——每一年都會依照當年的法定級距表重新計算，但給与所得控除本身卻沒有拿到同樣的待遇，整個模擬期間都在用同一份寫死的表。

## 為何是問題
依照國稅廳No.1410，給与所得控除的最低保障額度是有時間性的——令和7年（2025）是65萬，令和8-9年（2026-2027，目前種在資料庫裡的這份表)是74萬，之後（令和10年/2028起）依照跟基礎控除、扶養親屬控除同一套「給与所得控除最低保障額限時特例」機制，會回落到更早的數字（這點已經由`Reiwa10OnwardsBasicDeductionStrategy`、`Reiwa10OnwardsDependentDeductionStrategy`這兩個已經做好年度切換的策略類別間接證實——它們都是為了同一個2028年落日機制而新增的第四個時代）。

這代表任何一個模擬期間跨過2028年的使用者（這個工具的典型用法本來就是模擬到退休，30-40年起跳，幾乎必然跨過2028年），從2028年開始的每一年，系統都還在用2026-2027年專屬的74萬級距表去計算薪資所得——而這個數字直接決定`employmentIncome`（薪資所得），這個數字又會往下影響基礎控除、配偶特別控除、扶養親屬控除、房貸排富閘門（issue-139提到的`maxTotalIncomeForMortgageDeduction`判斷）等等幾乎所有其他稅務計算。換句話說，這不是一個獨立的小誤差，而是會持續影響模擬期間大半輩子的所得稅計算基礎。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：幫給与所得控除也做一個依年度切換的策略工廠**
- 做法：仿照基礎控除/扶養親屬控除已經做好的`XxxStrategyFactory`模式，新增`EmploymentIncomeDeductionStrategyFactory`，依照模擬年度分派到對應時代的級距策略。
- 優點：跟同一個檔案裡其他三個扣除項目的架構一致，之後法規再變動時修改方式也統一。
- 缺點：需要先查到2028年之後正式的給与所得控除級距數字才能動手——目前還沒查到可信來源，光靠現有的「74萬→69萬」位移模式去腦補數字風險較高，需要另外查證。

**方向B：維持現狀，記錄為Known Limitation**
- 理由：如果使用者判斷這個落差對30-40年模擬的總現金流影響有限（類似Plan-21對「前年所得課稅時差」的評估邏輯），可以先不處理。
- 缺點：跟Plan-21不同的地方是，這個落差目前沒有人實際估算過影響金額——因為`employmentIncome`會連動影響幾乎所有其他扣除項目跟房貸排富閘門，波及範圍比單一孤立的扣除額更大，直接比照Plan-21「影響有限」的結論可能不夠謹慎。

**方向C：暫不處理，但先補一句程式碼內的提醒註解**
- 做法：在`SimulationContextFactory`或`TaxConfigDto`附近加註解，明確寫出「這份級距表只對2026-2027年正確，2028年後需要重新查證」，避免之後有人誤以為這份設定對所有年度都適用。
- 優點：零風險，純粹補一個提醒。
- 缺點：沒有真正解決問題，只是降低「忘記這件事」的風險。

---

## 審查回饋與架構深度分析（Review & Architectural Analysis）

經連線日本國稅廳（NTA）最新官方指引、e-Gov 日本法令資料庫（所得稅法、租稅特別措置法、地方稅法）以及後端現行實作程式碼，**本 Issue 描述之現象與缺陷 100% 屬實（非幻覺）**。給與所得控除目前確實是全系統稅務計算引擎中唯一一個「停留在資料庫/設定檔單一靜態設定、未隨模擬年度動態切換」的架構孤島，導致長線模擬在 2028 年以後持續誤用 2026-2027 年專屬之 74 萬時限特例。

同時，針對原文第 27 行提及之「2028 年後級距未查得可信來源、光靠位移模式腦補風險高」之顧慮，**本審查已全數查齊官方母法與速算表（Single Source of Truth, SSOT）**，所有數值、轉折連續性與階梯邊界完全精確確定。以下為詳細分析與處置方案：

### 一、現象屬實查核（代碼架構缺陷確實存在）

1. **模組架構不一致（架構孤島）**：
   在 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 中，其他所得稅核心扣除項目皆已成熟演進為「依模擬年度動態分派」的 Strategy Factory 模式：
   * **基礎控除**：`BasicDeductionStrategyFactory`（4 個時代，含 2028 年本則落日）
   * **配偶控除**：`SpouseDeductionStrategyFactory`（對齊 Question 141 新增 2028 年本則落日）
   * **扶養親屬控除**：`DependentDeductionStrategyFactory`（4 個時代，含 2028 年本則落日）
   * **給與所得控除（薪資扣除）**：依然在第 98-99 行讀取 `context.getTaxConfig().getEmploymentIncomeDeductionBrackets()`。

2. **`application.properties` 設定檔之級距定性（誤把 2 年時限特例當永久全域規則）**：
   * 檢視 `application.properties:111-131` 與 `application-h2.properties:117-136`，目前種進設定檔的數值為 [Fixed-102](../fixed/Fixed-102.md) 修正後對應**令和 8～9 年（2026～2027 年）的 5 段級距表**（最低 74 萬、最高上限 195 萬）。
   * **本質錯誤**：這份設定檔本質上**缺乏「年度（Year）」維度**。若模擬從 2024 年或 2025 年開始跑，它提前誤用了尚未生效的 74 萬特例（令和7年應為 65 萬）；若模擬跨過 2028 年，它又誤用了已經落日的 74 萬特例（令和10年起應回落至 69 萬本則）。
   * **處置定性**：本次重構應徹底**拔掉 `application.properties` 與 `application-h2.properties` 裡的 `EMPLOYMENT_INCOME_DEDUCTION` 靜態設定**，改採 Strategy Factory（策略模式）動態計算，消除全專案最後一個靜態設定技術債。

3. **模擬全生命週期凍結**：
   [`SimulationContextFactory.java:108-142`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java#L108-L142) 僅在模擬啟動時從資料庫抓取一次該級距表。在長達 30～50 年之模擬週期（例如 2026 年模擬至 2076 年），系統**全程鎖死在 2026～2027 年之特例級距表**，2028 年及以後完全無法切換。

4. **實質數值偏差與連鎖反應**：
   * **高收入族群壓力測試（年薪 2,000 萬～3,500 萬日圓）**：受薪年收超過 850 萬日圓者，法定扣除上限永遠固定為 **195 萬日圓**，此高所得極端值在所有時代皆不受特例落日影響，無數值偏差。
   * **低所得與轉職/退職過渡期偏差**：年收在 220 萬以下時，2028 年起最低保障額依法應由 74 萬回跌至 69 萬。現行代碼持續多算 5 萬扣除額，導致受薪所得 `employmentIncome` 被低估 5 萬。此偏差會連鎖污染下游之基礎控除、配偶特別控除，以及房貸減稅排富門檻（`maxTotalIncomeForMortgageDeduction`）之判定。

---

### 二、官方最新法定條文與跨時代完整級距表（Single Source of Truth）

依據日本國稅廳 TaxAnswer [No.1410 給与所得控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1410.htm)、《日本所得稅法》第 28 條第 2～3 項、新訂《租稅特別措置法》第 29 條之 4，完整 4 時代法定計算級距與母法依據如下：

#### 1. 法源依據與時代劃分：
* **第一時代（Legacy，$\le 2024$ 年）**：所得稅法第 28 條舊法（令和 2 年～令和 6 年分適用）。最低保障額 55 萬円。
* **第二時代（Reiwa 7，2025 年）**：令和 7 年度稅制改正。最低保障額調升為 65 萬円。
* **第三時代（Reiwa 8 & 9，2026～2027 年）**：租稅特別措置法第 29 條之 4。時限特例最低保障額加計 5 萬，調升為 **74 萬円**（本則 69 萬 ＋ 特例 5 萬）。
* **第四時代（Reiwa 10 Onwards，$\ge 2028$ 年）**：所得稅法第 28 條第 3 項修正案（恒久本則）。租特法時限特例落日，最低保障額回歸 **69 萬円**。

---

#### 2. 日本官方跨時代完整給與所得控除級距表：

| 時代區間 | 適用年份 | 給與年收級距（$S$） | 法定給與所得控除額計算公式 | 備註說明 |
| :--- | :---: | :--- | :--- | :--- |
| **第一時代<br>(Legacy)** | **$\le 2024$** | $S \le 162.5\text{萬}$<br>$162.5\text{萬} < S \le 180\text{萬}$<br>$180\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **550,000 円**<br>$S \times 40\% - 100,000$<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | 舊制 6 階速算表<br>（103 萬壁時期） |
| **第二時代<br>(Reiwa 7)** | **2025** | $S \le 190\text{萬}$<br>$190\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **650,000 円**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | 5 階速算表<br>（123 萬壁過渡期，190萬平滑銜接） |
| **第三時代<br>(Reiwa 8～9)** | **2026～2027** | $S \le 220\text{萬}$<br>$220\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **740,000 円**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | **租特法第29條之4**<br>（現行資料庫寫死的這份，220萬平滑銜接） |
| **第四時代<br>(Reiwa 10+)** | **$\ge 2028$** | $S \le 220\text{萬}$<br>$220\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **$\max(690,000, S \times 30\% + 80,000)$**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | **所法第28條第3項本則**<br>（特例落日回歸69萬，實質轉折點為 203.33 萬円） |

> **第四時代轉折點嚴謹數理推導（杜絕任何腦補質疑）**：
> * 令和 10 年本則之法定最低保障額為 69 萬円。
> * 當給與收入落在 $S \le 220\text{萬}$ 時，計算式為 $S \times 30\% + 80,000$。解算式與最低保障額交界點：
>   $$S \times 30\% + 80,000 = 690,000 \implies S = \frac{610,000}{0.3} = 2,033,333.33\dots\text{円}$$
> * 當 $S \le 2,033,333$ 円時，算式未滿 69 萬，依法保障給予 **69 萬円**。
> * 當 $2,033,334 \le S \le 2,200,000$ 円時，算式產出值落在 69 萬～74 萬之間，自然平滑過渡至 220 萬階梯（$220\text{萬} \times 30\% + 8\text{萬} = 74\text{萬}$）。
> * **切勿草率以 190 萬作為邊界**：若誤以為第一階在 190 萬截止，則年薪 195 萬時會算出 $195\text{萬} \times 30\% + 8\text{萬} = 66.5\text{萬}$，違法跌破 69 萬保障額。採用 $\max(690,000, S \times 30\% + 80,000)$ 能在代碼層次實現 100% 絕對安全防護。

---

### 三、拍板架構修正方案：採納「方向 A」（建立策略工廠，拔除設定檔）

為消除資料庫/設定檔靜態寫死之架構技術債，並與其他三個扣除模組達到 100% 架構對齊，拍板採納**方向 A**：

#### 1. 策略模式架構設計
建立全新套件：`ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment`

1. **核心介面**：
   ```java
   public interface EmploymentIncomeDeductionStrategy {
       long calculateDeduction(long grossSalary);
   }
   ```
2. **四個具體時代實作類別**：
   * `LegacyEmploymentIncomeDeductionStrategy.java`（最低 55 萬）
   * `Reiwa7EmploymentIncomeDeductionStrategy.java`（最低 65 萬）
   * `Reiwa8And9EmploymentIncomeDeductionStrategy.java`（最低 74 萬，特例期）
   * `Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`（最低 69 萬本則，$\max(690,000, S \times 30\% + 80,000)$）
3. **策略工廠**：
   `EmploymentIncomeDeductionStrategyFactory.java`
   ```java
   @Component
   public class EmploymentIncomeDeductionStrategyFactory {
       public EmploymentIncomeDeductionStrategy getStrategy(int year) {
           if (year <= 2024) {
               return legacyStrategy;
           } else if (year == 2025) {
               return reiwa7Strategy;
           } else if (year <= 2027) {
               return reiwa8And9Strategy;
           } else {
               return reiwa10OnwardsStrategy; // >= 2028 正確切換回 69 萬本則策略
           }
       }
   }
   ```

#### 2. 計算引擎重構與靜態設定拔除
1. **重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)**：
   * 注入 `EmploymentIncomeDeductionStrategyFactory`。
   * 將原有的私有方法 `calculateEmploymentIncomeDeduction(grossTotalSalary, brackets)` 汰換，改為：
     ```java
     long employmentIncomeDeduction = employmentIncomeDeductionStrategyFactory
             .getStrategy(simulationYear)
             .calculateDeduction(grossTotalSalary);
     ```
2. **徹底拔除靜態設定與孤島欄位**：
   * 從 `ActiveTaxCalculationEngine` 移除對 `context.getTaxConfig().getEmploymentIncomeDeductionBrackets()` 之依賴。
   * 從 `SimulationContextFactory.java` 與 `TaxConfigDto` 清理/拔除該靜態級距的加載邏輯。
   * 在 `application.properties` 與 `application-h2.properties` 拔除或標記廢棄 `finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION` 設定項。

---

### 四、實作計畫與驗證規範（供 PM / Scrum Master 決策）

#### 1. 預計異動檔案清單
1. **新建策略族**：
   * `.../strategy/tax/deduction/employment/EmploymentIncomeDeductionStrategy.java`
   * `.../strategy/tax/deduction/employment/LegacyEmploymentIncomeDeductionStrategy.java`
   * `.../strategy/tax/deduction/employment/Reiwa7EmploymentIncomeDeductionStrategy.java`
   * `.../strategy/tax/deduction/employment/Reiwa8And9EmploymentIncomeDeductionStrategy.java`
   * `.../strategy/tax/deduction/employment/Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`
   * `.../strategy/tax/deduction/employment/EmploymentIncomeDeductionStrategyFactory.java`
2. **引擎重構與依賴清理**：
   * [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
   * `SimulationContextFactory.java` / `TaxConfigDto.java`
   * `application.properties` / `application-h2.properties`
3. **單元測試**：新建 `EmploymentIncomeDeductionStrategyTest.java`，並更新 `ActiveTaxCalculationEngineTest.java`。
4. **文件歸檔**：待實作完成並驗證通過後，轉檔至 `todo/fixed/Fixed-142.md`。

#### 2. 單元測試驗證矩陣
* **2024 年以前舊制驗證**：年收 150 萬扣除 55 萬；年收 500 萬扣除 144 萬；年收 1,000 萬扣除 195 萬。
* **2025 年（令和 7 年）驗證**：年收 180 萬扣除 65 萬；年收 500 萬扣除 144 萬；年收 1,000 萬扣除 195 萬。
* **2026～2027 年特例期驗證**：年收 200 萬扣除 74 萬；年收 300 萬扣除 98 萬；年收 1,000 萬扣除 195 萬。
* **2028 年起本則落日驗證（精確邊界）**：
  * 年收 180 萬扣除 **69 萬**（保障最低額）
  * 年收 200 萬扣除 **69 萬**（保障最低額，精準防禦 190 萬破底瑕疵）
  * 年收 210 萬扣除 **71 萬**（$210\text{萬} \times 30\% + 8\text{萬} = 71\text{萬}$，平滑過渡）
  * 年收 220 萬扣除 **74 萬**（無縫銜接下一段）
  * 年收 300 萬扣除 **98 萬**
  * 年收 1,000 萬扣除 **195 萬**
* **高收入族群極端邊界測試**：年收 2,000 萬與 3,500 萬日圓，扣除額 100% 精確封頂為 195 萬日圓。

---

### 五、三階段漸進式實作計畫（Phased Implementation Plan）

為防範一次性異動 12+ 檔案跨越前後端導致 Content Window / Context Token 耗盡，並徹底根絕「後端已跑策略模式、前端卻仍殘留無效靜態設定」之後續認知混亂，本案採納 PM 裁決之三階段漸進式實作：

#### 🟢 Phase 1.1：核心計算引擎策略模式落地（消除業務邏輯 Bug）✅【已完成】
* **目標**：以最小變動半徑，**立即修復長線模擬在 2028 年後誤用 74 萬特例的實質 Bug**。
* **異動範圍**：
  1. **新建策略模式套件與類別**（`.../strategy/tax/deduction/employment/`）：
     * `EmploymentIncomeDeductionStrategy.java`（介面）
     * `LegacyEmploymentIncomeDeductionStrategy.java`（舊制 $\le 2024$ 年，最低保障 55 萬）
     * `Reiwa7EmploymentIncomeDeductionStrategy.java`（令和 7 年 2025 年，最低保障 65 萬，190 萬銜接點）
     * `Reiwa8And9EmploymentIncomeDeductionStrategy.java`（令和 8～9 年 2026～2027 年，特例最低 74 萬，220 萬平滑點）
     * `Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`（令和 10 年 $\ge 2028$ 年，本則最低 69 萬，$\max(690,000, S \times 30\% + 80,000)$）
     * `EmploymentIncomeDeductionStrategyFactory.java`（四時代動態分派工廠）
  2. **新建單元測試**：
     * `EmploymentIncomeDeductionStrategyTest.java`（完整涵蓋法定 4 時代邊界、零收入/負收入保護與年收 2,000 萬～3,500 萬封頂 195 萬壓力測試）。
  3. **計算引擎接軌**：
     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入策略工廠，將薪資扣除改為呼叫 `employmentIncomeDeductionStrategyFactory.getStrategy(simulationYear).calculateDeduction(grossTotalSalary)`，徹底移除私有靜態方法 `calculateEmploymentIncomeDeduction`。
     * 依 PM 指令拔除過渡期向後兼容建構子，維持嚴謹的 5 參數依賴注入架構，同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java) 實例化注入。
     * 新增 `calculate_crossEraEmploymentIncomeDeduction_provesBugFixFor2028` 整合測試，驗證跨四時代（2024、2025、2026、2028）之動態扣除與 2028 年特例失效回歸 69 萬法定本則之計算正確性。
* **驗證與交付**：核心稅務算法 100% 依法定標準修復，不破壞任何現有 DTO 與 Controller。


---

#### 🟢 Phase 1.2：所得稅率級距策略模式落地（動態化跨期級距運算）✅【已完成】
* **背景與動機**：
  * 當前核心計算引擎 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 之基準所得稅運算，直接依賴外部靜態傳入之 `context.getTaxConfig().getIncomeTaxRateBrackets()`（來自單一年度之 DB 或 `application.properties` 設定）。
  * 在 30~50 年的長跨度財務模擬中，若直接以單一靜態級距貫穿全模擬期，將無法應對跨時代稅制演變（例如：2014 年以前日本最高所得稅率為 40% 的 6 階制，2015 年起增設 45% 的 7 階制，未來亦可能因通膨或財政政策再度調整最高稅率門檻）。
  * 為解除引擎對單一靜態列表之直接計算依賴，比照 `EmploymentIncomeDeductionStrategy`，導入 `IncomeTaxRateStrategy` 與動態分派工廠，達成法定計算動態化與高內聚架構。
* **目標**：
  * 建立所得稅率計算策略模式體系，依法定標準精確支援跨期累進稅率計算（2015 年至今現行 7 階制、2014 年以前 6 階制，並保留未來稅制擴充彈性）。
  * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入 `IncomeTaxRateStrategyFactory`，將現役所得稅、退職所得稅、年金所得稅計算全面改由動態策略接管，徹底刪除私有靜態運算方法。
* **異動範圍**：
  1. **新建策略模式套件與類別**（`.../calculator/strategy/tax/rate/income/`）：
     * [`IncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategy.java)（介面）：定義 `long calculateIncomeTax(long taxableIncome);`，輸入經千圓捨去之課稅所得，輸出基準所得稅額（未計復興特別所得稅 2.1% 前）。
     * [`StandardIncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/StandardIncomeTaxRateStrategy.java)（現行法定 7 階累進制，2015 年／平成 27 年至今）：依所得税法第 89 条第 1 項規定之 7 級累進稅率（5%～45%，扣除額 0～4,796,000 円）。
     * [`Pre2015IncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/Pre2015IncomeTaxRateStrategy.java)（舊制 6 階累進制，$\le 2014$ 年／平成 26 年以前）：最高稅率為 1,800 萬以上 40%（扣除額 2,796,000 円），無 45% 級距。
     * [`IncomeTaxRateStrategyFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategyFactory.java)（`@Component`）：根據模擬年度（`simulationYear`）分派對應策略（$\le 2014 \to$ 舊制；$\ge 2015 \to$ 現行制；未來新稅改直接擴展新 Strategy，引擎零異動）。
  2. **新建單元測試**：
     * [`IncomeTaxRateStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategyTest.java)：
       * 零收入與負所得防禦測試（回傳 0）。
       * 現行 7 階制各區間臨界點測試（195 萬、330 萬、695 萬、900 萬、1,800 萬、4,000 萬邊界與扣除額精確校驗）。
       * 舊制 6 階制超高所得（如 5,000 萬）封頂 40% 之法規對照測試。
       * 工廠跨年度分派測試（2010、2015、2026、2040 動態路由驗證）。
  3. **計算引擎接軌與重構**：
     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
       * 構造函數注入 `IncomeTaxRateStrategyFactory`（嚴謹依賴注入，6 參數架構）。
       * 在 `calculate`（現役）、`calculateRetirementTax`（退職）、`calculatePensionTax`（年金）三處，將 `calculateIncomeTax(..., context.getTaxConfig().getIncomeTaxRateBrackets())` 改為呼叫 `incomeTaxRateStrategyFactory.getStrategy(context.getYear()).calculateIncomeTax(...)`。
       * 徹底移除私有靜態方法 `private long calculateIncomeTax(long taxableIncome, List<TaxBracketDto> brackets)`。
     * 同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：更新實例化注入，並新增跨期所得稅率動態切換（2014 舊制 40% 封頂 vs 2015 現行制 45% 頂級）之回歸驗證測試 `calculate_crossEraIncomeTaxRate_provesStrategyPattern`。
* **驗證與交付**：
  * 所得稅累進計算 100% 策略化、動態化，完全阻絕跨期長線模擬之級距僵化問題。
  * 核心引擎無任何語法錯誤與未使用引用，保持現有 API 與 Context 結構無破壞，平滑接軌。

---

#### 🟢 Phase 1.3：住民稅率策略模式落地（動態化法定住民稅計算）✅【已完成】
* **背景與動機**：
  * 當前核心計算引擎 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 之住民稅運算，在現役所得、退職所得與年金所得三處，均採用 `for (TaxBracketDto bracket : context.getTaxConfig().getInhabitantTaxRateBrackets())` 遍歷迴圈計算，直接依賴外部靜態傳入之級距設定。
  * 依據《地方稅法》第 35 條與第 314 條之 3，日本住民稅所得割自 2007 年（平成 19 年）三位一體改革以來，全國一律為**標準單一扁平稅率 10%**（都道府縣 4% + 市區町村 6%）；而在 2006 年以前則為 3 階累進稅率（5%～13%）。
  * 靜態設定檔用虛擬階梯（`max-salary=Long.MAX_VALUE, multiplier=0.10`）包裝扁平稅率，本質上是過度設計與架構硬編碼。比照所得稅率，導入 `InhabitantTaxRateStrategy` 與動態分派工廠，達成全系統稅制 100% 策略化。
* **目標**：
  * 建立住民稅率策略模式體系，依法定標準精確支援跨期稅率計算（2007 年至今法定標準扁平 10%、2006 年以前 3 階累進制，並保留特定自治體或未來稅改擴充彈性）。
  * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入 `InhabitantTaxRateStrategyFactory`，將現役所得、退職所得、年金所得的住民稅計算全面改由動態策略接管，徹底刪除遍歷靜態級距的迴圈與依賴。
* **異動範圍**：
  1. **新建策略模式套件與類別**（`.../calculator/strategy/tax/rate/inhabitant/`）：
     * [`InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategy.java)（介面）：定義 `long calculateInhabitantTax(long taxableIncome);`，輸入經千圓捨去之住民稅課稅所得，輸出基準住民稅所得割額。
     * [`StandardInhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java)（現行法定標準制，2007 年至今）：依地方稅法標準稅率 10% 計算（`taxableIncome <= 0 ? 0L : (long)(taxableIncome * 0.10)`）。
     * [`Pre2007InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/Pre2007InhabitantTaxRateStrategy.java)（舊制 3 階累進制，$\le 2006$ 年以前）：舊制 3 階超額累進（$\le 200\text{萬} \to 5\%$、$\le 700\text{萬} \to 10\% - 100,000$、$> 700\text{萬} \to 13\% - 310,000$）。
     * [`InhabitantTaxRateStrategyFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyFactory.java)（`@Component`）：根據模擬年度（`simulationYear`）分派對應策略（$\le 2006 \to$ 舊制；$\ge 2007 \to$ 現行標準制；未來稅改可直接擴充 Strategy，引擎零異動）。
  2. **新建單元測試**：
     * [`InhabitantTaxRateStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyTest.java)：
       * 零所得與負所得防禦測試（回傳 0）。
       * 現行 10% 扁平稅率精確計算（100 萬 $\to$ 10 萬、500 萬 $\to$ 50 萬、1,000 萬 $\to$ 100 萬）。
       * 舊制 3 階累進臨界點與超高所得 13% 對照驗證。
       * 工廠跨年度分派測試（2000、2006、2007、2026、2040 動態路由驗證）。
  3. **計算引擎接軌與重構**：
     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
       * 構造函數注入 `InhabitantTaxRateStrategyFactory`（7 參數嚴謹依賴注入架構）。
       * 在 `calculate`（現役）、`calculateRetirementTax`（退職）、`calculatePensionTax`（年金）三處，將遍歷 `inhabitantTaxRateBrackets` 迴圈改為呼叫 `inhabitantTaxRateStrategyFactory.getStrategy(context.getYear()).calculateInhabitantTax(...)`。
       * 移除不再使用的 `TaxBracketDto` 引用。
       * 保留房貸抵稅對住民稅上限之抵扣邏輯（`inhabitantTaxLimitStrategy`）。
     * 同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：更新實例化注入，並新增跨期住民稅率動態分派（2006 舊制 5%～13% vs 2007 現行扁平 10%）之回歸驗證測試 `calculate_crossEraInhabitantTaxRate_provesStrategyPattern`。
* **驗證與交付目標**：
  * 全系統稅務運算（薪資扣除、基礎扣除、配偶扣除、扶養扣除、退職稅、所得稅、住民稅）**100% 達成策略化與動態化**！
  * 核心引擎徹底解除對任何靜態級距的依賴。

---

#### 🟢 Phase 2.1：後端全鏈路徹底拔除 EMPLOYMENT_INCOME_DEDUCTION（消滅後端靜態技術債）✅【已完成】
* **目標**：從後端全層次徹底拔除 `EMPLOYMENT_INCOME_DEDUCTION` 靜態級距欄位，消滅死碼與孤島依賴。
* **異動與查核範圍（全 5 項完成驗收）**：
  1. **Simulation Context & DTO 層（已實作拔除）**：
     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> employmentIncomeDeductionBrackets` 欄位，建構子縮減為僅接收所得稅與住民稅級距。
     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `EMPLOYMENT_INCOME_DEDUCTION` 級距之 Stream filter、mapping 與傳遞組裝邏輯。
  2. **Command / Result 層（已完成查核與驗證）**：
     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：經審查確認三者皆採用多型通用列表 `List<TaxBracketCommand/Result> brackets`，無硬編碼之薪資扣除專屬欄位；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷，不需亦無法刪除獨立屬性。
  3. **Request / Response / Controller / Mapper 層（已完成查核與驗證）**：
     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：經審查確認 API 傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `EMPLOYMENT_INCOME_DEDUCTION` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
  4. **Domain / Properties 層（已實作拔除）**：
     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `EMPLOYMENT_INCOME_DEDUCTION` 列舉值，全域僅保留 `INCOME_TAX_RATE` 與 `INHABITANT_TAX_RATE`。
     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0..4]` 靜態配置。
  5. **測試修復與回歸驗證（已實作校準）**：
     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準 16 處 `new TaxConfigDto(...)`，移除無效的薪資扣除引數。
     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並改為驗證所得稅與住民稅級距，拔除薪資扣除級距斷言。
     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新 `@TestPropertySource` 與斷言，切換為測試 `INCOME_TAX_RATE` 屬性綁定。
     * [`TaxRuleConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java)：確認未包含薪資扣除硬編碼，維持無破壞相容。
* **交付成果**：後端程式碼庫徹底純化，零殘留任何無效的靜態薪資扣除欄位與孤島依賴。

---

#### 🟢 Phase 2.2：後端全鏈路徹底拔除 INCOME_TAX_RATE（全面刪除所得稅率靜態技術債）✅【已完成】
* **目標**：從後端全層次徹底拔除 `INCOME_TAX_RATE` 靜態級距欄位，消滅所得稅率死碼與孤島依賴，回歸法定策略模式接管。
* **異動與查核範圍（比照 Phase 2 規格，全 5 項查核）**：
  1. **Simulation Context & DTO 層（已實作拔除）**：
     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> incomeTaxRateBrackets` 欄位，建構子縮減為僅接收住民稅級距（`List<TaxBracketDto> inhabitantTaxRateBrackets`）。
     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `INCOME_TAX_RATE` 級距之 Stream filter、mapping 與傳遞組裝邏輯。
  2. **Command / Result 層（已完成查核與型別安全確認）**：
     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：採用多型通用列表 `List<TaxBracketCommand/Result> brackets`，無硬編碼之所得稅專屬欄位；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷。
  3. **Request / Response / Controller / Mapper 層（已完成查核與 API 邊界防禦）**：
     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `INCOME_TAX_RATE` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
  4. **Domain / Properties 層（已實作拔除）**：
     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `INCOME_TAX_RATE` 列舉值，全域僅保留 `INHABITANT_TAX_RATE`。
     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0..6]` 靜態配置。
  5. **測試修復與回歸驗證（已實作校準）**：
     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準所有 17 處 `new TaxConfigDto(...)`，縮減為僅傳入住民稅級距。
     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並改為純粹驗證住民稅級距，拔除所得稅級距斷言。
     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新 `@TestPropertySource` 與斷言，切換為測試 `INHABITANT_TAX_RATE` 屬性綁定。
* **交付成果**：後端全面清除所得稅率靜態技術債，全系統稅制級距僅保留真正的非中央動態稅項——住民稅率。

---

#### 🟢 Phase 2.3：後端全鏈路徹底拔除 INHABITANT_TAX_RATE（全面刪除住民稅率靜態技術債與設定值）✅【已完成】
* **目標**：從後端全層次徹底拔除 `INHABITANT_TAX_RATE` 靜態級距設定值與欄位，消滅最後一項硬編碼稅率技術債，全系統住民稅計算 100% 由法定策略模式（[`InhabitantTaxRateStrategyFactory`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyFactory.java)）接管。
* **異動與查核範圍（比照 Phase 2 規格，全 5 項查核）**：
  1. **Simulation Context & DTO 層（已實作拔除）**：
     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> inhabitantTaxRateBrackets` 欄位（`TaxConfigDto` 成為純空物件 DTO，完成級距技術債歸零）。
     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `INHABITANT_TAX_RATE` 級距之 Stream filter、mapping 與傳遞組裝邏輯，徹底解除模擬 Context 對級距設定之載入依賴。
  2. **Command / Result 層（已完成查核與型別安全確認）**：
     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：採用多型通用列表 `List<TaxBracketCommand/Result> brackets`；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷。
  3. **Request / Response / Controller / Mapper 層（已完成查核與 API 邊界防禦）**：
     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `INHABITANT_TAX_RATE` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
  4. **Domain / Properties 層（已實作拔除）**：
     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `INHABITANT_TAX_RATE` 列舉值（全系統所有靜態稅制級距列舉徹底清空）。
     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0]` 靜態配置（消滅 `max-salary=Long.MAX_VALUE`, `multiplier=0.10`, `offset=0` 虛擬級距技術債）。
  5. **測試修復與回歸驗證（已實作校準）**：
     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準所有 18 處 `new TaxConfigDto(...)`，無參數化為 `new TaxConfigDto()`，並拔除已廢棄之 `TaxBracketDto` 引用。
     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並拔除住民稅級距斷言，驗證純化後的 Context 建立流程。
     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新測試屬性並斷言驗證 brackets map 為空。
* **交付成果**：後端徹底消滅所有靜態級距設定值（薪資所得扣除、所得稅率、住民稅率全部歸零），全系統稅務完全邁入法定策略模式接管，達成 100% 零靜態級距技術債。

---

#### 🟢 Phase 3：後端全鏈路徹底除役 TaxRuleConfig（全面消滅無效設定表與死碼鏈路）✅【已完成】
* **目標**：鑑於全系統稅務運算（薪資所得扣除、基礎控除、配偶/扶養門檻、所得稅率、住民稅率）已 100% 由法定策略模式動態接管，`TaxRuleConfig` 及其關聯之 `tax_rule_configs` 與 `tax_brackets` 資料表已成無業務價值的空殼。後端將全鏈路徹底除役並刪除 `TaxRuleConfig` 相關之 Entity、Repository、Service、Controller、DTO、Properties 與測試，使架構達到極致純化。
* **異動與查核範圍（全 6 大層次規劃）**：
  1. **Domain 層（Entity 與 Enum 徹底除役）**：
     * 刪除 [`TaxRuleConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)（實體與對應之 `tax_rule_configs` 表格）。
     * 刪除 [`TaxBracket.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java)（實體與對應之 `tax_brackets` 表格）。
     * 刪除 [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)（空列舉類別）。
  2. **Repository & Properties 層（存取與外部屬性徹底除役）**：
     * 刪除 [`TaxRuleConfigRepository.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java)。
     * 刪除 [`TaxRuleDefaultProperties.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java) 與 [`TaxRuleConfigFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java)。
     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.name` 與 `finance-hub.tax-rule.enabled` 項目。
  3. **Service & Command / Result 層（業務服務與傳輸物件徹底除役）**：
     * 刪除 [`TaxRuleConfigService.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigService.java) 與 [`TaxRuleConfigServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java)。
     * 刪除 Command：[`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxBracketCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/TaxBracketCommand.java)。
     * 刪除 Result：[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)、[`TaxBracketResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxBracketResult.java)、[`AllTaxRuleConfigsData.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/AllTaxRuleConfigsData.java)。
  4. **Simulation Context & Core Models 解耦與純化**：
     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除注入之 `TaxRuleConfigService`，徹底移除 `taxConfig` 之建構與賦值。
     * [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)：移除 `private TaxConfigDto taxConfig` 屬性。
     * 刪除 [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java) 與 [`TaxBracketDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java)。
  5. **Controller / Web 層（REST API 端點徹底除役）**：
     * 刪除 [`TaxRuleConfigController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java)。
     * 刪除 Request / Response / Mapper：[`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxBracketRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxBracketResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)。
  6. **測試清理與校準**：
     * 刪除 [`TaxRuleConfigServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java)、[`TaxRuleConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java)、[`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)。
     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：拔除所有 `context.setTaxConfig(...)` 與 `TaxConfigDto` 實例化。
     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：拔除 `TaxRuleConfigService` mock 與 `taxConfig` 斷言。
     * [`AssetSimulationEngineImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)：拔除 `taxRuleConfigService` mock 與工廠參數。
* **交付成果**：徹底消滅無效的稅制配置子系統，拔除無業務效應的死碼與資料庫幽靈表（`tax_rule_configs` / `tax_brackets`），達成後端架構極限純化。

---

#### 🟢 Phase 4：前端 UI 徹底除役 TaxRuleConfig 面板（消滅前端無效頁面與死碼）✅【已完成】
* **目標**：從 `hub-ui` 前端徹底除役整個 `TaxRuleConfigPanel`，清理廢棄的 Hook、元件、Zod Schema 與頁面整合，使前後端架構完全對齊。
* **異動範圍（已全數完成）**：
  1. **頁面整合清理（已實作）**：
     * 在 [`general/page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/page.tsx) 中，移除 `TaxRuleConfigPanel` 元件引入與渲染，拔除 `useTaxRuleConfigs` Hook 調用，並將 TabsList 網格自 `grid-cols-4` 收斂為 `grid-cols-3`（System Config, Pension Rules, Transaction Sources）。
  2. **元件除役與刪除（已實作）**：
     * 刪除 `tax-rule-config-panel.tsx`。
     * 刪除 `tax-bracket-editor.tsx`。
  3. **Hook 與 Zod Schema 刪除（已實作）**：
     * 刪除 `use-tax-rule-configs.ts`（包含 `taxRuleConfigSchema` 與 `taxBracketSchema`）。
  4. **介面查核與全專案依賴審查（已實作）**：
     * 全域 Grep 確認 `hub-ui` 無任何殘留 `tax-rule`、`TaxBracket`、`TaxRule` 參照或斷裂引入。
     * 「General Config」頁面保留之 System Config、Pension Rules、Transaction Sources 設定面板與 Hooks 均具備完整獨立性，運作 100% 正常。
* **交付成果**：前後端架構完全對齊，徹底消除無效 UI 設定與除役死碼，完成 Issue 142 終極治理。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                     10016  13.86G   7.36    20:56:53  
diff --git a/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx b/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx
deleted file mode 100644
index 87474f2..0000000
--- a/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx
+++ /dev/null
@@ -1,149 +0,0 @@
-import React from 'react';
-import { Button } from '@/components/ui/button';
-import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
-import { Plus, Trash2 } from 'lucide-react';
-import { Checkbox } from '@/components/ui/checkbox';
-import { ThousandSeparatorInput } from '@/components/ui/thousand-separator-input';
-import { type TaxBracketFormValues } from '../hooks/use-tax-rule-configs';
-import { Input } from '@/components/ui/input';
-
-interface TaxBracketEditorProps {
-  bracketType: 'EMPLOYMENT_INCOME_DEDUCTION' | 'INCOME_TAX_RATE' | 'INHABITANT_TAX_RATE';
-  title: string;
-  brackets: TaxBracketFormValues[];
-  onChange: (brackets: TaxBracketFormValues[]) => void;
-  disabled?: boolean;
-}
-
-export function TaxBracketEditor({ bracketType, title, brackets, onChange, disabled }: TaxBracketEditorProps) {
-  // Filter brackets for this specific type
-  const localBrackets = brackets.filter(b => b.bracketType === bracketType);
-  const otherBrackets = brackets.filter(b => b.bracketType !== bracketType);
-
-  const addBracket = () => {
-    const newBracket: TaxBracketFormValues = {
-      bracketType,
-      maxSalary: null,
-      multiplier: 0,
-      offset: 0,
-    };
-    onChange([...brackets, newBracket]);
-  };
-
-  const removeBracket = (localIndex: number) => {
-    const newLocalBrackets = [...localBrackets];
-    newLocalBrackets.splice(localIndex, 1);
-    onChange([...otherBrackets, ...newLocalBrackets]);
-  };
-
-  const updateBracket = (localIndex: number, field: keyof TaxBracketFormValues, value: any) => {
-    const newLocalBrackets = [...localBrackets];
-    newLocalBrackets[localIndex] = { ...newLocalBrackets[localIndex], [field]: value };
-    onChange([...otherBrackets, ...newLocalBrackets]);
-  };
-
-  return (
-    <div className="space-y-4">
-      <div className="flex justify-between items-center">
-        <h4 className="text-sm font-semibold text-foreground">{title}</h4>
-        <Button 
-          type="button" 
-          variant="outline" 
-          size="sm" 
-          onClick={addBracket}
-          disabled={disabled}
-        >
-          <Plus className="h-4 w-4 mr-2" /> Add Bracket
-        </Button>
-      </div>
-      <div className="border rounded-md">
-        <Table>
-          <TableHeader>
-            <TableRow>
-              <TableHead>Max Salary (JPY)</TableHead>
-              <TableHead className="w-[100px] text-center">Unlimited</TableHead>
-              <TableHead className="w-[150px]">Multiplier (%)</TableHead>
-              <TableHead className="w-[200px]">Offset (JPY)</TableHead>
-              <TableHead className="w-[80px]"></TableHead>
-            </TableRow>
-          </TableHeader>
-          <TableBody>
-            {localBrackets.length === 0 ? (
-              <TableRow>
-                <TableCell colSpan={5} className="text-center text-muted-foreground py-4">
-                  No brackets defined.
-                </TableCell>
-              </TableRow>
-            ) : (
-              localBrackets.map((bracket, index) => {
-                const isUnlimited = bracket.maxSalary === null || bracket.maxSalary === undefined;
-                
-                return (
-                  <TableRow key={index}>
-                    <TableCell>
-                      <ThousandSeparatorInput 
-                        value={isUnlimited ? '' : bracket.maxSalary}
-                        onChange={(val) => {
-                          if (!isUnlimited) {
-                            updateBracket(index, 'maxSalary', val ?? 0);
-                          }
-                        }}
-                        disabled={disabled || isUnlimited}
-                        placeholder={isUnlimited ? 'Unlimited' : 'e.g. 1950000'}
-                      />
-                    </TableCell>
-                    <TableCell className="text-center">
-                      <Checkbox 
-                        checked={isUnlimited}
-                        onCheckedChange={(checked) => {
-                          if (checked) {
-                            updateBracket(index, 'maxSalary', null);
-                          } else {
-                            updateBracket(index, 'maxSalary', 0);
-                          }
-                        }}
-                        disabled={disabled}
-                      />
-                    </TableCell>
-                    <TableCell>
-                      <div className="flex items-center space-x-2">
-                        <Input
-                          type="number"
-                          step="0.01"
-                          value={bracket.multiplier}
-                          onChange={(e) => updateBracket(index, 'multiplier', Number(e.target.value))}
-                          disabled={disabled}
-                        />
-                        <span className="text-sm">%</span>
-                      </div>
-                    </TableCell>
-                    <TableCell>
-                      <ThousandSeparatorInput 
-                        value={bracket.offset}
-                        onChange={(val) => updateBracket(index, 'offset', val ?? 0)}
-                        disabled={disabled}
-                        placeholder="e.g. 0"
-                      />
-                    </TableCell>
-                    <TableCell>
-                      <Button 
-                        type="button" 
-                        variant="ghost" 
-                        size="icon"
-                        className="text-destructive hover:bg-destructive/10"
-                        onClick={() => removeBracket(index)}
-                        disabled={disabled}
-                      >
-                        <Trash2 className="h-4 w-4" />
-                      </Button>
-                    </TableCell>
-                  </TableRow>
-                );
-              })
-            )}
-          </TableBody>
-        </Table>
-      </div>
-    </div>
-  );
-}
diff --git a/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx b/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx
deleted file mode 100644
index efd91b9..0000000
--- a/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx
+++ /dev/null
@@ -1,212 +0,0 @@
-import React, { useEffect } from 'react';
-import { useForm, Controller, type Resolver } from 'react-hook-form';
-import { zodResolver } from '@hookform/resolvers/zod';
-import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
-import { Label } from '@/components/ui/label';
-import { Input } from '@/components/ui/input';
-import { Button } from '@/components/ui/button';
-import { Checkbox } from '@/components/ui/checkbox';
-import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
-import { Loader2, Save, Trash2, Plus } from 'lucide-react';
-import { 
-  taxRuleConfigSchema, 
-  type TaxRuleConfigFormValues,
-  type useTaxRuleConfigs 
-} from '../hooks/use-tax-rule-configs';
-import { ThousandSeparatorInput } from '@/components/ui/thousand-separator-input';
-import { TaxBracketEditor } from './tax-bracket-editor';
-import {
-  AlertDialog,
-  AlertDialogAction,
-  AlertDialogCancel,
-  AlertDialogContent,
-  AlertDialogDescription,
-  AlertDialogFooter,
-  AlertDialogHeader,
-  AlertDialogTitle,
-  AlertDialogTrigger,
-} from "@/components/ui/alert-dialog";
-import { toast } from 'sonner';
-
-interface Props {
-  configsHook: ReturnType<typeof useTaxRuleConfigs>;
-}
-
-export function TaxRuleConfigPanel({ configsHook }: Props) {
-  const {
-    taxProfiles,
-    taxSelectedProfile,
-    handleTaxProfileChange,
-    taxConfigDetails,
-    isTaxLoadingProfiles,
-    isTaxLoadingDetails,
-    isTaxSaving,
-    isTaxDeleting,
-    isTaxCreatingNewProfile,
-    handleTaxNewProfileClick,
-    handleTaxCancelNewProfile,
-    saveTaxRuleConfig,
-    deleteTaxRuleConfig
-  } = configsHook;
-
-  const { register, handleSubmit, control, formState: { errors }, reset } = useForm<TaxRuleConfigFormValues>({
-    resolver: zodResolver(taxRuleConfigSchema) as Resolver<TaxRuleConfigFormValues>,
-    defaultValues: {
-      brackets: []
-    },
-  });
-
-  useEffect(() => {
-    if (taxConfigDetails) {
-      reset(taxConfigDetails);
-    }
-  }, [taxConfigDetails, reset]);
-
-  const onInvalid = (errors: any) => {
-    console.error("Form validation errors:", errors);
-    toast.error("Failed to save. Please check the form for errors.");
-  };
-
-  return (
-    <Card className="w-full">
-      <CardHeader>
-        <div className="flex justify-between items-center flex-wrap gap-4">
-          <div>
-            <CardTitle>Tax Rule Config</CardTitle>
-            <CardDescription>Configure income tax, resident tax, and deduction rules.</CardDescription>
-          </div>
-          <div className="flex gap-4 items-center">
-            <Select 
-              value={taxSelectedProfile || ""} 
-              onValueChange={handleTaxProfileChange}
-              disabled={isTaxLoadingProfiles || isTaxCreatingNewProfile || taxProfiles.length === 0}
-            >
-              <SelectTrigger className="w-[180px]">
-                <SelectValue placeholder="Select Profile" />
-              </SelectTrigger>
-              <SelectContent>
-                {taxProfiles.map(p => (
-                  <SelectItem key={p} value={p}>{p}</SelectItem>
-                ))}
-              </SelectContent>
-            </Select>
-          </div>
-        </div>
-      </CardHeader>
-      
-      <CardContent className="space-y-6">
-        {(isTaxLoadingProfiles || isTaxLoadingDetails) ? (
-          <div className="flex justify-center p-8">
-            <Loader2 className="h-8 w-8 animate-spin text-primary" />
-          </div>
-        ) : !taxConfigDetails ? (
-          <div className="text-center p-8 text-muted-foreground">
-            No tax profile details available.
-          </div>
-        ) : (
-          <div className="grid gap-4 md:grid-cols-2">
-            <div className="space-y-2 md:col-span-2">
-              <Label htmlFor="tax-name">Configuration Name</Label>
-              <Input 
-                id="tax-name"
-                {...register('name')} 
-                readOnly={!isTaxCreatingNewProfile} 
-                className={!isTaxCreatingNewProfile ? 'bg-muted text-muted-foreground' : ''} 
-              />
-              {errors.name && <p className="text-xs text-destructive mt-1">{errors.name.message}</p>}
-            </div>
-
-            <div className="flex items-center space-x-2 md:col-span-2">
-              <Controller control={control} name="enabled" render={({ field }) => (
-                <Checkbox 
-                  id="tax-enabled" 
-                  checked={field.value ?? false} 
-                  onCheckedChange={field.onChange} 
-                />
-              )} />
-              <Label htmlFor="tax-enabled">Enabled</Label>
-            </div>
-
-
-            <div className="md:col-span-2 mt-6">
-              <h3 className="text-lg font-medium border-b pb-2 mb-4">Tax Brackets</h3>
-              <Controller
-                control={control}
-                name="brackets"
-                render={({ field }) => (
-                  <div className="space-y-8">
-                    <TaxBracketEditor
-                      title="Employment Income Deduction (薪資所得扣除)"
-                      bracketType="EMPLOYMENT_INCOME_DEDUCTION"
-                      brackets={field.value || []}
-                      onChange={field.onChange}
-                    />
-                    <TaxBracketEditor
-                      title="Income Tax Rate (所得稅率)"
-                      bracketType="INCOME_TAX_RATE"
-                      brackets={field.value || []}
-                      onChange={field.onChange}
-                    />
-                    <TaxBracketEditor
-                      title="Resident Tax Rate (住民稅率)"
-                      bracketType="INHABITANT_TAX_RATE"
-                      brackets={field.value || []}
-                      onChange={field.onChange}
-                    />
-                  </div>
-                )}
-              />
-            </div>
-          </div>
-        )}
-      </CardContent>
-      <CardFooter className="flex justify-between border-t pt-6">
-        <div className="flex gap-2">
-           <AlertDialog>
-             <AlertDialogTrigger asChild>
-               {/* 盲點 5: Disable delete button for 'default' config */}
-               <div title={taxSelectedProfile === 'default' ? "Cannot delete the default configuration" : ""}>
-                 <Button 
-                   variant="outline" 
-                   className="text-destructive hover:text-destructive hover:bg-destructive/10 border-destructive/20" 
-                   disabled={isTaxDeleting || !taxConfigDetails || isTaxCreatingNewProfile || taxSelectedProfile === 'default'}
-                 >
-                   {isTaxDeleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4 mr-2" />} Delete
-                 </Button>
-               </div>
-             </AlertDialogTrigger>
-             <AlertDialogContent>
-               <AlertDialogHeader>
-                 <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
-                 <AlertDialogDescription>
-                   This action cannot be undone. This will permanently delete the "{taxConfigDetails?.name}" tax rule configuration.
-                 </AlertDialogDescription>
-               </AlertDialogHeader>
-               <AlertDialogFooter>
-                 <AlertDialogCancel>Cancel</AlertDialogCancel>
-                 <AlertDialogAction
-                   onClick={deleteTaxRuleConfig}
-                   className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
-                 >Continue</AlertDialogAction>
-               </AlertDialogFooter>
-             </AlertDialogContent>
-           </AlertDialog>
-        </div>
-
-        <div className="flex gap-2">
-          {isTaxCreatingNewProfile ? (
-            <Button variant="secondary" onClick={handleTaxCancelNewProfile}>Cancel</Button>
-          ) : (
-            <Button variant="outline" onClick={handleTaxNewProfileClick}>
-              <Plus className="h-4 w-4 mr-2" /> New
-            </Button>
-          )}
-          <Button onClick={handleSubmit(saveTaxRuleConfig, onInvalid)} disabled={isTaxSaving || (!taxConfigDetails && !isTaxCreatingNewProfile)}>
-            {isTaxSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
-            Save Settings
-          </Button>
-        </div>
-      </CardFooter>
-    </Card>
-  );
-}
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
deleted file mode 100644
index 1f49e87..0000000
--- a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
+++ /dev/null
@@ -1,215 +0,0 @@
-import { useState, useEffect, useCallback } from "react";
-import { toast } from "sonner";
-import { z } from "zod";
-
-export const taxBracketSchema = z.object({
-  maxSalary: z.number().nullable().optional(),
-  multiplier: z.number().min(0).max(100), // In UI, it's 0-100%
-  offset: z.number().default(0),
-  bracketType: z.enum(['EMPLOYMENT_INCOME_DEDUCTION', 'INCOME_TAX_RATE', 'INHABITANT_TAX_RATE']),
-});
-
-export const taxRuleConfigSchema = z.object({
-  name: z.string().min(1, "Name is required"),
-  enabled: z.boolean().default(false),
-  brackets: z.array(taxBracketSchema).default([]),
-});
-
-export type TaxBracketFormValues = z.infer<typeof taxBracketSchema>;
-export type TaxRuleConfigFormValues = z.infer<typeof taxRuleConfigSchema>;
-
-export function useTaxRuleConfigs() {
-  const [taxProfiles, setTaxProfiles] = useState<string[]>([]);
-  const [taxSelectedProfile, setTaxSelectedProfile] = useState<string>("");
-  const [taxPreviousSelectedProfile, setTaxPreviousSelectedProfile] = useState<string>("");
-  const [taxConfigDetails, setTaxConfigDetails] = useState<TaxRuleConfigFormValues | null>(null);
-  const [enabledConfigName, setEnabledConfigName] = useState<string | null>(null);
-
-  const [isTaxLoadingProfiles, setIsTaxLoadingProfiles] = useState(false);
-  const [isTaxLoadingDetails, setIsTaxLoadingDetails] = useState(false);
-  const [isTaxSaving, setIsTaxSaving] = useState(false);
-  const [isTaxDeleting, setIsTaxDeleting] = useState(false);
-  const [isTaxCreatingNewProfile, setIsTaxCreatingNewProfile] = useState(false);
-
-  const fetchTaxRuleProfiles = useCallback(async () => {
-    setIsTaxLoadingProfiles(true);
-    try {
-      const res = await fetch("/api/finance/configs/tax-rule");
-      const data = await res.json();
-      setTaxProfiles(data.allConfigNames || []);
-      setEnabledConfigName(data.enabledConfigName || null);
-      
-      const toSelect = data.enabledConfigName || (data.allConfigNames && data.allConfigNames.length > 0 ? data.allConfigNames[0] : "");
-      
-      // Only set if we haven't manually selected one yet
-      setTaxSelectedProfile((prev) => prev || toSelect);
-      setTaxPreviousSelectedProfile((prev) => prev || toSelect);
-    } catch (err) {
-      console.error(err);
-      toast.error("Failed to fetch tax rule config profiles");
-    } finally {
-      setIsTaxLoadingProfiles(false);
-    }
-  }, []);
-
-  const fetchTaxRuleConfigDetails = useCallback(async (profile: string) => {
-    if (isTaxCreatingNewProfile || !profile) return;
-    setIsTaxLoadingDetails(true);
-    try {
-      const res = await fetch(`/api/finance/configs/tax-rule/${profile}`);
-      const json = await res.json();
-      if (json.status === "success" && json.data) {
-        // Data transformations: Multiplier decimal to percentage
-        const configData = json.data;
-        if (configData.brackets) {
-          configData.brackets = configData.brackets.map((b: any) => ({
-            ...b,
-            multiplier: b.multiplier != null ? Number((b.multiplier * 100).toFixed(4)) : 0
-          }));
-        }
-        setTaxConfigDetails(configData);
-      } else {
-        toast.error(`Failed to load details for tax rule config ${profile}`);
-      }
-    } catch (err) {
-      console.error(err);
-      toast.error(`Failed to load details for tax rule config ${profile}`);
-    } finally {
-      setIsTaxLoadingDetails(false);
-    }
-  }, [isTaxCreatingNewProfile]);
-
-  useEffect(() => {
-    fetchTaxRuleProfiles();
-  }, [fetchTaxRuleProfiles]);
-
-  useEffect(() => {
-    if (taxSelectedProfile) {
-      fetchTaxRuleConfigDetails(taxSelectedProfile);
-    } else if (!isTaxCreatingNewProfile) {
-      setTaxConfigDetails(null);
-    }
-  }, [taxSelectedProfile, isTaxCreatingNewProfile, fetchTaxRuleConfigDetails]);
-
-  const handleTaxProfileChange = (profile: string) => {
-    setTaxSelectedProfile(profile);
-    setIsTaxCreatingNewProfile(false);
-  };
-
-  const handleTaxNewProfileClick = () => {
-    setTaxPreviousSelectedProfile(taxSelectedProfile);
-    setIsTaxCreatingNewProfile(true);
-    setTaxSelectedProfile("");
-    setTaxConfigDetails({
-      name: "",
-      enabled: false,
-      brackets: []
-    });
-  };
-
-  const handleTaxCancelNewProfile = () => {
-    setIsTaxCreatingNewProfile(false);
-    setTaxSelectedProfile(taxPreviousSelectedProfile);
-  };
-
-  const saveTaxRuleConfig = async (formData: TaxRuleConfigFormValues) => {
-    setIsTaxSaving(true);
-    const configName = formData.name;
-    const wasCreating = isTaxCreatingNewProfile;
-    
-    // Data transformations: Multiplier percentage to decimal, maxSalary undefined to null
-    const payload = {
-      ...formData,
-      brackets: formData.brackets.map(b => ({
-        ...b,
-        maxSalary: b.maxSalary === undefined || b.maxSalary === null ? null : b.maxSalary,
-        multiplier: b.multiplier != null ? Number((b.multiplier / 100).toFixed(6)) : 0
-      }))
-    };
-    
-    try {
-      const url = `/api/finance/configs/tax-rule${wasCreating ? '' : `/${configName}`}`;
-      const method = wasCreating ? "POST" : "PATCH";
-      
-      const res = await fetch(url, {
-        method,
-        headers: { "Content-Type": "application/json" },
-        body: JSON.stringify(payload),
-      });
-      
-      let json = null;
-      if (res.status === 201) {
-          json = await res.json();
-      } else if (res.ok) {
-          json = await res.json();
-      }
-      
-      if (json && json.status === "success") {
-        if (wasCreating) {
-          setIsTaxCreatingNewProfile(false);
-        }
-        toast.success(`Tax rule config "${configName}" saved successfully`);
-        
-        // Cache Invalidation / Refetch
-        // We re-fetch profiles to get the updated enabled status globally,
-        // and re-fetch the details to get the exact data saved.
-        await fetchTaxRuleProfiles();
-        setTaxSelectedProfile(configName);
-        setTaxPreviousSelectedProfile(configName);
-        await fetchTaxRuleConfigDetails(configName);
-      } else {
-        toast.error(`Failed to save tax rule config "${configName}"`);
-      }
-    } catch (err) {
-      console.error(err);
-      toast.error("An error occurred while saving tax rule config");
-    } finally {
-      setIsTaxSaving(false);
-    }
-  };
-
-  const deleteTaxRuleConfig = async () => {
-    if (!taxConfigDetails || taxConfigDetails.name === "default") {
-      toast.error("The 'default' tax rule configuration cannot be deleted.");
-      return;
-    }
-    setIsTaxDeleting(true);
-    const configName = taxConfigDetails.name;
-    try {
-      const res = await fetch(`/api/finance/configs/tax-rule/${configName}`, {
-        method: "DELETE"
-      });
-      
-      if (res.ok || res.status === 204) {
-        toast.success(`Tax rule config "${configName}" deleted successfully!`);
-        await fetchTaxRuleProfiles();
-        const newProfiles = taxProfiles.filter(p => p !== configName);
-        setTaxSelectedProfile(newProfiles.length > 0 ? newProfiles[0] : "");
-      } else {
-        toast.error(`Failed to delete "${configName}"`);
-      }
-    } catch (err) {
-      console.error(err);
-      toast.error(`Failed to delete "${configName}"`);
-    } finally {
-      setIsTaxDeleting(false);
-    }
-  };
-
-  return {
-    taxProfiles,
-    taxSelectedProfile,
-    enabledConfigName,
-    handleTaxProfileChange,
-    taxConfigDetails,
-    isTaxLoadingProfiles,
-    isTaxLoadingDetails,
-    isTaxSaving,
-    isTaxDeleting,
-    isTaxCreatingNewProfile,
-    handleTaxNewProfileClick,
-    handleTaxCancelNewProfile,
-    saveTaxRuleConfig,
-    deleteTaxRuleConfig
-  };
-}
diff --git a/hub-ui/src/app/finance/configs/general/page.tsx b/hub-ui/src/app/finance/configs/general/page.tsx
index fc3207c..a777f55 100644
--- a/hub-ui/src/app/finance/configs/general/page.tsx
+++ b/hub-ui/src/app/finance/configs/general/page.tsx
@@ -1,25 +1,21 @@
 'use client';
 
 import { useFinanceConfigs } from './hooks/use-finance-configs';
-import { useTaxRuleConfigs } from './hooks/use-tax-rule-configs';
 import { usePensionRuleConfigs } from './hooks/use-pension-rule-configs';
 import { TransactionSourceConfigPanel } from './components/transaction-source-config-panel';
 import { SystemConfigPanel } from './components/system-config-panel';
-import { TaxRuleConfigPanel } from './components/tax-rule-config-panel';
 import { PensionRuleConfigPanel } from './components/pension-rule-config-panel';
 import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
 
 export default function ConfigsPage() {
   const financeConfigs = useFinanceConfigs();
-  const taxRuleConfigs = useTaxRuleConfigs();
   const pensionRuleConfigs = usePensionRuleConfigs();
 
   return (
     <div className="font-sans flex flex-col gap-6 w-full py-4 md:py-8">
       <Tabs defaultValue="system" className="w-full">
-        <TabsList className="grid w-full grid-cols-4 max-w-[800px] mb-8">
+        <TabsList className="grid w-full grid-cols-3 max-w-[600px] mb-8">
           <TabsTrigger value="system">System Config</TabsTrigger>
-          <TabsTrigger value="tax-rules">Tax Rules</TabsTrigger>
           <TabsTrigger value="pension-rules">Pension Rules</TabsTrigger>
           <TabsTrigger value="transaction-sources">Transaction Sources</TabsTrigger>
         </TabsList>
@@ -30,12 +26,6 @@ export default function ConfigsPage() {
           </div>
         </TabsContent>
         
-        <TabsContent value="tax-rules" className="mt-0">
-          <div className="w-full">
-            <TaxRuleConfigPanel configsHook={taxRuleConfigs} />
-          </div>
-        </TabsContent>
-        
         <TabsContent value="pension-rules" className="mt-0">
           <div className="w-full">
             <PensionRuleConfigPanel configsHook={pensionRuleConfigs} />
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                10017  13.84G   6.75    20:57:45  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                      10018  13.86G   6.67    20:57:51  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java
deleted file mode 100644
index 81ce67b7..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java
+++ /dev/null
@@ -1,67 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.config.tax;
-
-import org.springframework.stereotype.Component;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracket;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxRuleConfig;
-
-/**
- * A factory component responsible for creating a default
- * {@link TaxRuleConfig} instance from externalized properties.
- */
-@Component
-public class TaxRuleConfigFactory {
-
-    /**
-     * Creates a new {@link TaxRuleConfig} instance from the given properties.
-     *
-     * @param properties The {@link TaxRuleDefaultProperties} containing default
-     *                   values.
-     * @return A new, fully populated {@link TaxRuleConfig} object.
-     */
-    public TaxRuleConfig create(TaxRuleDefaultProperties properties) {
-        TaxRuleConfig config = new TaxRuleConfig(
-                properties.getName(),
-                properties.getEnabled());
-
-        if (properties.getBrackets() != null) {
-            properties.getBrackets().forEach((type, list) -> {
-                if (list != null) {
-                    list.forEach(bp -> {
-                        config.addTaxBracket(new TaxBracket(
-                                bp.getMaxSalary(),
-                                bp.getMultiplier(),
-                                bp.getOffset(),
-                                type,
-                                config));
-                    });
-                }
-            });
-        }
-
-        return config;
-    }
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java
deleted file mode 100644
index c985308e..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java
+++ /dev/null
@@ -1,30 +0,0 @@
-package ch.lin.finance.hub.backend.api.app.config.tax;
-
-import java.math.BigDecimal;
-import java.util.HashMap;
-import java.util.List;
-import java.util.Map;
-
-import org.springframework.boot.context.properties.ConfigurationProperties;
-import org.springframework.stereotype.Component;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-import lombok.Data;
-
-@Component
-@ConfigurationProperties(prefix = "finance-hub.tax-rule")
-@Data
-public class TaxRuleDefaultProperties {
-
-    private String name = "default";
-    private Boolean enabled = true;
-
-    private Map<TaxBracketType, List<TaxBracketProperty>> brackets = new HashMap<>();
-
-    @Data
-    public static class TaxBracketProperty {
-        private Long maxSalary;
-        private BigDecimal multiplier;
-        private Long offset;
-    }
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java
deleted file mode 100644
index 4841cd88..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java
+++ /dev/null
@@ -1,57 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.repository.config;
-
-import java.util.Optional;
-
-import org.springframework.data.jpa.repository.JpaRepository;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxRuleConfig;
-
-/**
- * Repository interface for managing {@link TaxRuleConfig} entities.
- */
-public interface TaxRuleConfigRepository extends JpaRepository<TaxRuleConfig, Long> {
-
-    /**
-     * Finds the first enabled tax rule configuration.
-     *
-     * @return an {@link Optional} containing the enabled configuration.
-     */
-    Optional<TaxRuleConfig> findFirstByEnabledTrue();
-
-    /**
-     * Finds all enabled configurations (useful for setting them to false).
-     */
-    java.util.List<TaxRuleConfig> findAllByEnabledTrue();
-
-    /**
-     * Finds a tax rule configuration by its name.
-     *
-     * @param name the name of the configuration to find
-     * @return an {@link Optional} containing the configuration, or empty if not
-     *         found.
-     */
-    Optional<TaxRuleConfig> findByName(String name);
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigService.java
deleted file mode 100644
index bcf780a7..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigService.java
+++ /dev/null
@@ -1,60 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax;
-
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.CreateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.UpdateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
-
-/**
- * Service interface for managing Tax Rule configurations.
- */
-public interface TaxRuleConfigService {
-
-    /**
-     * Retrieves all configuration names and the name of the currently enabled one.
-     */
-    AllTaxRuleConfigsData getAllConfigs();
-
-    /**
-     * Retrieves a configuration by its name.
-     */
-    TaxRuleConfigResult getConfig(String name);
-
-    /**
-     * Creates a new tax rule configuration.
-     */
-    TaxRuleConfigResult createConfig(CreateTaxRuleConfigCommand command);
-
-    /**
-     * Updates an existing tax rule configuration.
-     */
-    TaxRuleConfigResult updateConfig(String name, UpdateTaxRuleConfigCommand command);
-
-    /**
-     * Deletes a tax rule configuration by its name.
-     */
-    void deleteConfig(String name);
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java
deleted file mode 100644
index 4b38a156..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java
+++ /dev/null
@@ -1,199 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax;
-
-import java.util.List;
-import java.util.Objects;
-import java.util.stream.Collectors;
-
-import org.slf4j.Logger;
-import org.slf4j.LoggerFactory;
-import org.springframework.boot.context.event.ApplicationReadyEvent;
-import org.springframework.context.event.EventListener;
-import org.springframework.stereotype.Service;
-import org.springframework.transaction.annotation.Transactional;
-
-import ch.lin.finance.hub.backend.api.app.config.tax.TaxRuleConfigFactory;
-import ch.lin.finance.hub.backend.api.app.config.tax.TaxRuleDefaultProperties;
-import ch.lin.finance.hub.backend.api.app.repository.config.TaxRuleConfigRepository;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.CreateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.UpdateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracket;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxRuleConfig;
-import ch.lin.platform.exception.ConfigNotFoundException;
-import ch.lin.platform.exception.InvalidRequestException;
-
-@Service
-public class TaxRuleConfigServiceImpl implements TaxRuleConfigService {
-
-    private static final Logger logger = LoggerFactory.getLogger(TaxRuleConfigServiceImpl.class);
-
-    private final TaxRuleConfigRepository repository;
-    private final TaxRuleDefaultProperties defaultProperties;
-    private final TaxRuleConfigFactory factory;
-
-    public TaxRuleConfigServiceImpl(TaxRuleConfigRepository repository,
-            TaxRuleDefaultProperties defaultProperties,
-            TaxRuleConfigFactory factory) {
-        this.repository = repository;
-        this.defaultProperties = defaultProperties;
-        this.factory = factory;
-    }
-
-    /**
-     * Initializes the default configuration on application startup.
-     */
-    @EventListener(ApplicationReadyEvent.class)
-    @Transactional
-    public void initializeDefaultConfigOnStartup() {
-        if (repository.count() == 0) {
-            TaxRuleConfig defaultConfig = factory.create(defaultProperties);
-            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
-                defaultConfig.setEnabled(true);
-            }
-            repository.save(defaultConfig);
-            logger.info("Enabled '{}' tax rule configuration on startup.", defaultConfig.getName());
-        }
-    }
-
-    @Override
-    @Transactional(readOnly = true)
-    public AllTaxRuleConfigsData getAllConfigs() {
-        if (repository.count() == 0) {
-            TaxRuleConfig defaultConfig = factory.create(defaultProperties);
-            defaultConfig.setEnabled(true);
-            repository.save(defaultConfig);
-        }
-
-        List<String> allNames = repository.findAll().stream()
-                .map(c -> c.getName())
-                .collect(Collectors.toList());
-
-        String enabledConfigName = repository.findFirstByEnabledTrue()
-                .map(c -> c.getName())
-                .orElse(defaultProperties.getName());
-
-        return new AllTaxRuleConfigsData(enabledConfigName, allNames);
-    }
-
-    @Override
-    @Transactional(readOnly = true)
-    public TaxRuleConfigResult getConfig(String name) {
-        TaxRuleConfig config = repository.findByName(name)
-                .orElseThrow(() -> new ConfigNotFoundException("TaxRuleConfig '" + name + "' not found."));
-        return TaxRuleConfigResult.fromEntity(config);
-    }
-
-    @Override
-    @Transactional
-    public TaxRuleConfigResult createConfig(CreateTaxRuleConfigCommand command) {
-        String configName = command.getName();
-        String defaultName = defaultProperties.getName();
-
-        if (defaultName.equalsIgnoreCase(configName)) {
-            throw new InvalidRequestException(
-                    "The '" + defaultName + "' configuration is system-reserved and cannot be created manually.");
-        }
-        repository.findByName(configName).ifPresent(c -> {
-            throw new InvalidRequestException("Configuration with name '" + configName + "' already exists.");
-        });
-
-        if (Boolean.TRUE.equals(command.getEnabled())) {
-            disableAllConfigs();
-        }
-
-        TaxRuleConfig newConfig = new TaxRuleConfig(
-                configName,
-                command.getEnabled());
-
-        if (command.getBrackets() != null) {
-            command.getBrackets().forEach(b -> {
-                newConfig.addTaxBracket(new TaxBracket(
-                        b.getMaxSalary(),
-                        b.getMultiplier(),
-                        b.getOffset(),
-                        b.getBracketType(),
-                        newConfig));
-            });
-        }
-
-        repository.save(newConfig);
-        logger.info("Created TaxRuleConfig: {}", configName);
-        return TaxRuleConfigResult.fromEntity(newConfig);
-    }
-
-    @Override
-    @Transactional
-    public TaxRuleConfigResult updateConfig(String name, UpdateTaxRuleConfigCommand command) {
-        TaxRuleConfig config = repository.findByName(name)
-                .orElseThrow(() -> new ConfigNotFoundException("TaxRuleConfig '" + name + "' not found."));
-
-        if (Boolean.TRUE.equals(command.getEnabled()) && !Boolean.TRUE.equals(config.getEnabled())) {
-            disableAllConfigs();
-        }
-
-        if (command.getEnabled() != null) {
-            config.setEnabled(command.getEnabled());
-        }
-
-        if (command.getBrackets() != null) {
-            config.clearTaxBrackets();
-            command.getBrackets().forEach(b -> {
-                config.addTaxBracket(new TaxBracket(
-                        b.getMaxSalary(),
-                        b.getMultiplier(),
-                        b.getOffset(),
-                        b.getBracketType(),
-                        config));
-            });
-        }
-
-        repository.save(Objects.requireNonNull(config));
-        logger.info("Updated TaxRuleConfig: {}", name);
-        return TaxRuleConfigResult.fromEntity(config);
-    }
-
-    @Override
-    @Transactional
-    public void deleteConfig(String name) {
-        if (defaultProperties.getName().equalsIgnoreCase(name)) {
-            throw new InvalidRequestException(
-                    "The '" + defaultProperties.getName()
-                            + "' configuration is system-reserved and cannot be deleted.");
-        }
-        TaxRuleConfig config = repository.findByName(name)
-                .orElseThrow(() -> new ConfigNotFoundException("TaxRuleConfig '" + name + "' not found."));
-        repository.delete(Objects.requireNonNull(config));
-        logger.info("Deleted TaxRuleConfig: {}", name);
-    }
-
-    private void disableAllConfigs() {
-        List<TaxRuleConfig> enabledConfigs = repository.findAllByEnabledTrue();
-        enabledConfigs.forEach(c -> c.setEnabled(false));
-        repository.saveAll(enabledConfigs);
-    }
-
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java
deleted file mode 100644
index 89bd8866..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java
+++ /dev/null
@@ -1,40 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.command;
-
-import java.util.List;
-
-import lombok.Builder;
-import lombok.Value;
-
-/**
- * Command DTO for creating a new TaxRuleConfig.
- */
-@Value
-@Builder
-public class CreateTaxRuleConfigCommand {
-    String name;
-    Boolean enabled;
-    List<TaxBracketCommand> brackets;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/TaxBracketCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/TaxBracketCommand.java
deleted file mode 100644
index 9eb01088..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/TaxBracketCommand.java
+++ /dev/null
@@ -1,42 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.command;
-
-import java.math.BigDecimal;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-import lombok.Builder;
-import lombok.Value;
-
-/**
- * Command DTO for a Tax Bracket.
- */
-@Value
-@Builder
-public class TaxBracketCommand {
-    TaxBracketType bracketType;
-    Long maxSalary;
-    BigDecimal multiplier;
-    Long offset;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java
deleted file mode 100644
index 7600661b..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java
+++ /dev/null
@@ -1,39 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.command;
-
-import java.util.List;
-
-import lombok.Builder;
-import lombok.Value;
-
-/**
- * Command DTO for updating an existing TaxRuleConfig.
- */
-@Value
-@Builder
-public class UpdateTaxRuleConfigCommand {
-    Boolean enabled;
-    List<TaxBracketCommand> brackets;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/AllTaxRuleConfigsData.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/AllTaxRuleConfigsData.java
deleted file mode 100644
index e30f3f86..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/AllTaxRuleConfigsData.java
+++ /dev/null
@@ -1,37 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.result;
-
-import java.util.List;
-
-import lombok.Value;
-
-/**
- * Result DTO containing metadata about all TaxRuleConfigs.
- */
-@Value
-public class AllTaxRuleConfigsData {
-    String enabledConfigName;
-    List<String> allConfigNames;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxBracketResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxBracketResult.java
deleted file mode 100644
index 6319915c..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxBracketResult.java
+++ /dev/null
@@ -1,42 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.result;
-
-import java.math.BigDecimal;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-import lombok.Builder;
-import lombok.Value;
-
-/**
- * Result DTO for a Tax Bracket.
- */
-@Value
-@Builder
-public class TaxBracketResult {
-    TaxBracketType bracketType;
-    Long maxSalary;
-    BigDecimal multiplier;
-    Long offset;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java
deleted file mode 100644
index a059a894..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java
+++ /dev/null
@@ -1,63 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax.result;
-
-import java.util.List;
-import java.util.stream.Collectors;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxRuleConfig;
-import lombok.Builder;
-import lombok.Value;
-
-/**
- * Result DTO for a TaxRuleConfig.
- */
-@Value
-@Builder
-public class TaxRuleConfigResult {
-    String name;
-    Boolean enabled;
-    List<TaxBracketResult> brackets;
-
-    public static TaxRuleConfigResult fromEntity(TaxRuleConfig config) {
-        if (config == null) {
-            return null;
-        }
-        List<TaxBracketResult> bracketResults = config.getTaxBrackets() == null ? List.of()
-                : config.getTaxBrackets().stream()
-                        .map(b -> TaxBracketResult.builder()
-                                .bracketType(b.getBracketType())
-                                .maxSalary(b.getMaxSalary())
-                                .multiplier(b.getMultiplier())
-                                .offset(b.getOffset())
-                                .build())
-                        .collect(Collectors.toList());
-
-        return TaxRuleConfigResult.builder()
-                .name(config.getName())
-                .enabled(config.getEnabled())
-                .brackets(bracketResults)
-                .build();
-    }
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
index e450b81b..09cfb809 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java
@@ -46,8 +46,6 @@ import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConf
 import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
 import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.TaxRuleConfigService;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionService;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
@@ -59,15 +57,12 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.Reso
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxConfigDto;
 import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
 import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
@@ -81,7 +76,6 @@ import lombok.extern.slf4j.Slf4j;
 public class SimulationContextFactory {
 
     private final FinanceHubConfigService configService;
-    private final TaxRuleConfigService taxRuleConfigService;
     private final PensionRuleConfigService pensionRuleConfigService;
     private final UserProfileRepository userProfileRepository;
     private final ChildProfileRepository childProfileRepository;
@@ -111,34 +105,6 @@ public class SimulationContextFactory {
         FinanceHubConfig config = configService.getConfig(activeConfigName);
         context.setConfig(config);
 
-        String activeTaxConfigName = taxRuleConfigService.getAllConfigs().getEnabledConfigName();
-        TaxRuleConfigResult taxConfigResult = taxRuleConfigService.getConfig(activeTaxConfigName);
-
-        List<TaxBracketDto> employmentBrackets = taxConfigResult.getBrackets().stream()
-                .filter(b -> b.getBracketType() == TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION)
-                .map(b -> new TaxBracketDto(b.getMaxSalary(), b.getMultiplier(), b.getOffset()))
-                .sorted(java.util.Comparator.comparing(b -> b.getMaxSalary(),
-                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
-                .collect(Collectors.toList());
-        List<TaxBracketDto> incomeTaxBrackets = taxConfigResult.getBrackets().stream()
-                .filter(b -> b.getBracketType() == TaxBracketType.INCOME_TAX_RATE)
-                .map(b -> new TaxBracketDto(b.getMaxSalary(), b.getMultiplier(), b.getOffset()))
-                .sorted(java.util.Comparator.comparing(b -> b.getMaxSalary(),
-                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
-                .collect(Collectors.toList());
-        List<TaxBracketDto> inhabitantTaxBrackets = taxConfigResult.getBrackets().stream()
-                .filter(b -> b.getBracketType() == TaxBracketType.INHABITANT_TAX_RATE)
-                .map(b -> new TaxBracketDto(b.getMaxSalary(), b.getMultiplier(), b.getOffset()))
-                .sorted(java.util.Comparator.comparing(b -> b.getMaxSalary(),
-                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
-                .collect(Collectors.toList());
-
-        TaxConfigDto taxConfigDto = new TaxConfigDto(
-                employmentBrackets,
-                incomeTaxBrackets,
-                inhabitantTaxBrackets);
-        context.setTaxConfig(taxConfigDto);
-
         String activePensionConfigName = pensionRuleConfigService.getAllConfigs().getEnabledConfigName();
         PensionRuleConfigResult pensionConfigResult = pensionRuleConfigService.getConfig(activePensionConfigName);
         PensionConfigDto pensionConfigDto = new PensionConfigDto(
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
index 1475aa3c..4b91ae8f 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
@@ -30,7 +30,6 @@ import java.util.List;
 import java.util.Map;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxConfigDto;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
@@ -50,7 +49,6 @@ public class SimulationContext implements Cloneable {
     private List<ChildProfile> children = new ArrayList<>();
     private Map<Long, Map<Integer, Long>> childEducationCostCache = Collections.emptyMap();
     private FinanceHubConfig config;
-    private TaxConfigDto taxConfig;
     private PensionConfigDto pensionConfig;
     private List<InsurancePolicyDto> insurancePolicies = new ArrayList<>();
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java
deleted file mode 100644
index 25689ebd..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java
+++ /dev/null
@@ -1,38 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;
-
-import java.math.BigDecimal;
-
-import lombok.Value;
-
-/**
- * Immutable DTO representing a tax bracket in the simulation context.
- */
-@Value
-public class TaxBracketDto {
-    Long maxSalary;
-    BigDecimal multiplier;
-    Long offset;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java
deleted file mode 100644
index 6ca96bcd..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java
+++ /dev/null
@@ -1,38 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;
-
-import java.util.List;
-
-import lombok.Value;
-
-/**
- * Immutable DTO representing tax configurations in the simulation context.
- */
-@Value
-public class TaxConfigDto {
-    List<TaxBracketDto> employmentIncomeDeductionBrackets;
-    List<TaxBracketDto> incomeTaxRateBrackets;
-    List<TaxBracketDto> inhabitantTaxRateBrackets;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index c0416b08..4829d697 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -24,16 +24,17 @@
 package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;
 
 import java.time.LocalDate;
-import java.util.List;
 
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.stereotype.Service;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
@@ -46,16 +47,25 @@ public class ActiveTaxCalculationEngine {
     private final DependentDeductionEngine dependentDeductionEngine;
     private final BasicDeductionStrategyFactory basicDeductionStrategyFactory;
     private final RetirementTaxStrategyFactory retirementTaxStrategyFactory;
+    private final EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory;
+    private final IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory;
+    private final InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory;
 
     @Autowired
     public ActiveTaxCalculationEngine(SpouseDeductionEngine spouseDeductionEngine,
             DependentDeductionEngine dependentDeductionEngine,
             BasicDeductionStrategyFactory basicDeductionStrategyFactory,
-            RetirementTaxStrategyFactory retirementTaxStrategyFactory) {
+            RetirementTaxStrategyFactory retirementTaxStrategyFactory,
+            EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory,
+            IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory,
+            InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory) {
         this.spouseDeductionEngine = spouseDeductionEngine;
         this.dependentDeductionEngine = dependentDeductionEngine;
         this.basicDeductionStrategyFactory = basicDeductionStrategyFactory;
         this.retirementTaxStrategyFactory = retirementTaxStrategyFactory;
+        this.employmentIncomeDeductionStrategyFactory = employmentIncomeDeductionStrategyFactory;
+        this.incomeTaxRateStrategyFactory = incomeTaxRateStrategyFactory;
+        this.inhabitantTaxRateStrategyFactory = inhabitantTaxRateStrategyFactory;
     }
 
     public TaxCalculationResult calculate(SimulationContext context, long grossBaseSalary, long annualBonus,
@@ -95,8 +105,9 @@ public class ActiveTaxCalculationEngine {
 
         // 2. Employment Income Deduction (給与所得控除)
         long grossTotalSalary = grossBaseSalary + annualBonus;
-        long employmentIncomeDeduction = calculateEmploymentIncomeDeduction(grossTotalSalary,
-                context.getTaxConfig().getEmploymentIncomeDeductionBrackets());
+        long employmentIncomeDeduction = employmentIncomeDeductionStrategyFactory
+                .getStrategy(simulationYear)
+                .calculateDeduction(grossTotalSalary);
 
         // 3. Gross Employment Income (給与所得)
         long employmentIncome = grossTotalSalary - employmentIncomeDeduction;
@@ -198,8 +209,8 @@ public class ActiveTaxCalculationEngine {
         taxableIncomeForInhabitantTax = (taxableIncomeForInhabitantTax / 1000) * 1000;
 
         // 6. Calculate Taxes
-        long baseIncomeTax = calculateIncomeTax(taxableIncomeForIncomeTax,
-                context.getTaxConfig().getIncomeTaxRateBrackets());
+        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(simulationYear)
+                .calculateIncomeTax(taxableIncomeForIncomeTax);
 
         // --- Apply Mortgage Tax Deduction (directly against income tax) ---
         // Under Article 41, Paragraph 1 of the Act on Special Measures Concerning Taxation,
@@ -216,15 +227,9 @@ public class ActiveTaxCalculationEngine {
         // Reconstruction special income tax (2.1% of income tax)
         long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);
 
-        // Inhabitant tax (roughly flat 10%)
-        long inhabitantTax = 0;
-        for (TaxBracketDto bracket : context.getTaxConfig().getInhabitantTaxRateBrackets()) {
-            if (bracket.getMaxSalary() == null || taxableIncomeForInhabitantTax <= bracket.getMaxSalary()) {
-                inhabitantTax = (long) (taxableIncomeForInhabitantTax * bracket.getMultiplier().doubleValue())
-                        + bracket.getOffset();
-                break;
-            }
-        }
+        // Inhabitant tax (statutory rate strategy)
+        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
+                .calculateInhabitantTax(taxableIncomeForInhabitantTax);
 
         // If there's leftover mortgage deduction, it can offset inhabitant tax (up to a
         // cap, e.g. 97,500 or 136,500 depending on rules)
@@ -248,24 +253,6 @@ public class ActiveTaxCalculationEngine {
                 .build();
     }
 
-    private long calculateEmploymentIncomeDeduction(long grossSalary, List<TaxBracketDto> brackets) {
-        for (TaxBracketDto bracket : brackets) {
-            if (bracket.getMaxSalary() == null || grossSalary <= bracket.getMaxSalary()) {
-                return (long) (grossSalary * bracket.getMultiplier().doubleValue()) + bracket.getOffset();
-            }
-        }
-        throw new IllegalStateException("Tax bracket configuration is invalid or missing a catch-all bracket.");
-    }
-
-    private long calculateIncomeTax(long taxableIncome, List<TaxBracketDto> brackets) {
-        for (TaxBracketDto bracket : brackets) {
-            if (bracket.getMaxSalary() == null || taxableIncome <= bracket.getMaxSalary()) {
-                return (long) (taxableIncome * bracket.getMultiplier().doubleValue()) + bracket.getOffset();
-            }
-        }
-        throw new IllegalStateException("Tax bracket configuration is invalid or missing a catch-all bracket.");
-    }
-
     private long calculateLifeInsuranceDeductionIncomeTax(long premium) {
         if (premium <= 20_000) {
             return premium;
@@ -312,18 +299,12 @@ public class ActiveTaxCalculationEngine {
                     .build();
         }
 
-        long baseIncomeTax = calculateIncomeTax(taxableIncome,
-                context.getTaxConfig().getIncomeTaxRateBrackets());
+        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(context.getYear())
+                .calculateIncomeTax(taxableIncome);
         long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);
 
-        long inhabitantTax = 0;
-        for (TaxBracketDto bracket : context.getTaxConfig().getInhabitantTaxRateBrackets()) {
-            if (bracket.getMaxSalary() == null || taxableIncome <= bracket.getMaxSalary()) {
-                inhabitantTax = (long) (taxableIncome * bracket.getMultiplier().doubleValue())
-                        + bracket.getOffset();
-                break;
-            }
-        }
+        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
+                .calculateInhabitantTax(taxableIncome);
 
         return TaxCalculationResult.builder()
                 .incomeTax(incomeTax)
@@ -366,18 +347,12 @@ public class ActiveTaxCalculationEngine {
         long taxableIncomeForInhabitantTax = Math.max(0, miscellaneousIncome - totalDeductionsInhabitantTax);
         taxableIncomeForInhabitantTax = (taxableIncomeForInhabitantTax / 1000) * 1000;
 
-        long baseIncomeTax = calculateIncomeTax(taxableIncomeForIncomeTax,
-                context.getTaxConfig().getIncomeTaxRateBrackets());
+        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(context.getYear())
+                .calculateIncomeTax(taxableIncomeForIncomeTax);
         long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);
 
-        long inhabitantTax = 0;
-        for (TaxBracketDto bracket : context.getTaxConfig().getInhabitantTaxRateBrackets()) {
-            if (bracket.getMaxSalary() == null || taxableIncomeForInhabitantTax <= bracket.getMaxSalary()) {
-                inhabitantTax = (long) (taxableIncomeForInhabitantTax * bracket.getMultiplier().doubleValue())
-                        + bracket.getOffset();
-                break;
-            }
-        }
+        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
+                .calculateInhabitantTax(taxableIncomeForInhabitantTax);
 
         long netIncome = pensionIncome - socialInsurance - incomeTax - inhabitantTax;
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java
deleted file mode 100644
index 12e7eeec..00000000
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java
+++ /dev/null
@@ -1,43 +0,0 @@
-package ch.lin.finance.hub.backend.api.app.config.tax;
-
-import java.math.BigDecimal;
-
-import static org.assertj.core.api.Assertions.assertThat;
-import org.junit.jupiter.api.Test;
-import org.springframework.beans.factory.annotation.Autowired;
-import org.springframework.boot.context.properties.EnableConfigurationProperties;
-import org.springframework.boot.test.context.SpringBootTest;
-import org.springframework.test.context.TestPropertySource;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-
-@SpringBootTest(classes = { TaxRuleDefaultPropertiesTest.TestConfig.class })
-@TestPropertySource(properties = {
-        "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=1625000",
-        "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0",
-        "finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=550000"
-})
-public class TaxRuleDefaultPropertiesTest {
-
-    @EnableConfigurationProperties(TaxRuleDefaultProperties.class)
-    public static class TestConfig {
-    }
-
-    @Autowired
-    private TaxRuleDefaultProperties properties;
-
-    @Test
-    void shouldLoadDefaultProperties() {
-        assertThat(properties.getName()).isEqualTo("default");
-        assertThat(properties.getEnabled()).isTrue();
-
-        assertThat(properties.getBrackets()).containsKey(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION);
-        assertThat(properties.getBrackets().get(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION)).hasSize(1);
-
-        TaxRuleDefaultProperties.TaxBracketProperty bracket = properties.getBrackets()
-                .get(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION).get(0);
-        assertThat(bracket.getMaxSalary()).isEqualTo(1625000L);
-        assertThat(bracket.getMultiplier()).isEqualByComparingTo(BigDecimal.ZERO);
-        assertThat(bracket.getOffset()).isEqualTo(550000L);
-    }
-}
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java
deleted file mode 100644
index 55fc685d..00000000
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java
+++ /dev/null
@@ -1,141 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.config.tax;
-
-import java.util.List;
-import java.util.Optional;
-
-import static org.junit.jupiter.api.Assertions.assertEquals;
-import static org.junit.jupiter.api.Assertions.assertThrows;
-import org.junit.jupiter.api.BeforeEach;
-import org.junit.jupiter.api.Test;
-import org.junit.jupiter.api.extension.ExtendWith;
-import org.mockito.InjectMocks;
-import org.mockito.Mock;
-import static org.mockito.Mockito.verify;
-import static org.mockito.Mockito.when;
-import org.mockito.junit.jupiter.MockitoExtension;
-
-import ch.lin.finance.hub.backend.api.app.config.tax.TaxRuleConfigFactory;
-import ch.lin.finance.hub.backend.api.app.config.tax.TaxRuleDefaultProperties;
-import ch.lin.finance.hub.backend.api.app.repository.config.TaxRuleConfigRepository;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.CreateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxRuleConfig;
-import ch.lin.platform.exception.ConfigNotFoundException;
-import ch.lin.platform.exception.InvalidRequestException;
-
-@ExtendWith(MockitoExtension.class)
-@SuppressWarnings("null")
-class TaxRuleConfigServiceImplTest {
-
-    @Mock
-    private TaxRuleConfigRepository repository;
-    @Mock
-    private TaxRuleDefaultProperties defaultProperties;
-    @Mock
-    private TaxRuleConfigFactory factory;
-
-    @InjectMocks
-    private TaxRuleConfigServiceImpl service;
-
-    @BeforeEach
-    @SuppressWarnings("unused")
-    void setUp() {
-    }
-
-    @Test
-    void testGetAllConfigs_EmptyRepo() {
-        when(repository.count()).thenReturn(0L);
-        when(defaultProperties.getName()).thenReturn("default");
-
-        TaxRuleConfig config = new TaxRuleConfig("default", true);
-        when(factory.create(defaultProperties)).thenReturn(config);
-
-        when(repository.findAll()).thenReturn(List.of(config));
-        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));
-
-        AllTaxRuleConfigsData data = service.getAllConfigs();
-
-        assertEquals("default", data.getEnabledConfigName());
-        assertEquals(1, data.getAllConfigNames().size());
-        verify(repository).save(config);
-    }
-
-    @Test
-    void testGetAllConfigs() {
-        when(repository.count()).thenReturn(1L);
-        when(defaultProperties.getName()).thenReturn("default");
-
-        TaxRuleConfig config = new TaxRuleConfig("default", true);
-        when(repository.findAll()).thenReturn(List.of(config));
-        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));
-
-        AllTaxRuleConfigsData data = service.getAllConfigs();
-
-        assertEquals("default", data.getEnabledConfigName());
-        assertEquals(1, data.getAllConfigNames().size());
-    }
-
-    @Test
-    void testGetConfig_NotFound() {
-        when(repository.findByName("custom")).thenReturn(Optional.empty());
-
-        ConfigNotFoundException exception = assertThrows(ConfigNotFoundException.class,
-                () -> service.getConfig("custom"));
-        assertEquals("TaxRuleConfig 'custom' not found.", exception.getMessage());
-    }
-
-    @Test
-    void testCreateConfig_SystemReservedName() {
-        when(defaultProperties.getName()).thenReturn("default");
-
-        CreateTaxRuleConfigCommand cmd = CreateTaxRuleConfigCommand.builder()
-                .name("default")
-                .enabled(true)
-                .build();
-
-        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
-                () -> service.createConfig(cmd));
-        assertEquals(
-                "The 'default' configuration is system-reserved and cannot be created manually.",
-                exception.getMessage());
-    }
-
-    @Test
-    void testCreateConfig_AlreadyExists() {
-        when(defaultProperties.getName()).thenReturn("default");
-        when(repository.findByName("custom"))
-                .thenReturn(Optional.of(new TaxRuleConfig("custom", true)));
-
-        CreateTaxRuleConfigCommand cmd = CreateTaxRuleConfigCommand.builder()
-                .name("custom")
-                .enabled(true)
-                .build();
-
-        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
-                () -> service.createConfig(cmd));
-        assertEquals("Configuration with name 'custom' already exists.", exception.getMessage());
-    }
-}
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index e45dab8e..363a7712 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -75,8 +75,6 @@ import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensi
 import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
 import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionServiceImpl;
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Pre2022TaxStrategy;
 import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
@@ -143,9 +141,6 @@ class AssetSimulationEngineImplTest {
     @Mock
     private FinanceHubConfigService configService;
 
-    @Mock
-    private ch.lin.finance.hub.backend.api.app.service.config.tax.TaxRuleConfigService taxRuleConfigService;
-
     @Mock
     private SalaryRecordRepository salaryRecordRepository;
 
@@ -230,7 +225,7 @@ class AssetSimulationEngineImplTest {
                 dcRepository, dcUserSettingsRepository);
 
         SimulationContextFactory factory = new SimulationContextFactory(
-                configService, taxRuleConfigService, pensionRuleConfigService, userProfileRepository,
+                configService, pensionRuleConfigService, userProfileRepository,
                 org.mockito.Mockito.mock(ChildProfileRepository.class),
                 incomeExpenseCalculator,
                 new CurrencyConversionServiceImpl(),
@@ -256,17 +251,6 @@ class AssetSimulationEngineImplTest {
         configs.setEnabledConfigName("default");
         when(configService.getAllConfigs()).thenReturn(configs);
 
-        AllTaxRuleConfigsData taxConfigs = new AllTaxRuleConfigsData("default-tax",
-                Collections.singletonList("default-tax"));
-        lenient().when(taxRuleConfigService.getAllConfigs()).thenReturn(taxConfigs);
-
-        TaxRuleConfigResult taxResult = TaxRuleConfigResult.builder()
-                .name("default-tax")
-                .enabled(true)
-                .brackets(Collections.emptyList())
-                .build();
-        lenient().when(taxRuleConfigService.getConfig("default-tax")).thenReturn(taxResult);
-
         AllPensionRuleConfigsData pensionConfigs = new AllPensionRuleConfigsData("default-pension",
                 Collections.singletonList("default-pension"));
         lenient().when(pensionRuleConfigService.getAllConfigs()).thenReturn(pensionConfigs);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
index 570751bc..ab739b96 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java
@@ -54,10 +54,6 @@ import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensi
 import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
 import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.TaxRuleConfigService;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxBracketResult;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
 import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionService;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
@@ -69,7 +65,6 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.Reso
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
 import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 
 @SuppressWarnings("unused")
@@ -79,8 +74,6 @@ class SimulationContextFactoryTest {
     @Mock
     private FinanceHubConfigService configService;
     @Mock
-    private TaxRuleConfigService taxRuleConfigService;
-    @Mock
     private PensionRuleConfigService pensionRuleConfigService;
     @Mock
     private UserProfileRepository userProfileRepository;
@@ -126,31 +119,14 @@ class SimulationContextFactoryTest {
     @BeforeEach
     void setUp() {
         when(configService.getAllConfigs()).thenReturn(new AllConfigsData("default", List.of("default")));
-        when(taxRuleConfigService.getAllConfigs())
-                .thenReturn(new AllTaxRuleConfigsData("default-tax", List.of("default-tax")));
         when(pensionRuleConfigService.getAllConfigs())
                 .thenReturn(new AllPensionRuleConfigsData("default-pension", List.of("default-pension")));
         when(configService.getConfig("default")).thenReturn(new FinanceHubConfig("default"));
     }
 
     @Test
-    void shouldCreateContextWithTaxConfigDto() {
+    void shouldCreateContextWithPensionConfigDto() {
         // Arrange
-
-        TaxBracketResult b1 = TaxBracketResult.builder()
-                .bracketType(TaxBracketType.EMPLOYMENT_INCOME_DEDUCTION)
-                .maxSalary(1625000L).multiplier(new BigDecimal("1.0")).offset(-550000L).build();
-        TaxBracketResult b2 = TaxBracketResult.builder()
-                .bracketType(TaxBracketType.INCOME_TAX_RATE)
-                .maxSalary(1949000L).multiplier(new BigDecimal("0.05")).offset(0L).build();
-
-        TaxRuleConfigResult taxConfigResult = TaxRuleConfigResult.builder()
-                .name("default-tax")
-                .enabled(true)
-                .brackets(List.of(b1, b2))
-                .build();
-        when(taxRuleConfigService.getConfig("default-tax")).thenReturn(taxConfigResult);
-
         PensionRuleConfigResult pensionConfigResult = PensionRuleConfigResult.builder()
                 .name("default-pension")
                 .enabled(true)
@@ -168,15 +144,6 @@ class SimulationContextFactoryTest {
         SimulationContext context = factory.createContext();
 
         // Assert
-        assertThat(context.getTaxConfig()).isNotNull();
-
-        assertThat(context.getTaxConfig().getEmploymentIncomeDeductionBrackets()).hasSize(1);
-        assertThat(context.getTaxConfig().getEmploymentIncomeDeductionBrackets().get(0).getMaxSalary())
-                .isEqualTo(1625000L);
-
-        assertThat(context.getTaxConfig().getIncomeTaxRateBrackets()).hasSize(1);
-        assertThat(context.getTaxConfig().getInhabitantTaxRateBrackets()).isEmpty();
-
         assertThat(context.getPensionConfig()).isNotNull();
         assertThat(context.getPensionConfig().getMaxBasicPensionAmount()).isEqualTo(816000L);
         assertThat(context.getPensionConfig().getMaxBasicPensionMonths()).isEqualTo(480L);
@@ -198,12 +165,6 @@ class SimulationContextFactoryTest {
         when(childProfileRepository.findAll()).thenReturn(List.of(child));
         when(insurancePolicyRepository.findAll()).thenReturn(List.of());
 
-        TaxRuleConfigResult taxConfigResult = TaxRuleConfigResult.builder()
-                .name("default-tax")
-                .brackets(List.of())
-                .build();
-        when(taxRuleConfigService.getConfig("default-tax")).thenReturn(taxConfigResult);
-
         PensionRuleConfigResult pensionConfigResult = PensionRuleConfigResult.builder()
                 .name("default-pension")
                 .build();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
index 077850cb..58da5e04 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java
@@ -23,19 +23,17 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;
 
-import java.math.BigDecimal;
-import java.util.Collections;
-
 import static org.assertj.core.api.Assertions.assertThat;
 import org.junit.jupiter.api.Test;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxBracketDto;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.TaxConfigDto;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 
@@ -45,8 +43,13 @@ class ActiveTaxCalculationEngineTest {
     private final DependentDeductionEngine dependentEngine = new DependentDeductionEngine();
     private final BasicDeductionStrategyFactory basicDeductionStrategyFactory = new BasicDeductionStrategyFactory();
     private final RetirementTaxStrategyFactory retirementTaxStrategyFactory = new RetirementTaxStrategyFactory();
+    private final EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory = new EmploymentIncomeDeductionStrategyFactory();
+    private final IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory = new IncomeTaxRateStrategyFactory();
+    private final InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory = new InhabitantTaxRateStrategyFactory();
     private final ActiveTaxCalculationEngine engine = new ActiveTaxCalculationEngine(
-            spouseEngine, dependentEngine, basicDeductionStrategyFactory, retirementTaxStrategyFactory);
+            spouseEngine, dependentEngine, basicDeductionStrategyFactory, retirementTaxStrategyFactory,
+            employmentIncomeDeductionStrategyFactory, incomeTaxRateStrategyFactory,
+            inhabitantTaxRateStrategyFactory);
 
     @Test
     void calculate_baseCase_noDeductions() {
@@ -54,13 +57,8 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2024);
@@ -102,13 +100,8 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2026);
@@ -141,13 +134,8 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 8_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), -427_500L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2028);
@@ -178,12 +166,7 @@ class ActiveTaxCalculationEngineTest {
         long pensionIncome = 3_000_000L;
         int age = 65;
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_900_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.05"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
-        context.setTaxConfig(taxConfig);
         context.setYear(2026); // Reiwa 8: miscellaneousIncome <= 4.89M gets 1,040,000 JPY deduction
 
         // When
@@ -218,13 +201,8 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 26_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2026);
@@ -263,13 +241,8 @@ class ActiveTaxCalculationEngineTest {
         long grossSalary = 27_000_000L;
         UserProfile profile = new UserProfile("test", null, null, null);
 
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2026);
@@ -307,13 +280,8 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 20_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2026);
@@ -348,13 +316,8 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 20_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2026);
@@ -388,13 +351,8 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 30_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2021);
@@ -428,13 +386,8 @@ class ActiveTaxCalculationEngineTest {
         long statutoryIncomeCap = 30_000_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
         context.setProfile(profile);
-        context.setTaxConfig(taxConfig);
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
         context.setPensionConfig(pensionConfig);
         context.setYear(2021);
@@ -467,16 +420,11 @@ class ActiveTaxCalculationEngineTest {
         long mortgageDeduction = 200_000L;
 
         UserProfile profile = new UserProfile("test", null, null, null);
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.0"), 1_950_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.4"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
 
         // Pre-2022 context (statutory cap = 30,000,000 JPY)
         SimulationContext context2021 = new SimulationContext();
         context2021.setProfile(profile);
-        context2021.setTaxConfig(taxConfig);
         context2021.setPensionConfig(pensionConfig);
         context2021.setYear(2021);
         context2021.setHealthInsuranceRate(0.05);
@@ -496,7 +444,6 @@ class ActiveTaxCalculationEngineTest {
         // 2026 context (statutory cap = 20,000,000 JPY)
         SimulationContext context2026 = new SimulationContext();
         context2026.setProfile(profile);
-        context2026.setTaxConfig(taxConfig);
         context2026.setPensionConfig(pensionConfig);
         context2026.setYear(2026);
         context2026.setHealthInsuranceRate(0.05);
@@ -517,12 +464,8 @@ class ActiveTaxCalculationEngineTest {
 
     @Test
     void calculateRetirementTax_incomeBelowDeduction_returnsZeroTax() {
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.emptyList(),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.2"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.1"), 0L)));
         SimulationContext context = new SimulationContext();
-        context.setTaxConfig(taxConfig);
+        context.setYear(2024);
 
         TaxCalculationResult result = engine.calculateRetirementTax(context, 800_000L, 1);
 
@@ -539,12 +482,8 @@ class ActiveTaxCalculationEngineTest {
         // Short-term strategy applies half taxation: taxableIncome = 1,000,000 JPY
         // Income tax rate: 5% (offset 0) -> base = 50,000 JPY -> with 2.1% surtax = 51,050 JPY
         // Inhabitant tax rate: 10% -> 100,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.emptyList(),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.05"), 0L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
         SimulationContext context = new SimulationContext();
-        context.setTaxConfig(taxConfig);
+        context.setYear(2024);
 
         TaxCalculationResult result = engine.calculateRetirementTax(context, 3_200_000L, 3);
 
@@ -563,12 +502,8 @@ class ActiveTaxCalculationEngineTest {
         // baseIncomeTax = 7,300,000 * 0.23 - 636,000 = 1,043,000 JPY
         // incomeTax = 1,043,000 + (long)(1,043,000 * 0.021) = 1,064,903 JPY
         // Inhabitant tax: 7,300,000 * 0.10 = 730,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.emptyList(),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.23"), -636_000L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
         SimulationContext context = new SimulationContext();
-        context.setTaxConfig(taxConfig);
+        context.setYear(2024);
 
         TaxCalculationResult result = engine.calculateRetirementTax(context, 10_000_000L, 3);
 
@@ -586,12 +521,8 @@ class ActiveTaxCalculationEngineTest {
         // baseIncomeTax = 4,400,000 * 0.20 - 427,500 = 452,500 JPY
         // incomeTax = 452,500 + (long)(452,500 * 0.021) = 462,002 JPY
         // Inhabitant tax: 4,400,000 * 0.10 = 440,000 JPY
-        TaxConfigDto taxConfig = new TaxConfigDto(
-                Collections.emptyList(),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.20"), -427_500L)),
-                Collections.singletonList(new TaxBracketDto(Long.MAX_VALUE, new BigDecimal("0.10"), 0L)));
         SimulationContext context = new SimulationContext();
-        context.setTaxConfig(taxConfig);
+        context.setYear(2024);
 
         TaxCalculationResult result = engine.calculateRetirementTax(context, 12_800_000L, 10);
 
@@ -599,5 +530,172 @@ class ActiveTaxCalculationEngineTest {
         assertThat(result.inhabitantTax()).isEqualTo(440_000L);
         assertThat(result.netIncome()).isEqualTo(12_800_000L - 462_002L - 440_000L);
     }
+
+    @Test
+    void calculate_crossEraEmploymentIncomeDeduction_provesBugFixFor2028() {
+        // Gross salary: 1,800,000 JPY
+        // Statutory Minimum Deduction across 4 Eras:
+        // - 2024 (Legacy): 1.8M * 40% - 100k = 620,000 JPY -> Employment Income = 1,180,000 JPY
+        // - 2025 (Reiwa 7): 650,000 JPY (Minimum Guarantee) -> Employment Income = 1,150,000 JPY
+        // - 2026 (Reiwa 8): 740,000 JPY (Special Sunset) -> Employment Income = 1,060,000 JPY
+        // - 2028 (Reiwa 10): 690,000 JPY (Statutory Baseline) -> Employment Income = 1,110,000 JPY
+        // (Bug in Issue 142 caused 2028+ to wrongly remain at 740,000 JPY, underestimating income by 50,000 JPY)
+        // 1.8M JPY is an optimal boundary test value exceeding the 1.68M/1.78M exemption thresholds,
+        // ensuring non-zero income and inhabitant taxes across all 4 eras without loose assertions.
+        long grossSalary = 1_800_000L;
+        UserProfile profile = new UserProfile("test", null, null, null);
+
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+
+        // 1. Year 2024 (Legacy): 620,000 JPY deduction
+        SimulationContext context2024 = new SimulationContext();
+        context2024.setProfile(profile);
+        context2024.setPensionConfig(pensionConfig);
+        context2024.setYear(2024);
+        context2024.setHealthInsuranceRate(0.0);
+        context2024.setPensionRate(0.0);
+        context2024.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2024 = engine.calculate(
+                context2024, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+        // Employment income = 1,800,000 - 620,000 = 1,180,000
+        // Basic deduction 2024: 480,000 (income tax), 430,000 (inhabitant tax)
+        // Taxable income tax = 1,180,000 - 480,000 = 700,000 -> 700,000 * 5% = 35,000 -> recon: 35,735
+        // Taxable inhab tax = 1,180,000 - 430,000 = 750,000 -> 750,000 * 10% = 75,000
+        assertThat(res2024.incomeTax()).isEqualTo(35_735L);
+        assertThat(res2024.inhabitantTax()).isEqualTo(75_000L);
+
+        // 2. Year 2025 (Reiwa 7): Minimum Guarantee 650,000 JPY
+        SimulationContext context2025 = new SimulationContext();
+        context2025.setProfile(profile);
+        context2025.setPensionConfig(pensionConfig);
+        context2025.setYear(2025);
+        context2025.setHealthInsuranceRate(0.0);
+        context2025.setPensionRate(0.0);
+        context2025.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2025 = engine.calculate(
+                context2025, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+        // Employment income = 1,800,000 - 650,000 = 1,150,000
+        // Basic deduction 2025 (Reiwa 7, income <= 1.32M): 950,000 (income tax)
+        // Taxable income tax = 1,150,000 - 950,000 = 200,000 -> 200,000 * 5% = 10,000 -> recon: 10,210
+        // Taxable inhab tax = 1,150,000 - 430,000 = 720,000 -> 720,000 * 10% = 72,000
+        assertThat(res2025.incomeTax()).isEqualTo(10_210L);
+        assertThat(res2025.inhabitantTax()).isEqualTo(72_000L);
+
+        // 3. Year 2026 (Reiwa 8): Special Sunset Minimum 740,000 JPY
+        SimulationContext context2026 = new SimulationContext();
+        context2026.setProfile(profile);
+        context2026.setPensionConfig(pensionConfig);
+        context2026.setYear(2026);
+        context2026.setHealthInsuranceRate(0.0);
+        context2026.setPensionRate(0.0);
+        context2026.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2026 = engine.calculate(
+                context2026, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+        // Employment income = 1,800,000 - 740,000 = 1,060,000
+        // Basic deduction 2026 (Reiwa 8, income <= 4.89M): 1,040,000 (income tax)
+        // Taxable income tax = 1,060,000 - 1,040,000 = 20,000 -> 20,000 * 5% = 1,000 -> recon: 1,021
+        // Taxable inhab tax = 1,060,000 - 430,000 = 630,000 -> 630,000 * 10% = 63,000
+        assertThat(res2026.incomeTax()).isEqualTo(1_021L);
+        assertThat(res2026.inhabitantTax()).isEqualTo(63_000L);
+
+        // 4. Year 2028 (Reiwa 10): Statutory Baseline Reversion 690,000 JPY
+        SimulationContext context2028 = new SimulationContext();
+        context2028.setProfile(profile);
+        context2028.setPensionConfig(pensionConfig);
+        context2028.setYear(2028);
+        context2028.setHealthInsuranceRate(0.0);
+        context2028.setPensionRate(0.0);
+        context2028.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2028 = engine.calculate(
+                context2028, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+        // Employment income = 1,800,000 - 690,000 = 1,110,000 (50k higher than 2026!)
+        // Basic deduction 2028 (Reiwa 10, income <= 1.32M): 990,000 (income tax)
+        // Taxable income tax = 1,110,000 - 990,000 = 120,000 -> 120,000 * 5% = 6,000 -> recon: 6,126 (5,105 higher than 2026!)
+        // Taxable inhab tax = 1,110,000 - 430,000 = 680,000 -> 680,000 * 10% = 68,000 (5,000 higher than 2026!)
+        assertThat(res2028.incomeTax()).isEqualTo(6_126L);
+        assertThat(res2028.inhabitantTax()).isEqualTo(68_000L);
+    }
+
+    @Test
+    void calculate_crossEraIncomeTaxRate_provesStrategyPattern() {
+        // High income earner with 60M gross salary to test top bracket differentiation
+        long grossSalary = 60_000_000L;
+        UserProfile profile = new UserProfile("test", null, null, null);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+
+        // Context 2014: Pre-2015 era with 40% cap
+        SimulationContext context2014 = new SimulationContext();
+        context2014.setProfile(profile);
+        context2014.setPensionConfig(pensionConfig);
+        context2014.setYear(2014);
+        context2014.setHealthInsuranceRate(0.0);
+        context2014.setPensionRate(0.0);
+        context2014.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2014 = engine.calculate(
+                context2014, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Context 2015: Standard 2015+ era with 45% top bracket
+        SimulationContext context2015 = new SimulationContext();
+        context2015.setProfile(profile);
+        context2015.setPensionConfig(pensionConfig);
+        context2015.setYear(2015);
+        context2015.setHealthInsuranceRate(0.0);
+        context2015.setPensionRate(0.0);
+        context2015.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2015 = engine.calculate(
+                context2015, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Income tax for 2015 should be strictly higher than 2014 due to the 45% statutory bracket
+        assertThat(res2015.incomeTax()).isGreaterThan(res2014.incomeTax());
+    }
+
+    @Test
+    void calculate_crossEraInhabitantTaxRate_provesStrategyPattern() {
+        // Low-to-medium income earner with 3,000,000 gross salary
+        long grossSalary = 3_000_000L;
+        UserProfile profile = new UserProfile("test", null, null, null);
+        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
+
+        // Context 2006: Pre-2007 Trinity reform era (tiered 5% ~ 13%)
+        SimulationContext context2006 = new SimulationContext();
+        context2006.setProfile(profile);
+        context2006.setPensionConfig(pensionConfig);
+        context2006.setYear(2006);
+        context2006.setHealthInsuranceRate(0.0);
+        context2006.setPensionRate(0.0);
+        context2006.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2006 = engine.calculate(
+                context2006, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Context 2007: Post-reform flat 10% era
+        SimulationContext context2007 = new SimulationContext();
+        context2007.setProfile(profile);
+        context2007.setPensionConfig(pensionConfig);
+        context2007.setYear(2007);
+        context2007.setHealthInsuranceRate(0.0);
+        context2007.setPensionRate(0.0);
+        context2007.setEmploymentInsuranceRate(0.0);
+
+        TaxCalculationResult res2007 = engine.calculate(
+                context2007, grossSalary, 0L, 0, 0, 0,
+                new MortgageYearlyResult(0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
+
+        // Under 2006 tiers, taxable income around 1.5M was taxed at 5%, whereas in 2007 it was taxed at flat 10%
+        assertThat(res2007.inhabitantTax()).isGreaterThan(res2006.inhabitantTax());
+    }
 }
 
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java
deleted file mode 100644
index 788f9a8a..00000000
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java
+++ /dev/null
@@ -1,50 +0,0 @@
-package ch.lin.finance.hub.backend.api.domain.model.config.tax;
-
-import java.math.BigDecimal;
-
-import ch.lin.platform.domain.model.AuditableEntity;
-import jakarta.persistence.Column;
-import jakarta.persistence.Entity;
-import jakarta.persistence.EnumType;
-import jakarta.persistence.Enumerated;
-import jakarta.persistence.JoinColumn;
-import jakarta.persistence.ManyToOne;
-import jakarta.persistence.Table;
-import lombok.AccessLevel;
-import lombok.Getter;
-import lombok.NoArgsConstructor;
-
-@Entity
-@Table(name = TaxBracket.TABLE_NAME)
-@Getter
-@NoArgsConstructor(access = AccessLevel.PROTECTED)
-public class TaxBracket extends AuditableEntity {
-
-    public static final String TABLE_NAME = "tax_brackets";
-
-    @Column(name = "max_salary", nullable = false)
-    private Long maxSalary;
-
-    @Column(name = "multiplier", nullable = false, precision = 5, scale = 4)
-    private BigDecimal multiplier;
-
-    @Column(name = "offset_value", nullable = false)
-    private Long offset;
-
-    @Enumerated(EnumType.STRING)
-    @Column(name = "bracket_type", nullable = false, length = 50)
-    private TaxBracketType bracketType;
-
-    @ManyToOne(optional = false)
-    @JoinColumn(name = "tax_rule_config_id", nullable = false)
-    private TaxRuleConfig taxRuleConfig;
-
-    public TaxBracket(Long maxSalary, BigDecimal multiplier, Long offset, TaxBracketType bracketType,
-            TaxRuleConfig taxRuleConfig) {
-        this.maxSalary = maxSalary;
-        this.multiplier = multiplier;
-        this.offset = offset;
-        this.bracketType = bracketType;
-        this.taxRuleConfig = taxRuleConfig;
-    }
-}
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java
deleted file mode 100644
index 10bbed22..00000000
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java
+++ /dev/null
@@ -1,7 +0,0 @@
-package ch.lin.finance.hub.backend.api.domain.model.config.tax;
-
-public enum TaxBracketType {
-    EMPLOYMENT_INCOME_DEDUCTION, // 薪資所得扣除
-    INCOME_TAX_RATE, // 所得稅率
-    INHABITANT_TAX_RATE // 住民稅率
-}
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java
deleted file mode 100644
index 38f939f1..00000000
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java
+++ /dev/null
@@ -1,55 +0,0 @@
-package ch.lin.finance.hub.backend.api.domain.model.config.tax;
-
-import java.util.ArrayList;
-import java.util.List;
-
-import ch.lin.platform.domain.model.AuditableEntity;
-import jakarta.persistence.CascadeType;
-import jakarta.persistence.Column;
-import jakarta.persistence.Entity;
-import jakarta.persistence.OneToMany;
-import jakarta.persistence.Table;
-import jakarta.persistence.UniqueConstraint;
-import lombok.AccessLevel;
-import lombok.EqualsAndHashCode;
-import lombok.Getter;
-import lombok.NoArgsConstructor;
-import lombok.Setter;
-
-@Entity
-@Table(name = TaxRuleConfig.TABLE_NAME, uniqueConstraints = {
-        @UniqueConstraint(name = TaxRuleConfig.UNIQUE_NAME, columnNames = TaxRuleConfig.NAME_COLUMN)
-})
-@Getter
-@Setter
-@NoArgsConstructor(access = AccessLevel.PROTECTED)
-@EqualsAndHashCode(of = { "name", "enabled" }, callSuper = false)
-public class TaxRuleConfig extends AuditableEntity {
-
-    public static final String TABLE_NAME = "tax_rule_configs";
-    public static final String UNIQUE_NAME = "uk_tax_rule_config_name";
-    public static final String NAME_COLUMN = "name";
-
-    @Column(name = "name", nullable = false)
-    private String name;
-
-    @Column(name = "enabled", nullable = false)
-    private Boolean enabled;
-
-    @OneToMany(mappedBy = "taxRuleConfig", cascade = CascadeType.ALL, orphanRemoval = true)
-    @jakarta.persistence.OrderBy("maxSalary ASC")
-    private final List<TaxBracket> taxBrackets = new ArrayList<>();
-
-    public TaxRuleConfig(String name, Boolean enabled) {
-        this.name = name;
-        this.enabled = enabled;
-    }
-
-    public void addTaxBracket(TaxBracket bracket) {
-        taxBrackets.add(bracket);
-    }
-
-    public void clearTaxBrackets() {
-        taxBrackets.clear();
-    }
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java
deleted file mode 100644
index 40c1cee7..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java
+++ /dev/null
@@ -1,172 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config;
-
-import java.net.URI;
-import java.util.List;
-import java.util.stream.Collectors;
-
-import org.slf4j.Logger;
-import org.slf4j.LoggerFactory;
-import org.springframework.http.ResponseEntity;
-import org.springframework.web.bind.annotation.DeleteMapping;
-import org.springframework.web.bind.annotation.GetMapping;
-import org.springframework.web.bind.annotation.PatchMapping;
-import org.springframework.web.bind.annotation.PathVariable;
-import org.springframework.web.bind.annotation.PostMapping;
-import org.springframework.web.bind.annotation.RequestBody;
-import org.springframework.web.bind.annotation.RequestMapping;
-import org.springframework.web.bind.annotation.RestController;
-import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
-
-import ch.lin.finance.hub.backend.api.app.service.config.tax.TaxRuleConfigService;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.CreateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.TaxBracketCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.UpdateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.AllTaxRuleConfigsResponse;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.CreateTaxRuleConfigRequest;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.TaxRuleConfigMapper;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.TaxRuleConfigResponse;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.UpdateTaxRuleConfigRequest;
-import ch.lin.platform.api.ApiResponse;
-import jakarta.validation.Valid;
-
-/**
- * REST controller for managing global Tax Rule configurations.
- */
-@RestController
-@RequestMapping("/configs/tax-rule")
-public class TaxRuleConfigController {
-
-    private static final Logger logger = LoggerFactory.getLogger(TaxRuleConfigController.class);
-
-    private final TaxRuleConfigService configService;
-    private final TaxRuleConfigMapper configMapper;
-
-    public TaxRuleConfigController(TaxRuleConfigService configService, TaxRuleConfigMapper configMapper) {
-        this.configService = configService;
-        this.configMapper = configMapper;
-    }
-
-    /**
-     * Retrieves the names of all available tax rule configurations and identifies
-     * which one is currently enabled.
-     */
-    @GetMapping
-    public ResponseEntity<AllTaxRuleConfigsResponse> getAllConfigs() {
-        logger.debug("Fetching all tax rule configurations.");
-        AllTaxRuleConfigsData configsData = configService.getAllConfigs();
-        AllTaxRuleConfigsResponse response = new AllTaxRuleConfigsResponse(configsData.getEnabledConfigName(),
-                configsData.getAllConfigNames());
-        return ResponseEntity.ok(response);
-    }
-
-    /**
-     * Retrieves a specific tax rule configuration by name.
-     */
-    @GetMapping("/{name}")
-    public ResponseEntity<ApiResponse<TaxRuleConfigResponse>> getConfig(@PathVariable String name) {
-        logger.debug("Fetching tax rule configuration: {}", name);
-        TaxRuleConfigResponse response = configMapper.toResponse(configService.getConfig(name));
-        return ResponseEntity.ok(ApiResponse.success(response));
-    }
-
-    /**
-     * Creates a new tax rule configuration.
-     */
-    @PostMapping
-    public ResponseEntity<ApiResponse<TaxRuleConfigResponse>> createConfig(
-            @Valid @RequestBody CreateTaxRuleConfigRequest request) {
-        logger.info("Received request to create a new tax rule configuration: {}", request.getName());
-
-        List<TaxBracketCommand> bracketCommands = null;
-        if (request.getBrackets() != null) {
-            bracketCommands = request.getBrackets().stream()
-                    .map(b -> TaxBracketCommand.builder()
-                            .bracketType(b.getBracketType())
-                            .maxSalary(b.getMaxSalary())
-                            .multiplier(b.getMultiplier())
-                            .offset(b.getOffset())
-                            .build())
-                    .collect(Collectors.toList());
-        }
-
-        CreateTaxRuleConfigCommand command = CreateTaxRuleConfigCommand.builder()
-                .name(request.getName())
-                .enabled(Boolean.TRUE.equals(request.getEnabled()))
-                .brackets(bracketCommands)
-                .build();
-
-        TaxRuleConfigResponse createdConfig = configMapper.toResponse(configService.createConfig(command));
-
-        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
-                .path("/{name}")
-                .buildAndExpand(createdConfig.getName())
-                .toUri();
-
-        return ResponseEntity.created(location)
-                .body(ApiResponse.success(createdConfig));
-    }
-
-    /**
-     * Partially updates an existing tax rule configuration.
-     */
-    @PatchMapping("/{name}")
-    public ResponseEntity<ApiResponse<TaxRuleConfigResponse>> updateConfig(
-            @PathVariable String name,
-            @Valid @RequestBody UpdateTaxRuleConfigRequest request) {
-        logger.info("Received request to update tax rule configuration: {}", name);
-
-        List<TaxBracketCommand> bracketCommands = null;
-        if (request.getBrackets() != null) {
-            bracketCommands = request.getBrackets().stream()
-                    .map(b -> TaxBracketCommand.builder()
-                            .bracketType(b.getBracketType())
-                            .maxSalary(b.getMaxSalary())
-                            .multiplier(b.getMultiplier())
-                            .offset(b.getOffset())
-                            .build())
-                    .collect(Collectors.toList());
-        }
-
-        UpdateTaxRuleConfigCommand command = UpdateTaxRuleConfigCommand.builder()
-                .enabled(request.getEnabled())
-                .brackets(bracketCommands)
-                .build();
-
-        TaxRuleConfigResponse updatedConfig = configMapper.toResponse(configService.updateConfig(name, command));
-        return ResponseEntity.ok(ApiResponse.success(updatedConfig));
-    }
-
-    /**
-     * Deletes a tax rule configuration.
-     */
-    @DeleteMapping("/{name}")
-    public ResponseEntity<Void> deleteConfig(@PathVariable String name) {
-        logger.info("Received request to delete tax rule configuration: {}", name);
-        configService.deleteConfig(name);
-        return ResponseEntity.noContent().build();
-    }
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/AllTaxRuleConfigsResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/AllTaxRuleConfigsResponse.java
deleted file mode 100644
index 57b9be55..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/AllTaxRuleConfigsResponse.java
+++ /dev/null
@@ -1,43 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.util.List;
-
-import lombok.AllArgsConstructor;
-import lombok.Builder;
-import lombok.Data;
-import lombok.NoArgsConstructor;
-
-/**
- * Response DTO containing metadata about all TaxRuleConfigs.
- */
-@Data
-@Builder
-@NoArgsConstructor
-@AllArgsConstructor
-public class AllTaxRuleConfigsResponse {
-    private String enabledConfigName;
-    private List<String> allConfigNames;
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java
deleted file mode 100644
index 02d5bfa6..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java
+++ /dev/null
@@ -1,53 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.util.List;
-
-import jakarta.validation.Valid;
-import jakarta.validation.constraints.NotBlank;
-import jakarta.validation.constraints.NotNull;
-import lombok.AllArgsConstructor;
-import lombok.Builder;
-import lombok.Data;
-import lombok.NoArgsConstructor;
-
-/**
- * Request DTO for creating a new TaxRuleConfig.
- */
-@Data
-@Builder
-@NoArgsConstructor
-@AllArgsConstructor
-public class CreateTaxRuleConfigRequest {
-
-    @NotBlank
-    private String name;
-
-    @NotNull
-    private Boolean enabled;
-
-    @Valid
-    private List<TaxBracketRequest> brackets;
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java
deleted file mode 100644
index 7ee537de..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java
+++ /dev/null
@@ -1,54 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.math.BigDecimal;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-import jakarta.validation.constraints.NotNull;
-import lombok.AllArgsConstructor;
-import lombok.Builder;
-import lombok.Data;
-import lombok.NoArgsConstructor;
-
-/**
- * Request DTO for a Tax Bracket.
- */
-@Data
-@Builder
-@NoArgsConstructor
-@AllArgsConstructor
-public class TaxBracketRequest {
-
-    @NotNull
-    private TaxBracketType bracketType;
-
-    private Long maxSalary;
-
-    @NotNull
-    private BigDecimal multiplier;
-
-    @NotNull
-    private Long offset;
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketResponse.java
deleted file mode 100644
index d09d04d1..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketResponse.java
+++ /dev/null
@@ -1,42 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.math.BigDecimal;
-
-import ch.lin.finance.hub.backend.api.domain.model.config.tax.TaxBracketType;
-import lombok.Builder;
-import lombok.Data;
-
-/**
- * Response DTO for a Tax Bracket.
- */
-@Data
-@Builder
-public class TaxBracketResponse {
-    private TaxBracketType bracketType;
-    private Long maxSalary;
-    private BigDecimal multiplier;
-    private Long offset;
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java
deleted file mode 100644
index 54cb3569..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java
+++ /dev/null
@@ -1,36 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import org.mapstruct.Mapper;
-
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
-
-/**
- * Mapper for TaxRuleConfig DTOs.
- */
-@Mapper(componentModel = "spring")
-public interface TaxRuleConfigMapper {
-    TaxRuleConfigResponse toResponse(TaxRuleConfigResult result);
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java
deleted file mode 100644
index 2596261a..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java
+++ /dev/null
@@ -1,40 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.util.List;
-
-import lombok.Builder;
-import lombok.Data;
-
-/**
- * Response DTO for a TaxRuleConfig.
- */
-@Data
-@Builder
-public class TaxRuleConfigResponse {
-    private String name;
-    private Boolean enabled;
-    private List<TaxBracketResponse> brackets;
-}
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java
deleted file mode 100644
index 01edd8e1..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java
+++ /dev/null
@@ -1,47 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config.dto.tax;
-
-import java.util.List;
-
-import jakarta.validation.Valid;
-import lombok.AllArgsConstructor;
-import lombok.Builder;
-import lombok.Data;
-import lombok.NoArgsConstructor;
-
-/**
- * Request DTO for updating an existing TaxRuleConfig.
- */
-@Data
-@Builder
-@NoArgsConstructor
-@AllArgsConstructor
-public class UpdateTaxRuleConfigRequest {
-
-    private Boolean enabled;
-
-    @Valid
-    private List<TaxBracketRequest> brackets;
-}
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index 76f0ea38..e4b23d44 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -110,65 +110,6 @@ finance.hub.system-config.ga-max-generations=20
 finance.hub.system-config.ga-mutation-rate=0.2
 finance.hub.system-config.ga-elite-ratio=10
 
-# =============================================================================
-# Tax Rule Defaults
-# =============================================================================
-
-# Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=740000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=3600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.3
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=80000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=6600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.2
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=440000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=8500000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.1
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=1100000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1950000
-
-# Tax Brackets: INCOME_TAX_RATE (所得稅率級距)
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].max-salary=1950000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].multiplier=0.05
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].offset=0
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].max-salary=3300000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].multiplier=0.10
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].offset=-97500
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].max-salary=6950000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].multiplier=0.20
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].offset=-427500
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].max-salary=9000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].multiplier=0.23
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].offset=-636000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].max-salary=18000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].multiplier=0.33
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].offset=-1536000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].max-salary=40000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].multiplier=0.40
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].offset=-2796000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].multiplier=0.45
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].offset=-4796000
-
-# Tax Brackets: INHABITANT_TAX_RATE (住民稅率)
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].multiplier=0.10
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].offset=0
-
 # =============================================================================
 # Pension Rule Defaults
 # =============================================================================
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index fd75ecbd..aec850eb 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -104,65 +104,6 @@ finance.hub.system-config.ga-elite-ratio=10
 # Simulation Defaults
 finance.hub.system-config.simulation-early-repayment-month=12
 
-# =============================================================================
-# Tax Rule Defaults
-# =============================================================================
-
-# Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=740000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=3600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.3
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=80000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=6600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.2
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=440000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=8500000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.1
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=1100000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1950000
-
-# Tax Brackets: INCOME_TAX_RATE (所得稅率級距)
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].max-salary=1950000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].multiplier=0.05
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].offset=0
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].max-salary=3300000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].multiplier=0.10
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[1].offset=-97500
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].max-salary=6950000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].multiplier=0.20
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[2].offset=-427500
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].max-salary=9000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].multiplier=0.23
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[3].offset=-636000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].max-salary=18000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].multiplier=0.33
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[4].offset=-1536000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].max-salary=40000000
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].multiplier=0.40
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[5].offset=-2796000
-
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].multiplier=0.45
-finance-hub.tax-rule.brackets.INCOME_TAX_RATE[6].offset=-4796000
-
-# Tax Brackets: INHABITANT_TAX_RATE (住民稅率)
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].multiplier=0.10
-finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0].offset=0
-
 # =============================================================================
 # Pension Rule Defaults
 # =============================================================================
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java
deleted file mode 100644
index 775f2ba9..00000000
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java
+++ /dev/null
@@ -1,119 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.config;
-
-import java.util.List;
-import java.util.Objects;
-
-import org.junit.jupiter.api.BeforeEach;
-import org.junit.jupiter.api.DisplayName;
-import org.junit.jupiter.api.Test;
-import org.junit.jupiter.api.extension.ExtendWith;
-import org.mapstruct.factory.Mappers;
-import static org.mockito.ArgumentMatchers.any;
-import org.mockito.InjectMocks;
-import org.mockito.Mock;
-import static org.mockito.Mockito.times;
-import static org.mockito.Mockito.verify;
-import static org.mockito.Mockito.when;
-import org.mockito.Spy;
-import org.mockito.junit.jupiter.MockitoExtension;
-import org.springframework.http.MediaType;
-import org.springframework.test.web.servlet.MockMvc;
-import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
-import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
-import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
-import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
-import org.springframework.test.web.servlet.setup.MockMvcBuilders;
-
-import com.fasterxml.jackson.databind.ObjectMapper;
-
-import ch.lin.finance.hub.backend.api.app.service.config.tax.TaxRuleConfigService;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.command.CreateTaxRuleConfigCommand;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.AllTaxRuleConfigsData;
-import ch.lin.finance.hub.backend.api.app.service.config.tax.result.TaxRuleConfigResult;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.CreateTaxRuleConfigRequest;
-import ch.lin.finance.hub.backend.api.controller.config.dto.tax.TaxRuleConfigMapper;
-
-@ExtendWith(MockitoExtension.class)
-class TaxRuleConfigControllerTest {
-
-    private MockMvc mockMvc;
-
-    @Mock
-    private TaxRuleConfigService configService;
-
-    @Spy
-    @SuppressWarnings("unused")
-    private final TaxRuleConfigMapper configMapper = Mappers.getMapper(TaxRuleConfigMapper.class);
-
-    @InjectMocks
-    private TaxRuleConfigController controller;
-
-    private final ObjectMapper objectMapper = new ObjectMapper();
-
-    @BeforeEach
-    @SuppressWarnings("unused")
-    void setUp() {
-        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
-    }
-
-    @Test
-    @DisplayName("Should get all configs successfully")
-    void getAllConfigs_ReturnsAllConfigsResponse() throws Exception {
-        AllTaxRuleConfigsData data = new AllTaxRuleConfigsData("default", List.of("default", "custom"));
-        when(configService.getAllConfigs()).thenReturn(data);
-
-        mockMvc.perform(get("/configs/tax-rule"))
-                .andExpect(status().isOk())
-                .andExpect(jsonPath("$.enabledConfigName").value("default"))
-                .andExpect(jsonPath("$.allConfigNames[0]").value("default"))
-                .andExpect(jsonPath("$.allConfigNames[1]").value("custom"));
-
-        verify(configService, times(1)).getAllConfigs();
-    }
-
-    @Test
-    @DisplayName("Should create config successfully")
-    void createConfig_ReturnsCreatedConfig() throws Exception {
-        CreateTaxRuleConfigRequest request = CreateTaxRuleConfigRequest.builder()
-                .name("custom")
-                .enabled(true)
-                .build();
-
-        TaxRuleConfigResult result = TaxRuleConfigResult.builder()
-                .name("custom")
-                .enabled(true)
-                .build();
-
-        when(configService.createConfig(any(CreateTaxRuleConfigCommand.class))).thenReturn(result);
-
-        mockMvc.perform(post("/configs/tax-rule")
-                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
-                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
-                .andExpect(status().isCreated())
-                .andExpect(jsonPath("$.data.name").value("custom"))
-                .andExpect(jsonPath("$.data.enabled").value(true));
-    }
-}
diff --git a/finance-hub-backend/todo/pending fix/issue-142.md b/finance-hub-backend/todo/pending fix/issue-142.md
index 0463e21e..58547ab8 100644
--- a/finance-hub-backend/todo/pending fix/issue-142.md
+++ b/finance-hub-backend/todo/pending fix/issue-142.md
@@ -34,3 +34,352 @@
 - 做法：在`SimulationContextFactory`或`TaxConfigDto`附近加註解，明確寫出「這份級距表只對2026-2027年正確，2028年後需要重新查證」，避免之後有人誤以為這份設定對所有年度都適用。
 - 優點：零風險，純粹補一個提醒。
 - 缺點：沒有真正解決問題，只是降低「忘記這件事」的風險。
+
+---
+
+## 審查回饋與架構深度分析（Review & Architectural Analysis）
+
+經連線日本國稅廳（NTA）最新官方指引、e-Gov 日本法令資料庫（所得稅法、租稅特別措置法、地方稅法）以及後端現行實作程式碼，**本 Issue 描述之現象與缺陷 100% 屬實（非幻覺）**。給與所得控除目前確實是全系統稅務計算引擎中唯一一個「停留在資料庫/設定檔單一靜態設定、未隨模擬年度動態切換」的架構孤島，導致長線模擬在 2028 年以後持續誤用 2026-2027 年專屬之 74 萬時限特例。
+
+同時，針對原文第 27 行提及之「2028 年後級距未查得可信來源、光靠位移模式腦補風險高」之顧慮，**本審查已全數查齊官方母法與速算表（Single Source of Truth, SSOT）**，所有數值、轉折連續性與階梯邊界完全精確確定。以下為詳細分析與處置方案：
+
+### 一、現象屬實查核（代碼架構缺陷確實存在）
+
+1. **模組架構不一致（架構孤島）**：
+   在 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 中，其他所得稅核心扣除項目皆已成熟演進為「依模擬年度動態分派」的 Strategy Factory 模式：
+   * **基礎控除**：`BasicDeductionStrategyFactory`（4 個時代，含 2028 年本則落日）
+   * **配偶控除**：`SpouseDeductionStrategyFactory`（對齊 Question 141 新增 2028 年本則落日）
+   * **扶養親屬控除**：`DependentDeductionStrategyFactory`（4 個時代，含 2028 年本則落日）
+   * **給與所得控除（薪資扣除）**：依然在第 98-99 行讀取 `context.getTaxConfig().getEmploymentIncomeDeductionBrackets()`。
+
+2. **`application.properties` 設定檔之級距定性（誤把 2 年時限特例當永久全域規則）**：
+   * 檢視 `application.properties:111-131` 與 `application-h2.properties:117-136`，目前種進設定檔的數值為 [Fixed-102](../fixed/Fixed-102.md) 修正後對應**令和 8～9 年（2026～2027 年）的 5 段級距表**（最低 74 萬、最高上限 195 萬）。
+   * **本質錯誤**：這份設定檔本質上**缺乏「年度（Year）」維度**。若模擬從 2024 年或 2025 年開始跑，它提前誤用了尚未生效的 74 萬特例（令和7年應為 65 萬）；若模擬跨過 2028 年，它又誤用了已經落日的 74 萬特例（令和10年起應回落至 69 萬本則）。
+   * **處置定性**：本次重構應徹底**拔掉 `application.properties` 與 `application-h2.properties` 裡的 `EMPLOYMENT_INCOME_DEDUCTION` 靜態設定**，改採 Strategy Factory（策略模式）動態計算，消除全專案最後一個靜態設定技術債。
+
+3. **模擬全生命週期凍結**：
+   [`SimulationContextFactory.java:108-142`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java#L108-L142) 僅在模擬啟動時從資料庫抓取一次該級距表。在長達 30～50 年之模擬週期（例如 2026 年模擬至 2076 年），系統**全程鎖死在 2026～2027 年之特例級距表**，2028 年及以後完全無法切換。
+
+4. **實質數值偏差與連鎖反應**：
+   * **高收入族群壓力測試（年薪 2,000 萬～3,500 萬日圓）**：受薪年收超過 850 萬日圓者，法定扣除上限永遠固定為 **195 萬日圓**，此高所得極端值在所有時代皆不受特例落日影響，無數值偏差。
+   * **低所得與轉職/退職過渡期偏差**：年收在 220 萬以下時，2028 年起最低保障額依法應由 74 萬回跌至 69 萬。現行代碼持續多算 5 萬扣除額，導致受薪所得 `employmentIncome` 被低估 5 萬。此偏差會連鎖污染下游之基礎控除、配偶特別控除，以及房貸減稅排富門檻（`maxTotalIncomeForMortgageDeduction`）之判定。
+
+---
+
+### 二、官方最新法定條文與跨時代完整級距表（Single Source of Truth）
+
+依據日本國稅廳 TaxAnswer [No.1410 給与所得控除](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1410.htm)、《日本所得稅法》第 28 條第 2～3 項、新訂《租稅特別措置法》第 29 條之 4，完整 4 時代法定計算級距與母法依據如下：
+
+#### 1. 法源依據與時代劃分：
+* **第一時代（Legacy，$\le 2024$ 年）**：所得稅法第 28 條舊法（令和 2 年～令和 6 年分適用）。最低保障額 55 萬円。
+* **第二時代（Reiwa 7，2025 年）**：令和 7 年度稅制改正。最低保障額調升為 65 萬円。
+* **第三時代（Reiwa 8 & 9，2026～2027 年）**：租稅特別措置法第 29 條之 4。時限特例最低保障額加計 5 萬，調升為 **74 萬円**（本則 69 萬 ＋ 特例 5 萬）。
+* **第四時代（Reiwa 10 Onwards，$\ge 2028$ 年）**：所得稅法第 28 條第 3 項修正案（恒久本則）。租特法時限特例落日，最低保障額回歸 **69 萬円**。
+
+---
+
+#### 2. 日本官方跨時代完整給與所得控除級距表：
+
+| 時代區間 | 適用年份 | 給與年收級距（$S$） | 法定給與所得控除額計算公式 | 備註說明 |
+| :--- | :---: | :--- | :--- | :--- |
+| **第一時代<br>(Legacy)** | **$\le 2024$** | $S \le 162.5\text{萬}$<br>$162.5\text{萬} < S \le 180\text{萬}$<br>$180\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **550,000 円**<br>$S \times 40\% - 100,000$<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | 舊制 6 階速算表<br>（103 萬壁時期） |
+| **第二時代<br>(Reiwa 7)** | **2025** | $S \le 190\text{萬}$<br>$190\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **650,000 円**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | 5 階速算表<br>（123 萬壁過渡期，190萬平滑銜接） |
+| **第三時代<br>(Reiwa 8～9)** | **2026～2027** | $S \le 220\text{萬}$<br>$220\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **740,000 円**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | **租特法第29條之4**<br>（現行資料庫寫死的這份，220萬平滑銜接） |
+| **第四時代<br>(Reiwa 10+)** | **$\ge 2028$** | $S \le 220\text{萬}$<br>$220\text{萬} < S \le 360\text{萬}$<br>$360\text{萬} < S \le 660\text{萬}$<br>$660\text{萬} < S \le 850\text{萬}$<br>$S > 850\text{萬}$ | **$\max(690,000, S \times 30\% + 80,000)$**<br>$S \times 30\% + 80,000$<br>$S \times 20\% + 440,000$<br>$S \times 10\% + 1,100,000$<br>**1,950,000 円（上限）** | **所法第28條第3項本則**<br>（特例落日回歸69萬，實質轉折點為 203.33 萬円） |
+
+> **第四時代轉折點嚴謹數理推導（杜絕任何腦補質疑）**：
+> * 令和 10 年本則之法定最低保障額為 69 萬円。
+> * 當給與收入落在 $S \le 220\text{萬}$ 時，計算式為 $S \times 30\% + 80,000$。解算式與最低保障額交界點：
+>   $$S \times 30\% + 80,000 = 690,000 \implies S = \frac{610,000}{0.3} = 2,033,333.33\dots\text{円}$$
+> * 當 $S \le 2,033,333$ 円時，算式未滿 69 萬，依法保障給予 **69 萬円**。
+> * 當 $2,033,334 \le S \le 2,200,000$ 円時，算式產出值落在 69 萬～74 萬之間，自然平滑過渡至 220 萬階梯（$220\text{萬} \times 30\% + 8\text{萬} = 74\text{萬}$）。
+> * **切勿草率以 190 萬作為邊界**：若誤以為第一階在 190 萬截止，則年薪 195 萬時會算出 $195\text{萬} \times 30\% + 8\text{萬} = 66.5\text{萬}$，違法跌破 69 萬保障額。採用 $\max(690,000, S \times 30\% + 80,000)$ 能在代碼層次實現 100% 絕對安全防護。
+
+---
+
+### 三、拍板架構修正方案：採納「方向 A」（建立策略工廠，拔除設定檔）
+
+為消除資料庫/設定檔靜態寫死之架構技術債，並與其他三個扣除模組達到 100% 架構對齊，拍板採納**方向 A**：
+
+#### 1. 策略模式架構設計
+建立全新套件：`ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment`
+
+1. **核心介面**：
+   ```java
+   public interface EmploymentIncomeDeductionStrategy {
+       long calculateDeduction(long grossSalary);
+   }
+   ```
+2. **四個具體時代實作類別**：
+   * `LegacyEmploymentIncomeDeductionStrategy.java`（最低 55 萬）
+   * `Reiwa7EmploymentIncomeDeductionStrategy.java`（最低 65 萬）
+   * `Reiwa8And9EmploymentIncomeDeductionStrategy.java`（最低 74 萬，特例期）
+   * `Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`（最低 69 萬本則，$\max(690,000, S \times 30\% + 80,000)$）
+3. **策略工廠**：
+   `EmploymentIncomeDeductionStrategyFactory.java`
+   ```java
+   @Component
+   public class EmploymentIncomeDeductionStrategyFactory {
+       public EmploymentIncomeDeductionStrategy getStrategy(int year) {
+           if (year <= 2024) {
+               return legacyStrategy;
+           } else if (year == 2025) {
+               return reiwa7Strategy;
+           } else if (year <= 2027) {
+               return reiwa8And9Strategy;
+           } else {
+               return reiwa10OnwardsStrategy; // >= 2028 正確切換回 69 萬本則策略
+           }
+       }
+   }
+   ```
+
+#### 2. 計算引擎重構與靜態設定拔除
+1. **重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)**：
+   * 注入 `EmploymentIncomeDeductionStrategyFactory`。
+   * 將原有的私有方法 `calculateEmploymentIncomeDeduction(grossTotalSalary, brackets)` 汰換，改為：
+     ```java
+     long employmentIncomeDeduction = employmentIncomeDeductionStrategyFactory
+             .getStrategy(simulationYear)
+             .calculateDeduction(grossTotalSalary);
+     ```
+2. **徹底拔除靜態設定與孤島欄位**：
+   * 從 `ActiveTaxCalculationEngine` 移除對 `context.getTaxConfig().getEmploymentIncomeDeductionBrackets()` 之依賴。
+   * 從 `SimulationContextFactory.java` 與 `TaxConfigDto` 清理/拔除該靜態級距的加載邏輯。
+   * 在 `application.properties` 與 `application-h2.properties` 拔除或標記廢棄 `finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION` 設定項。
+
+---
+
+### 四、實作計畫與驗證規範（供 PM / Scrum Master 決策）
+
+#### 1. 預計異動檔案清單
+1. **新建策略族**：
+   * `.../strategy/tax/deduction/employment/EmploymentIncomeDeductionStrategy.java`
+   * `.../strategy/tax/deduction/employment/LegacyEmploymentIncomeDeductionStrategy.java`
+   * `.../strategy/tax/deduction/employment/Reiwa7EmploymentIncomeDeductionStrategy.java`
+   * `.../strategy/tax/deduction/employment/Reiwa8And9EmploymentIncomeDeductionStrategy.java`
+   * `.../strategy/tax/deduction/employment/Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`
+   * `.../strategy/tax/deduction/employment/EmploymentIncomeDeductionStrategyFactory.java`
+2. **引擎重構與依賴清理**：
+   * [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
+   * `SimulationContextFactory.java` / `TaxConfigDto.java`
+   * `application.properties` / `application-h2.properties`
+3. **單元測試**：新建 `EmploymentIncomeDeductionStrategyTest.java`，並更新 `ActiveTaxCalculationEngineTest.java`。
+4. **文件歸檔**：待實作完成並驗證通過後，轉檔至 `todo/fixed/Fixed-142.md`。
+
+#### 2. 單元測試驗證矩陣
+* **2024 年以前舊制驗證**：年收 150 萬扣除 55 萬；年收 500 萬扣除 144 萬；年收 1,000 萬扣除 195 萬。
+* **2025 年（令和 7 年）驗證**：年收 180 萬扣除 65 萬；年收 500 萬扣除 144 萬；年收 1,000 萬扣除 195 萬。
+* **2026～2027 年特例期驗證**：年收 200 萬扣除 74 萬；年收 300 萬扣除 98 萬；年收 1,000 萬扣除 195 萬。
+* **2028 年起本則落日驗證（精確邊界）**：
+  * 年收 180 萬扣除 **69 萬**（保障最低額）
+  * 年收 200 萬扣除 **69 萬**（保障最低額，精準防禦 190 萬破底瑕疵）
+  * 年收 210 萬扣除 **71 萬**（$210\text{萬} \times 30\% + 8\text{萬} = 71\text{萬}$，平滑過渡）
+  * 年收 220 萬扣除 **74 萬**（無縫銜接下一段）
+  * 年收 300 萬扣除 **98 萬**
+  * 年收 1,000 萬扣除 **195 萬**
+* **高收入族群極端邊界測試**：年收 2,000 萬與 3,500 萬日圓，扣除額 100% 精確封頂為 195 萬日圓。
+
+---
+
+### 五、三階段漸進式實作計畫（Phased Implementation Plan）
+
+為防範一次性異動 12+ 檔案跨越前後端導致 Content Window / Context Token 耗盡，並徹底根絕「後端已跑策略模式、前端卻仍殘留無效靜態設定」之後續認知混亂，本案採納 PM 裁決之三階段漸進式實作：
+
+#### 🟢 Phase 1.1：核心計算引擎策略模式落地（消除業務邏輯 Bug）✅【已完成】
+* **目標**：以最小變動半徑，**立即修復長線模擬在 2028 年後誤用 74 萬特例的實質 Bug**。
+* **異動範圍**：
+  1. **新建策略模式套件與類別**（`.../strategy/tax/deduction/employment/`）：
+     * `EmploymentIncomeDeductionStrategy.java`（介面）
+     * `LegacyEmploymentIncomeDeductionStrategy.java`（舊制 $\le 2024$ 年，最低保障 55 萬）
+     * `Reiwa7EmploymentIncomeDeductionStrategy.java`（令和 7 年 2025 年，最低保障 65 萬，190 萬銜接點）
+     * `Reiwa8And9EmploymentIncomeDeductionStrategy.java`（令和 8～9 年 2026～2027 年，特例最低 74 萬，220 萬平滑點）
+     * `Reiwa10OnwardsEmploymentIncomeDeductionStrategy.java`（令和 10 年 $\ge 2028$ 年，本則最低 69 萬，$\max(690,000, S \times 30\% + 80,000)$）
+     * `EmploymentIncomeDeductionStrategyFactory.java`（四時代動態分派工廠）
+  2. **新建單元測試**：
+     * `EmploymentIncomeDeductionStrategyTest.java`（完整涵蓋法定 4 時代邊界、零收入/負收入保護與年收 2,000 萬～3,500 萬封頂 195 萬壓力測試）。
+  3. **計算引擎接軌**：
+     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入策略工廠，將薪資扣除改為呼叫 `employmentIncomeDeductionStrategyFactory.getStrategy(simulationYear).calculateDeduction(grossTotalSalary)`，徹底移除私有靜態方法 `calculateEmploymentIncomeDeduction`。
+     * 依 PM 指令拔除過渡期向後兼容建構子，維持嚴謹的 5 參數依賴注入架構，同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java) 實例化注入。
+     * 新增 `calculate_crossEraEmploymentIncomeDeduction_provesBugFixFor2028` 整合測試，驗證跨四時代（2024、2025、2026、2028）之動態扣除與 2028 年特例失效回歸 69 萬法定本則之計算正確性。
+* **驗證與交付**：核心稅務算法 100% 依法定標準修復，不破壞任何現有 DTO 與 Controller。
+
+
+---
+
+#### 🟢 Phase 1.2：所得稅率級距策略模式落地（動態化跨期級距運算）✅【已完成】
+* **背景與動機**：
+  * 當前核心計算引擎 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 之基準所得稅運算，直接依賴外部靜態傳入之 `context.getTaxConfig().getIncomeTaxRateBrackets()`（來自單一年度之 DB 或 `application.properties` 設定）。
+  * 在 30~50 年的長跨度財務模擬中，若直接以單一靜態級距貫穿全模擬期，將無法應對跨時代稅制演變（例如：2014 年以前日本最高所得稅率為 40% 的 6 階制，2015 年起增設 45% 的 7 階制，未來亦可能因通膨或財政政策再度調整最高稅率門檻）。
+  * 為解除引擎對單一靜態列表之直接計算依賴，比照 `EmploymentIncomeDeductionStrategy`，導入 `IncomeTaxRateStrategy` 與動態分派工廠，達成法定計算動態化與高內聚架構。
+* **目標**：
+  * 建立所得稅率計算策略模式體系，依法定標準精確支援跨期累進稅率計算（2015 年至今現行 7 階制、2014 年以前 6 階制，並保留未來稅制擴充彈性）。
+  * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入 `IncomeTaxRateStrategyFactory`，將現役所得稅、退職所得稅、年金所得稅計算全面改由動態策略接管，徹底刪除私有靜態運算方法。
+* **異動範圍**：
+  1. **新建策略模式套件與類別**（`.../calculator/strategy/tax/rate/income/`）：
+     * [`IncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategy.java)（介面）：定義 `long calculateIncomeTax(long taxableIncome);`，輸入經千圓捨去之課稅所得，輸出基準所得稅額（未計復興特別所得稅 2.1% 前）。
+     * [`StandardIncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/StandardIncomeTaxRateStrategy.java)（現行法定 7 階累進制，2015 年／平成 27 年至今）：依所得税法第 89 条第 1 項規定之 7 級累進稅率（5%～45%，扣除額 0～4,796,000 円）。
+     * [`Pre2015IncomeTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/Pre2015IncomeTaxRateStrategy.java)（舊制 6 階累進制，$\le 2014$ 年／平成 26 年以前）：最高稅率為 1,800 萬以上 40%（扣除額 2,796,000 円），無 45% 級距。
+     * [`IncomeTaxRateStrategyFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategyFactory.java)（`@Component`）：根據模擬年度（`simulationYear`）分派對應策略（$\le 2014 \to$ 舊制；$\ge 2015 \to$ 現行制；未來新稅改直接擴展新 Strategy，引擎零異動）。
+  2. **新建單元測試**：
+     * [`IncomeTaxRateStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/income/IncomeTaxRateStrategyTest.java)：
+       * 零收入與負所得防禦測試（回傳 0）。
+       * 現行 7 階制各區間臨界點測試（195 萬、330 萬、695 萬、900 萬、1,800 萬、4,000 萬邊界與扣除額精確校驗）。
+       * 舊制 6 階制超高所得（如 5,000 萬）封頂 40% 之法規對照測試。
+       * 工廠跨年度分派測試（2010、2015、2026、2040 動態路由驗證）。
+  3. **計算引擎接軌與重構**：
+     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
+       * 構造函數注入 `IncomeTaxRateStrategyFactory`（嚴謹依賴注入，6 參數架構）。
+       * 在 `calculate`（現役）、`calculateRetirementTax`（退職）、`calculatePensionTax`（年金）三處，將 `calculateIncomeTax(..., context.getTaxConfig().getIncomeTaxRateBrackets())` 改為呼叫 `incomeTaxRateStrategyFactory.getStrategy(context.getYear()).calculateIncomeTax(...)`。
+       * 徹底移除私有靜態方法 `private long calculateIncomeTax(long taxableIncome, List<TaxBracketDto> brackets)`。
+     * 同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：更新實例化注入，並新增跨期所得稅率動態切換（2014 舊制 40% 封頂 vs 2015 現行制 45% 頂級）之回歸驗證測試 `calculate_crossEraIncomeTaxRate_provesStrategyPattern`。
+* **驗證與交付**：
+  * 所得稅累進計算 100% 策略化、動態化，完全阻絕跨期長線模擬之級距僵化問題。
+  * 核心引擎無任何語法錯誤與未使用引用，保持現有 API 與 Context 結構無破壞，平滑接軌。
+
+---
+
+#### 🟢 Phase 1.3：住民稅率策略模式落地（動態化法定住民稅計算）✅【已完成】
+* **背景與動機**：
+  * 當前核心計算引擎 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 之住民稅運算，在現役所得、退職所得與年金所得三處，均採用 `for (TaxBracketDto bracket : context.getTaxConfig().getInhabitantTaxRateBrackets())` 遍歷迴圈計算，直接依賴外部靜態傳入之級距設定。
+  * 依據《地方稅法》第 35 條與第 314 條之 3，日本住民稅所得割自 2007 年（平成 19 年）三位一體改革以來，全國一律為**標準單一扁平稅率 10%**（都道府縣 4% + 市區町村 6%）；而在 2006 年以前則為 3 階累進稅率（5%～13%）。
+  * 靜態設定檔用虛擬階梯（`max-salary=Long.MAX_VALUE, multiplier=0.10`）包裝扁平稅率，本質上是過度設計與架構硬編碼。比照所得稅率，導入 `InhabitantTaxRateStrategy` 與動態分派工廠，達成全系統稅制 100% 策略化。
+* **目標**：
+  * 建立住民稅率策略模式體系，依法定標準精確支援跨期稅率計算（2007 年至今法定標準扁平 10%、2006 年以前 3 階累進制，並保留特定自治體或未來稅改擴充彈性）。
+  * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：注入 `InhabitantTaxRateStrategyFactory`，將現役所得、退職所得、年金所得的住民稅計算全面改由動態策略接管，徹底刪除遍歷靜態級距的迴圈與依賴。
+* **異動範圍**：
+  1. **新建策略模式套件與類別**（`.../calculator/strategy/tax/rate/inhabitant/`）：
+     * [`InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategy.java)（介面）：定義 `long calculateInhabitantTax(long taxableIncome);`，輸入經千圓捨去之住民稅課稅所得，輸出基準住民稅所得割額。
+     * [`StandardInhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java)（現行法定標準制，2007 年至今）：依地方稅法標準稅率 10% 計算（`taxableIncome <= 0 ? 0L : (long)(taxableIncome * 0.10)`）。
+     * [`Pre2007InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/Pre2007InhabitantTaxRateStrategy.java)（舊制 3 階累進制，$\le 2006$ 年以前）：舊制 3 階超額累進（$\le 200\text{萬} \to 5\%$、$\le 700\text{萬} \to 10\% - 100,000$、$> 700\text{萬} \to 13\% - 310,000$）。
+     * [`InhabitantTaxRateStrategyFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyFactory.java)（`@Component`）：根據模擬年度（`simulationYear`）分派對應策略（$\le 2006 \to$ 舊制；$\ge 2007 \to$ 現行標準制；未來稅改可直接擴充 Strategy，引擎零異動）。
+  2. **新建單元測試**：
+     * [`InhabitantTaxRateStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyTest.java)：
+       * 零所得與負所得防禦測試（回傳 0）。
+       * 現行 10% 扁平稅率精確計算（100 萬 $\to$ 10 萬、500 萬 $\to$ 50 萬、1,000 萬 $\to$ 100 萬）。
+       * 舊制 3 階累進臨界點與超高所得 13% 對照驗證。
+       * 工廠跨年度分派測試（2000、2006、2007、2026、2040 動態路由驗證）。
+  3. **計算引擎接軌與重構**：
+     * 重構 [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
+       * 構造函數注入 `InhabitantTaxRateStrategyFactory`（7 參數嚴謹依賴注入架構）。
+       * 在 `calculate`（現役）、`calculateRetirementTax`（退職）、`calculatePensionTax`（年金）三處，將遍歷 `inhabitantTaxRateBrackets` 迴圈改為呼叫 `inhabitantTaxRateStrategyFactory.getStrategy(context.getYear()).calculateInhabitantTax(...)`。
+       * 移除不再使用的 `TaxBracketDto` 引用。
+       * 保留房貸抵稅對住民稅上限之抵扣邏輯（`inhabitantTaxLimitStrategy`）。
+     * 同步更新 [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：更新實例化注入，並新增跨期住民稅率動態分派（2006 舊制 5%～13% vs 2007 現行扁平 10%）之回歸驗證測試 `calculate_crossEraInhabitantTaxRate_provesStrategyPattern`。
+* **驗證與交付目標**：
+  * 全系統稅務運算（薪資扣除、基礎扣除、配偶扣除、扶養扣除、退職稅、所得稅、住民稅）**100% 達成策略化與動態化**！
+  * 核心引擎徹底解除對任何靜態級距的依賴。
+
+---
+
+#### 🟢 Phase 2.1：後端全鏈路徹底拔除 EMPLOYMENT_INCOME_DEDUCTION（消滅後端靜態技術債）✅【已完成】
+* **目標**：從後端全層次徹底拔除 `EMPLOYMENT_INCOME_DEDUCTION` 靜態級距欄位，消滅死碼與孤島依賴。
+* **異動與查核範圍（全 5 項完成驗收）**：
+  1. **Simulation Context & DTO 層（已實作拔除）**：
+     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> employmentIncomeDeductionBrackets` 欄位，建構子縮減為僅接收所得稅與住民稅級距。
+     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `EMPLOYMENT_INCOME_DEDUCTION` 級距之 Stream filter、mapping 與傳遞組裝邏輯。
+  2. **Command / Result 層（已完成查核與驗證）**：
+     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：經審查確認三者皆採用多型通用列表 `List<TaxBracketCommand/Result> brackets`，無硬編碼之薪資扣除專屬欄位；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷，不需亦無法刪除獨立屬性。
+  3. **Request / Response / Controller / Mapper 層（已完成查核與驗證）**：
+     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：經審查確認 API 傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `EMPLOYMENT_INCOME_DEDUCTION` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
+  4. **Domain / Properties 層（已實作拔除）**：
+     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `EMPLOYMENT_INCOME_DEDUCTION` 列舉值，全域僅保留 `INCOME_TAX_RATE` 與 `INHABITANT_TAX_RATE`。
+     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0..4]` 靜態配置。
+  5. **測試修復與回歸驗證（已實作校準）**：
+     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準 16 處 `new TaxConfigDto(...)`，移除無效的薪資扣除引數。
+     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並改為驗證所得稅與住民稅級距，拔除薪資扣除級距斷言。
+     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新 `@TestPropertySource` 與斷言，切換為測試 `INCOME_TAX_RATE` 屬性綁定。
+     * [`TaxRuleConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java)：確認未包含薪資扣除硬編碼，維持無破壞相容。
+* **交付成果**：後端程式碼庫徹底純化，零殘留任何無效的靜態薪資扣除欄位與孤島依賴。
+
+---
+
+#### 🟢 Phase 2.2：後端全鏈路徹底拔除 INCOME_TAX_RATE（全面刪除所得稅率靜態技術債）✅【已完成】
+* **目標**：從後端全層次徹底拔除 `INCOME_TAX_RATE` 靜態級距欄位，消滅所得稅率死碼與孤島依賴，回歸法定策略模式接管。
+* **異動與查核範圍（比照 Phase 2 規格，全 5 項查核）**：
+  1. **Simulation Context & DTO 層（已實作拔除）**：
+     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> incomeTaxRateBrackets` 欄位，建構子縮減為僅接收住民稅級距（`List<TaxBracketDto> inhabitantTaxRateBrackets`）。
+     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `INCOME_TAX_RATE` 級距之 Stream filter、mapping 與傳遞組裝邏輯。
+  2. **Command / Result 層（已完成查核與型別安全確認）**：
+     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：採用多型通用列表 `List<TaxBracketCommand/Result> brackets`，無硬編碼之所得稅專屬欄位；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷。
+  3. **Request / Response / Controller / Mapper 層（已完成查核與 API 邊界防禦）**：
+     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `INCOME_TAX_RATE` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
+  4. **Domain / Properties 層（已實作拔除）**：
+     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `INCOME_TAX_RATE` 列舉值，全域僅保留 `INHABITANT_TAX_RATE`。
+     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0..6]` 靜態配置。
+  5. **測試修復與回歸驗證（已實作校準）**：
+     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準所有 17 處 `new TaxConfigDto(...)`，縮減為僅傳入住民稅級距。
+     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並改為純粹驗證住民稅級距，拔除所得稅級距斷言。
+     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新 `@TestPropertySource` 與斷言，切換為測試 `INHABITANT_TAX_RATE` 屬性綁定。
+* **交付成果**：後端全面清除所得稅率靜態技術債，全系統稅制級距僅保留真正的非中央動態稅項——住民稅率。
+
+---
+
+#### 🟢 Phase 2.3：後端全鏈路徹底拔除 INHABITANT_TAX_RATE（全面刪除住民稅率靜態技術債與設定值）✅【已完成】
+* **目標**：從後端全層次徹底拔除 `INHABITANT_TAX_RATE` 靜態級距設定值與欄位，消滅最後一項硬編碼稅率技術債，全系統住民稅計算 100% 由法定策略模式（[`InhabitantTaxRateStrategyFactory`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyFactory.java)）接管。
+* **異動與查核範圍（比照 Phase 2 規格，全 5 項查核）**：
+  1. **Simulation Context & DTO 層（已實作拔除）**：
+     * [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)：徹底刪除 `List<TaxBracketDto> inhabitantTaxRateBrackets` 欄位（`TaxConfigDto` 成為純空物件 DTO，完成級距技術債歸零）。
+     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除針對 `INHABITANT_TAX_RATE` 級距之 Stream filter、mapping 與傳遞組裝邏輯，徹底解除模擬 Context 對級距設定之載入依賴。
+  2. **Command / Result 層（已完成查核與型別安全確認）**：
+     * [`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)：採用多型通用列表 `List<TaxBracketCommand/Result> brackets`；隨著底層 `TaxBracketType` 列舉常數拔除，無效類別已被型別系統徹底阻斷。
+  3. **Request / Response / Controller / Mapper 層（已完成查核與 API 邊界防禦）**：
+     * [`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)：傳輸層皆使用通用結構 `List<TaxBracketRequest/Response> brackets`。在 `TaxBracketType` 移除後，任何傳入 `INHABITANT_TAX_RATE` 之 JSON 請求將在 Jackson 反序列化層自動被拒絕，確保 API 邊界的型別安全。
+  4. **Domain / Properties 層（已實作拔除）**：
+     * [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)：移除 `INHABITANT_TAX_RATE` 列舉值（全系統所有靜態稅制級距列舉徹底清空）。
+     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.brackets.INHABITANT_TAX_RATE[0]` 靜態配置（消滅 `max-salary=Long.MAX_VALUE`, `multiplier=0.10`, `offset=0` 虛擬級距技術債）。
+  5. **測試修復與回歸驗證（已實作校準）**：
+     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：全面校準所有 18 處 `new TaxConfigDto(...)`，無參數化為 `new TaxConfigDto()`，並拔除已廢棄之 `TaxBracketDto` 引用。
+     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：更新測試資料並拔除住民稅級距斷言，驗證純化後的 Context 建立流程。
+     * [`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)：更新測試屬性並斷言驗證 brackets map 為空。
+* **交付成果**：後端徹底消滅所有靜態級距設定值（薪資所得扣除、所得稅率、住民稅率全部歸零），全系統稅務完全邁入法定策略模式接管，達成 100% 零靜態級距技術債。
+
+---
+
+#### 🟢 Phase 3：後端全鏈路徹底除役 TaxRuleConfig（全面消滅無效設定表與死碼鏈路）✅【已完成】
+* **目標**：鑑於全系統稅務運算（薪資所得扣除、基礎控除、配偶/扶養門檻、所得稅率、住民稅率）已 100% 由法定策略模式動態接管，`TaxRuleConfig` 及其關聯之 `tax_rule_configs` 與 `tax_brackets` 資料表已成無業務價值的空殼。後端將全鏈路徹底除役並刪除 `TaxRuleConfig` 相關之 Entity、Repository、Service、Controller、DTO、Properties 與測試，使架構達到極致純化。
+* **異動與查核範圍（全 6 大層次規劃）**：
+  1. **Domain 層（Entity 與 Enum 徹底除役）**：
+     * 刪除 [`TaxRuleConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxRuleConfig.java)（實體與對應之 `tax_rule_configs` 表格）。
+     * 刪除 [`TaxBracket.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracket.java)（實體與對應之 `tax_brackets` 表格）。
+     * 刪除 [`TaxBracketType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java)（空列舉類別）。
+  2. **Repository & Properties 層（存取與外部屬性徹底除役）**：
+     * 刪除 [`TaxRuleConfigRepository.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/config/TaxRuleConfigRepository.java)。
+     * 刪除 [`TaxRuleDefaultProperties.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultProperties.java) 與 [`TaxRuleConfigFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleConfigFactory.java)。
+     * [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：徹底移除 `finance-hub.tax-rule.name` 與 `finance-hub.tax-rule.enabled` 項目。
+  3. **Service & Command / Result 層（業務服務與傳輸物件徹底除役）**：
+     * 刪除 [`TaxRuleConfigService.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigService.java) 與 [`TaxRuleConfigServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImpl.java)。
+     * 刪除 Command：[`CreateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/CreateTaxRuleConfigCommand.java)、[`UpdateTaxRuleConfigCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/UpdateTaxRuleConfigCommand.java)、[`TaxBracketCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/command/TaxBracketCommand.java)。
+     * 刪除 Result：[`TaxRuleConfigResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxRuleConfigResult.java)、[`TaxBracketResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/TaxBracketResult.java)、[`AllTaxRuleConfigsData.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/tax/result/AllTaxRuleConfigsData.java)。
+  4. **Simulation Context & Core Models 解耦與純化**：
+     * [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)：移除注入之 `TaxRuleConfigService`，徹底移除 `taxConfig` 之建構與賦值。
+     * [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)：移除 `private TaxConfigDto taxConfig` 屬性。
+     * 刪除 [`TaxConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java) 與 [`TaxBracketDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java)。
+  5. **Controller / Web 層（REST API 端點徹底除役）**：
+     * 刪除 [`TaxRuleConfigController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigController.java)。
+     * 刪除 Request / Response / Mapper：[`CreateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/CreateTaxRuleConfigRequest.java)、[`UpdateTaxRuleConfigRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/UpdateTaxRuleConfigRequest.java)、[`TaxBracketRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java)、[`TaxRuleConfigResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigResponse.java)、[`TaxBracketResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketResponse.java)、[`TaxRuleConfigMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxRuleConfigMapper.java)。
+  6. **測試清理與校準**：
+     * 刪除 [`TaxRuleConfigServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/config/tax/TaxRuleConfigServiceImplTest.java)、[`TaxRuleConfigControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/TaxRuleConfigControllerTest.java)、[`TaxRuleDefaultPropertiesTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/config/tax/TaxRuleDefaultPropertiesTest.java)。
+     * [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)：拔除所有 `context.setTaxConfig(...)` 與 `TaxConfigDto` 實例化。
+     * [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)：拔除 `TaxRuleConfigService` mock 與 `taxConfig` 斷言。
+     * [`AssetSimulationEngineImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)：拔除 `taxRuleConfigService` mock 與工廠參數。
+* **交付成果**：徹底消滅無效的稅制配置子系統，拔除無業務效應的死碼與資料庫幽靈表（`tax_rule_configs` / `tax_brackets`），達成後端架構極限純化。
+
+---
+
+#### 🟢 Phase 4：前端 UI 徹底除役 TaxRuleConfig 面板（消滅前端無效頁面與死碼）✅【已完成】
+* **目標**：從 `hub-ui` 前端徹底除役整個 `TaxRuleConfigPanel`，清理廢棄的 Hook、元件、Zod Schema 與頁面整合，使前後端架構完全對齊。
+* **異動範圍（已全數完成）**：
+  1. **頁面整合清理（已實作）**：
+     * 在 [`general/page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/page.tsx) 中，移除 `TaxRuleConfigPanel` 元件引入與渲染，拔除 `useTaxRuleConfigs` Hook 調用，並將 TabsList 網格自 `grid-cols-4` 收斂為 `grid-cols-3`（System Config, Pension Rules, Transaction Sources）。
+  2. **元件除役與刪除（已實作）**：
+     * 刪除 `tax-rule-config-panel.tsx`。
+     * 刪除 `tax-bracket-editor.tsx`。
+  3. **Hook 與 Zod Schema 刪除（已實作）**：
+     * 刪除 `use-tax-rule-configs.ts`（包含 `taxRuleConfigSchema` 與 `taxBracketSchema`）。
+  4. **介面查核與全專案依賴審查（已實作）**：
+     * 全域 Grep 確認 `hub-ui` 無任何殘留 `tax-rule`、`TaxBracket`、`TaxRule` 參照或斷裂引入。
+     * 「General Config」頁面保留之 System Config、Pension Rules、Transaction Sources 設定面板與 Hooks 均具備完整獨立性，運作 100% 正常。
+* **交付成果**：前後端架構完全對齊，徹底消除無效 UI 設定與除役死碼，完成 Issue 142 終極治理。
+
+
diff --git a/finance-hub-backend/todo/pending fix/question-141.md b/finance-hub-backend/todo/pending fix/question-141.md
index aace255f..25b437d3 100644
--- a/finance-hub-backend/todo/pending fix/question-141.md
+++ b/finance-hub-backend/todo/pending fix/question-141.md
@@ -28,3 +28,110 @@
 
 ## 不建議的處理方式
 不建議在沒有查到法條依據的情況下，直接假設「69萬/74萬同樣適用」就去修改`Reiwa8SpouseDeductionStrategy`或新增第四時代策略——這會是在沒有法源佐證的狀態下動稅務計算邏輯，風險跟直接維持現狀但缺一個未查證的門檻相比，可能更高。
+
+---
+
+## 審查回饋與架構深度分析（Review & Architectural Analysis）
+
+經連線日本國稅廳（NTA）最新官方指引、e-Gov 日本法令資料庫（所得稅法、租稅特別措置法、地方稅法）以及後端現行實作程式碼，**本 Question 描述之現象與缺陷屬實（非幻覺），現行代碼確實存在實質 Bug，長線模擬漏掉了 2028 年起第四時代；同時，PM 先前堅持之法理質疑（「配偶控除絕對不是照扶養親屬走」）在稅法母法層次完全成立**。以下為詳細法理分析、現象屬實查核與正確架構方案：
+
+### 一、現象屬實查核（代碼 Bug 確實存在，長線模擬低估稅負）
+
+1. **工廠派發缺少第四時代**：
+   在 [`SpouseDeductionStrategyFactory.java:48-56, 67-75`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/SpouseDeductionStrategyFactory.java#L48-L56) 中，僅定義了三個策略時代（`legacyStrategy`、`reiwa7Strategy`、`reiwa8Strategy`）。
+   對於 2026 年以後的所有年份（無論是 2028、2035 還是 2050 年），所得稅無條件全部派發 `reiwa8Strategy`；對於 2027 年以後的所有年份，住民稅無條件全部派發 `reiwa8Strategy`。
+2. **門檻數值跨時代硬寫死**：
+   [`Reiwa8SpouseDeductionStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/Reiwa8SpouseDeductionStrategy.java) 將受薪配偶年薪階梯寫死為 136萬 / 169萬 / 207萬。
+3. **長線模擬數值計算偏差（實質低估稅負漏洞）**：
+   在 2028 年及未來的長線財務模擬中：
+   * **溢給扣除額漏洞**：配偶年薪若為 205 萬日圓，2028 年起法定合計所得為 $205\text{萬} - 70.1\text{萬} = 134.9\text{萬} > 133\text{萬}$，依法扣除額**必須歸零（0 円）**。現行代碼因沿用 207 萬上限，會誤給予 **30,000 円** 特別扣除。
+   * **老人加成超額誤判漏洞**：年滿 70 歲配偶年薪若為 135 萬日圓，2028 年起法定合計所得為 $135\text{萬} - 69\text{萬} = 66\text{萬} > 62\text{萬}$，依法已跌出一般配偶控除進入特別控除，**依法喪失 48 萬老人加成，扣除額僅剩 38 萬**。現行代碼判定 $135\text{萬} \le 136\text{萬}$，誤給予 **480,000 円** 老人配偶控除。
+   此偏差在長線模擬中會實質低估所得稅負、高估現金流，違反系統安全邊際基準。
+
+---
+
+### 二、法規法理深度剖析（為什麼配偶不照扶養走，但門檻卻同步下修？）
+
+這是一次關鍵的法理釐清，完美解答了當初 PM 與 Claude 爭論的癥結點：
+
+#### 1. 母法層次：配偶絕對不是照扶養親屬走（PM 的質疑 100% 正確）
+依據日本《所得稅法》第 2 條第 1 項第 34 號，法律在定義「扶養親族」時，明文規定：
+> **「居住者の親族（その居住者の配偶者を除く。）……のうち、合計所得金額が一定額以下の者」**
+
+* 法律母法白紙黑字將配偶排除於扶養親屬之外。
+* 配偶控除走的是所法第 83 條，扶養控除走的是所法第 84 條，兩者是完全平行的互斥法規。PM 當初反對「看到扶養親屬有特例就腦補配偶也一定照抄」在法律方法論上完全正確。
+
+#### 2. 底層通用法規：兩者同時被《所得稅法》第 28 條支配
+既然互不從屬，為什麼兩者在 2026 年都是 136 萬、在 2028 年都是 131 萬？
+* 配偶控除母法（第 83 條）與扶養親族母法（第 2 條第 34 號）的法定標準**唯一只有「合計所得金額 $\le$ 62萬」**，兩者都沒有自己專屬的薪資扣除算式。
+* 當配偶或親屬是受薪勞工時，稅法總則強制要求一律引用**《所得稅法》第 28 條第 2 項（給與所得＝薪資－給與所得控除）**。
+* 承載 74 萬最低扣除的法源是**《租稅特別措置法》第 29 條之 4**，其客體為「全體給與收入 220 萬以下之受薪者」，**此特例為通用法規，全體受薪勞工（包含受薪配偶）一體適用**。
+
+#### 3. 官方主管機關公告：國稅廳確認令和 10 年配偶門檻回跌為 131 萬
+國稅廳在官方《令和8年度税制改正（所得税の基礎控除の引上げ等関係）》中白紙黑字確認：
+> 「配偶者控除・配偶者特別控除の判定には、配偶者自身の『合計所得金額』が用いられます。給与所得控除額が変動（令和8・9年の74万円から令和10年以後の69万円へ本則復帰）することに伴い、**給与収入のみの場合の判定ラインも令和10年分以後は131万円程度へ変更される予定です。**」
+
+官方查證結果確立：**法律不存在任何將配偶扣除凍結在 74 萬的特例，令和 10 年（2028 年）起最低給與所得控除回歸 69 萬本則，受薪配偶年薪門檻必然同步回落至 131 萬！**
+
+---
+
+### 三、拍板修正方案：比照扶養扣除建立完整第四時代架構
+
+#### 1. 新增策略類別：`Reiwa10OnwardsSpouseDeductionStrategy.java`
+* 實作 `SpouseDeductionStrategy` 介面。
+* **配偶年薪階梯下修（反映 69 萬本則）**：
+  * 配偶者控除上限：`1_310_000L`（合計所得 62 萬 ＋ 69 萬）
+  * 特別控除滿額上限：`1_640_000L`（合計所得 95 萬 ＋ 69 萬）
+  * 特別控除遞減階梯與歸零上限：依令和 10 年本則速算表，最高歸零上限精確設置為 `2_016_000L`（合計所得 133 萬）。
+* **本人所得排富與極端高所得覆蓋**：
+  * 保留 Tier 1（$\le 10,950,000$）、Tier 2（$\le 11,450,000$）、Tier 3（$\le 11,950,000$）。
+  * 本人年薪超過 1,195 萬日圓（所得超過 1,000 萬）一律判定 Tier 4 歸零。
+  * **壓力測試保證**：核心使用者若為**年薪 2,000 萬 ～ 3,500 萬日圓高收入族群**，扣除額穩固為 0，絕不溢扣。
+
+#### 2. 工廠派發時程精確對齊（嚴格遵守課稅時差）：`SpouseDeductionStrategyFactory.java`
+比照 `DependentDeductionStrategyFactory.java`，建立四個時代之精準派發：
+
+```java
+// 1. 所得稅（現年所得課稅）：2028 年起切換第四時代
+public SpouseDeductionStrategy getIncomeTaxStrategy(int year) {
+    if (year <= 2024) {
+        return legacyStrategy;
+    } else if (year == 2025) {
+        return reiwa7Strategy;
+    } else if (year <= 2027) {
+        return reiwa8Strategy; // 2026~2027 令和8~9年特例 (136萬)
+    } else {
+        return reiwa10OnwardsStrategy; // >= 2028 令和10年起本則落日 (131萬)
+    }
+}
+
+// 2. 住民稅（前年所得課稅）：2029 年起切換第四時代
+public SpouseDeductionStrategy getInhabitantTaxStrategy(int year) {
+    if (year <= 2025) {
+        return legacyStrategy;
+    } else if (year == 2026) {
+        return reiwa7Strategy;
+    } else if (year <= 2028) {
+        return reiwa8Strategy; // 2027~2028 課徵 2026~2027 特例所得 (136萬)
+    } else {
+        return reiwa10OnwardsStrategy; // >= 2029 課徵 2028 起本則所得 (131萬)
+    }
+}
+```
+
+---
+
+### 四、實作計畫與驗證規範（供 PM / Scrum Master 決策）
+
+#### 1. 預計異動檔案清單
+1. **新建策略**：`finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/Reiwa10OnwardsSpouseDeductionStrategy.java`
+2. **工廠修正**：`finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/SpouseDeductionStrategyFactory.java`
+3. **單元測試**：`finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/SpouseDeductionStrategyTest.java`
+4. **文件轉檔**：待實作與測試通過後，轉檔至 `todo/fixed/Fixed-141.md`。
+
+#### 2. 單元測試驗證矩陣
+* **2026～2027 年特例期驗證**：所得稅派發 Reiwa 8（門檻 136萬/169萬/207萬）。
+* **2028 年所得稅本則切換驗證**：配偶年薪 131 萬享全額配偶控除；135 萬落入特別控除（老人加成歸零）；205 萬特別控除歸零。
+* **2028 年住民稅前年時差驗證**：2028 年住民稅課徵 2027 年特例所得，依然派發 Reiwa 8（門檻維持 136 萬）。
+* **2029 年住民稅本則切換驗證**：2029 年住民稅課徵 2028 年所得，切換為 Reiwa 10（門檻回跌至 131 萬）。
+* **高收入族群極端邊界測試**：本人年薪 2,000 萬與 3,500 萬，配偶扣除額 100% 歸零。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                           10019  13.82G   6.59    20:57:57  