# Epic: Phase 2 Task 3 - 房貸升息壓力測試與最佳化引擎 (Mortgage Stress Test & Optimizer)

**核心情境與目標**：
日本購屋族面臨的核心難題：「**如果未來幾年升息了，我該不該把手上的現金砸進去提前還款？但如果提早還，本金變少，是不是就吃不到完整的『住宅ローン控除 (房貸減稅)』，這樣到底划不划算？**」

本 Epic 旨在透過擴充「模擬引擎 (Simulation Engine)」的 Override 機制，在**完全不更動真實房貸資料結構 (Source of Truth)** 的前提下，於平行時空（模擬器）中推演未來的升息劇本，並利用 AI 最佳化演算法找出能讓整體資產最大化的提前還款策略。

> **架構原則 (解耦與安全)**：
> 1. **真實世界不變**：資料庫的 `MortgageRecord`、前端房貸頁面與後端 CRUD 完全不動，維持現狀（單一利率紀錄）。
> 2. **模擬器專屬擴充**：所有的「升息劇本」與「提前還款策略」皆封裝在 `SimulationOverrides` 中，作為 Request Payload 傳給 `/simulation/asset-simulation/{targetAge}`。
> 3. **底層模型當作 Fitness Function**：最佳化引擎 (3-1) 將模擬引擎 (3-2) 視為黑箱，只負責丟入參數並取得最終資產分數，兩者互相獨立，不會互相干擾。

---

> **核心問題**：若有 $N$ 筆房貸，每筆有 $Y$ 年的可選年份，加上 NISA 與年金等變數，暴力窮舉搜尋空間將高達 $O(Y^N)$，會導致 Web API 嚴重 Timeout。 
> **解法**：導入雙軌制最佳化架構，充分利用既有模擬引擎作為評估函數（Fitness Function）。

## 3-1 最佳化搜尋引擎 (Genetic Algorithm / Grid Search)

**概念**：
財務決策往往高度耦合（例如：現金拿去提前還房貸 vs. 投入 NISA vs. 年金延後領取）。我們需要一個強大的全局最佳化引擎，在避免「維度詛咒 (Curse of Dimensionality)」的前提下，找出能讓長期資產（例如 85 歲時淨值）最大化的組合。
目前只是對所有房貸做單維度 Grid Search，且是以單一固定利率假設運作。這個 Task 整合三件高度相關的事：**房貸利率模型化（精確模擬基礎）**、**多筆房貸獨立求解（M3 的複雜度挑戰）**、**雙軌制演算法**。

**功能與實作範圍**：
1. **擴充 SimulationOverrides**：
   - 增加 `MortgageRepaymentStrategy` 陣列，定義 AI 可以操作的基因（例如：年份、次數上限、單次金額上限）。
2. **自訂最佳化搜尋空間 (Checkbox UI)**：
   - 在前端的 `simulation-override-panel.tsx` 提供選項：
     - 在前端的 Optimizer Tab 提供選項，讓使用者決定要把哪些參數交給 AI 最佳化（例如：`[] 房貸提前還款年份 (可支援拆分多次)`、`[] NISA 逆向求解`、`[] 年金提領年齡`、`[] iDeCo/DC 提領年齡`、`[] 目標退休年齡`、`[] 房貸提前還款策略 (年份、次數與單次金額上限)`）。這將控制權交還給使用者，避免系統過度優化使用者不想變動的情感參數。
     - `[ ] 允許系統自動追還本金 (請輸入可動用預備金上限：___ 萬)`
     - `[ ] 完全交給系統尋找任意年份的提前還款最佳解`
3. **建立全局求解器 (GlobalOptimizerService)**：
   - **Fitness Function**：呼叫 `AssetSimulationEngine`，評分標準為：`總獲利 = (省下的利息) - (損失的住宅ローン控除) + (若拿去投資的預期獲利)`。
   - [x] **動態演算法切換 (Dynamic Strategy)**：根據變數維度，自動決定使用**網格搜尋 (Grid Search)**（少變數，1秒完成）或 **基因演算法 (Genetic Algorithm)**（多變數，數秒內逼近最佳解）。
     - 若使用者僅勾選 1~2 個變數：系統使用 **網格搜尋 (Grid Search)** 窮舉，保證找出絕對最佳解（1秒內完成）。
     - 若勾選 3 個以上變數：為避免龐大組合數（數百萬種）導致超時，後端會自動切換為 **基因演算法 (Genetic Algorithm) 或 模擬退火 (Simulated Annealing)**，透過突變與天擇在數千次迭代內迅速收斂（約 5~10 秒內得出極度逼近的最佳解）。

---

## 3-2 房貸升息情境模型化 (Mortgage Rate Scenario Modeling)

**概念**：
為了讓 3-1 算出正確的結果，底層的模擬引擎必須具備計算「升息發生時，月供改變與本金變化」的能力，並且能精準計算對「住宅ローン控除」的影響。

**功能與實作範圍**：
1. **擴充 SimulationOverrides 支援升息劇本**：
   - 新增 `RateScenario`（例如：`[ { startYear: 5, rate: 1.5% }, { startYear: 10, rate: 2.0% } ]`）。
2. **升級 MortgageSimulationService**：
   - 攔截真實的 `MortgageRecord`，若 Payload 有傳入 `RateScenario`，則在記憶體中動態替換利率。
   - **核心數學實作**：當年度跨越升息區間，或發生提前還款時，必須根據「剩餘本金」與「剩餘期數」重新計算每月的攤還金額 (Monthly Payment)。
3. **整合現有稅務引擎**：
   - 確保本金餘額的變動，能正確聯動現有的 `MortgageTaxDeductionStrategy`，精準計算出「縮水後的房貸減稅額度」。
4. **前端模擬面板 UI**：
   - 在 `hub-ui` 的 `/finance/simulator/simulation` 頁面新增區塊，讓使用者可配置壓力測試情境（如：「如果 5 年後全面升息 1%」）。

