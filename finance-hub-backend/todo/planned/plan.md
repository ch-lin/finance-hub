# Financial Planning (FP) & AI Categorization Roadmap


## 🎯 Epic 1 - Tax Management & Reporting

**Epic Scope**:
處理複雜的稅務計算，包含現役時代的所得稅與住民稅推算、ESPP/RSU 確定申告與未來提領年金 (DC/iDeCo) 的稅務折損。同時也包含一般課稅投資帳戶的稅務影響。

---

### Task 1: 一般課稅投資帳戶 (Taxable Brokerage Account / 特定口座)

**概念**：目前的模擬中，當 NISA (1,800萬) 滿額後，所有剩餘的存款皆以 0% 報酬率停留在現金 (Liquid Cash) 中，導致長期試算過於保守。

**功能**：
1. 引入「一般課稅帳戶」的資產池。
2. 模擬引擎在每年的資金分配時，若 NISA 額度已滿，自動將剩餘的淨存儲 (Net Savings) 投入此帳戶。
3. 該帳戶享有與 NISA 相同的預期報酬率，但在提領變現時，針對獲利部分 (Capital Gains) 扣除 20.315% 的稅金。

---

### Task 2: Tax Reporting (確定申告) Integration
- **概念**：自動化申報稅務所需的資料抓取與轉換。
- **功能**：解析 `E_TRADE benefitHistory.html`，串接銀行 API 取得匯率計算 JPY FMV，供後續稅務試算。

---

### Task 3: 退職金提領最佳化器 (Retirement Withdrawal Optimizer)

- **概念**：處理退職稅「重疊期間調整 (5年/15年規則)」帶來的雙重扣除問題，推算稅後淨額最高的最優解。
- **功能**：
  - 在使用者未指定提領年齡時，於背景執行多個時間線（同年合併領取、間隔5年、間隔15年）。
  - 自動比較各種提領情境下的**稅後總淨現金 (Net Liquid Cash)**。
  - 將最佳提領策略（包含何時提領 iDeCo 與 企業DC）推薦給使用者。
- **備註**：引擎實作採進階功能模式（使用者按下 "Optimize Retirement" 按鈕才執行），避免主引擎運算量暴增三倍。

> ⚠️ **開發警告 (Ref: [FINDING-37])**
> 目前 `TaxAdvantagedAccountProjector` 中，因為提領年齡被強制綁定 (`idecoUnlocked = dcUnlocked`)，所以計算稅金時一定會走入 `dcUnlocked && idecoUnlocked` 的安全合算分支。但在該段邏輯下方的 `else` 分支（分別計算各別帳戶稅金）目前是一段 **會造成退職所得控除被重複計算 (Double-dip)** 的死碼。
> 未來在實作此 Task（解開提領年齡的硬綁定，支援以下不同時間點提領）時，**必須**優先重構或移除該段危險的 `else` 邏輯，並完整實作跨年度的「退職稅重疊期間扣除」機制，否則會導致稅後淨額被嚴重高估！

**三種主要最佳解選項**：
如果要實作日本退職稅的 5年/15年 重疊年資扣除，演算法確實會變得異常複雜（需要追蹤每一次提領的年份，以及它們各自的加入期間，然後算出交集並將重疊的扣除額歸零）。
使用者其實不想知道這些複雜的稅法，他們真正在意的是：**「在我的金額與年資條件下，我到底該選哪一種領法，最後拿到手（稅後）的錢才會最多？」**
這絕對是一個**最佳化（Optimization）**的問題，而且跟 NISA 最佳化非常類似！如果我們未來要實作這個功能，可以這樣設計：
1. **選項 A（同年領取）**：60 歲時同時領取 iDeCo 與 企業DC，合併計稅。適合兩個帳戶金額不極端巨大的人。
2. **選項 B（5年冷卻 — 先 iDeCo 後 DC）**：60 歲領 iDeCo，65 歲領企業 DC，各自單獨計稅，無重疊扣除。適合 iDeCo 金額不大但企業 DC 極龐大的人。
3. **選項 C（15年冷卻 — 先 DC 後 iDeCo）**：60 歲領企業 DC，75 歲領 iDeCo，適合想把 iDeCo 留作超長期養老金的人。

---

## 🎯 Epic 2 - Historical Actuals vs Future Budgeting (Insights & Suggestions)

> **為什麼是 Epic 2？**  
> 這個功能對「要不要買房」的決策沒有直接幫助，但對長期的財務規劃品質非常有價值。需要在 Phase 2-3 的模擬引擎穩定後再疊加此層洞察。

**Epic Scope**:
將系統架構明確劃分為「過去的實際花費 (Historical Actuals)」與「未來的預算規劃 (Future Projections)」，並利用 AI 標籤結果提供預算校正建議，而非直接覆蓋未來預算。

---

### Task 1: 預算與實際花費對比洞察 (Budget vs. Actuals Insights)
- **概念**：為避免自動匯入歷史資料(如 NotebookLM 標籤) 直接干擾或覆蓋使用者精心規劃的未來預算 (`LifeEvent`)，系統不主動新增預算事件。
- **功能**：
  1. 系統透過 AI 標籤聚合出過去一年的實際特定分類支出（例如 `TRAVEL` 總額 60 萬）。
  2. 系統抓取使用者在該年度手動設定的相關 `LifeEvent` 預算（例如 40 萬）。
  3. 在 Dashboard 提供 **Insights (洞察建議)** 卡片：「您去年的旅遊花費超出預定預算 20 萬，是否需要系統幫您調升未來的旅遊預算以確保破產模擬更準確？」
  4. 使用者點擊同意後，才正式更新 `LifeEvent`，確保對未來的規劃皆為使用者的「有意識決定」。

---

## 📋 Backlog — 低優先、有空再做

> 以下功能因為「已有 local import 機制可以手動匯入資料」而降低緊迫性，或者屬於純 UX 便利性改善，暫時移入 Backlog 不排入固定 Phase。

### B1 多筆房貸獨立還款參數 (Per-Mortgage Override) [✅ 已由 GA 引擎實作完成]

> **實作與演進狀態 (2026-10-07)**：
> - **核心已完成**：本功能的核心理念已在 GA 最佳化求解器與 `SimulationOverrides.mortgageEarlyRepayments` 實作完成。舊版單一純量 `mortgageEarlyRepaymentYear` 已在 Fixed-44 廢除，改為每筆房貸獨立之 `Map<Long, List<MortgageEarlyRepaymentOverride>>`；GA 求解器（`Chromosome.mortgageRepayments`）已全面支援多筆房貸各自獨立演化尋優，並於 Fixed-124 延伸支援「縮短年限」與「減少月付金額」雙策略。
> - **Grid Search 延伸**：原構想第 4、5 點提及的 Grid Search 局部搜尋，因多維組合數指數爆炸風險，已轉由 GA 基因演算法全域求解替代；Grid Search 引擎之多房貸維度延伸評估則獨立收錄於 [Plan 04](Plan-04-grid-search-multi-mortgage-dimension.md) 供未來參考。

- **概念**：目前 `SimulationOverrides.mortgageEarlyRepaymentYear` 是一個全域變數，強制將所有房貸視為單一資金池。若使用者同時持有主宅房貸 (低利) 與投資物件房貸 (高利)，系統無法支援「僅提前清償高利貸款」的決策。
- **功能**：
  1. 將全域的 `SimulationOverrides.mortgageEarlyRepaymentYear: Integer` 擴充為 `mortgageEarlyRepaymentYears: Map<String, Integer>`（以 `mortgageId` 為鍵）。
  2. 升級最佳化求解器 (Solver)，使其能夠針對不同貸款進行逐筆搜尋或提供策略建議，回答「該不該優先清償某筆高利率貸款」的問題。
  3. 讓 Solver 能針對單一特定房貸（例如高利率的投資物件）做獨立的還款最佳化，而不影響其他貸款。
  4. **降維與排序**：根據「有效利率」（扣除減稅等因素）對負債進行排序。
  5. **局部搜尋 (Local Search)**：優先對最高利率的房貸進行單維度搜尋（跑 40-50 次模擬）找出局部最佳年份並固定，再依序處理下一筆。時間複雜度降至 $O(N \times Y)$，能以極低的運算成本逼近 99.9% 的全域最佳解，適合 UI 點擊「快速優化」時在 1~2 秒內即時回傳。

### B2: 「Add Record」與手動表單功能補齊
- **說明**：針對 Income & Expenses（Expenses, Salaries）和 Portfolios（Pensions, DC/iDeCo, NISA, Company Stock）實作 `POST` API 與對應的 Shadcn UI Dialog。
- **暫緩理由**：現有資料可透過 import 機制或直接操作資料庫補入，不阻擋 Phase 2 的模擬跑通。

### B3: 「Upload CSV」單檔手動上傳功能
- **說明**：在 Bank 和 Credit Card 的 Accounts 頁面新增 Upload CSV 按鈕，後端接收後直接呼叫 Parser 解析。
- **功能**：前端提供檔案上傳按鈕，後端接收檔案後直接呼叫對應的 Parser 進行解析與匯入。
- **暫緩理由**：已有 local import 掃資料夾機制可替代。

### B4: Finance Extractor Chrome Extension 擴充
- **說明**：實作 Salary (Payroll), SBI NISA, iDeCo/DC, Pension, E-Trade 等 HTML 抽取並打 API 傳送給後端。
- **暫緩理由**：現有 Extension 已支援主要資料來源，擴充屬於長期工程投資，Phase 1 完成後再決定方向。

### B5: iDeCo/DC 法定月繳上限防呆 (Statutory Contribution Limits)
- **概念**：iDeCo 與 DC 在日本有嚴格的法定月繳上限（依職業別如自營業者 68,000 日圓、公務員等）。目前引擎無上限防呆，可能導致過度樂觀的免稅成長與稅務扣除。
- **說明**：在 `UserProfile` 擴充 `employmentType`（職業型態），模擬引擎根據職業別動態套用法定上限（最高 68,000 日圓），防止 Optimizer 自動搜尋到不合規的超額免稅結果。
- **功能**：
  1. 在 `UserProfile` 擴充 `employmentType`（職業型態）屬性，並更新相關 UI。
  2. 模擬引擎根據職業別，在投入資金時動態套用對應的法定上限（最高 68,000 日圓），防止 solver 或手動輸入造成的免稅額度溢位。
- **暫緩理由**：使用者手動設定實際投入金額時，自身知道法規上限不會超額輸入，所以直到 Global Optimizer（Task 3-4）要自動搜尋 iDeCo 投入額作為決策維度之前，這個防呆的優先度都不高。

### B6: 商品歷史價格與相關紀錄追蹤 (Item History Tracking)
- **概念**：在單筆明細的 UI 右側加入「相關紀錄」按鈕，讓使用者可以追蹤同一項商品在過去不同時間點的價格浮動。
- **功能**：
  1. 後端 `ExpenseItem` 與 `ExpenseDto` 新增 `itemId` 欄位（例如 Amazon ASIN）。
  2. 前端實作相關紀錄面板，點擊後透過 `itemId` 向後端查詢該商品所有的歷史購買紀錄。

### B7: DC/iDeCo 解鎖年齡預設行為提示 (Withdrawal Age Default Confirmation)
- **概念**：來自 issue-148 的重新檢討——`taxAdvantagedWithdrawalAge` 若使用者從未設定過，模擬引擎目前的隱性預設是「法定最低解鎖年齡（60歲）」，一旦使用者模擬起始年齡已經 ≥ 60 歲，模擬第一年就會立刻觸發強制解鎖、整筆課稅領出。這個因果關係目前完全沒有在畫面上被講清楚，使用者可能在不知情的情況下，就已經做了這個影響重大的決定。
- **功能**：
  1. 在個人資料/入職設定畫面，偵測使用者目前年齡是否已 ≥ 60 歲、且 `taxAdvantagedWithdrawalAge` 從未填寫過。
  2. 若符合，提示使用者確認「這個年齡要不要現在就當作 DC/iDeCo 解鎖領出的時間點」，讓這個目前隱藏的預設決定變成使用者有意識的選擇。
- **暫緩理由**：使用者本人目前距離 60 歲還有 20 多年，這個情境短期內不會被實際觸發，不需要排入任何 Plan，先記錄方向即可，慢慢來。

### B8: 扶養控除「同居老親等」加算應排除非直系尊属之同居70歲以上親屬 (Elderly Cohabiting Dependent — Direct Ascendant Check)
- **概念**：來自 Source Code Review（2026-10-13）——日本所得稅法上「同居老親等」這一級最高扶養扣除額（所得稅58萬円/住民稅45萬円），法定資格除了70歲以上、同居之外，還要求必須是使用者本人或配偶的「直系尊属」（父母、祖父母）。目前`AbstractDependentDeductionStrategy`只檢查`isCohabiting`這個布林值跟年齡，`Dependent`資料模型裡`memberType`只分`SPOUSE`/`DEPENDENT`兩種，完全沒有欄位可以表示「這個人是不是我的直系尊属」——只要使用者勾選「同居」、年滿70歲，系統一律套用最高級扣除額，不管這個人實際上是不是父母。
- **功能**：
  1. 在`Dependent`資料模型新增一個欄位，讓使用者可以標示這名扶養親屬是否為直系尊属。
  2. `AbstractDependentDeductionStrategy`的扣除額計算改讀這個新欄位，非直系尊属的同居70歲以上親屬只能適用一般高齡扶養的扣除額，不能套用「老親等」加碼。
- **暫緩理由**：使用者確認目前沒有同居的老親，這個落差目前不會被實際觸發，不需要排入任何 Plan，先記錄方向即可。

### B9: 復興特別所得税沒有2037年到期判斷，及2037年以後的替代稅制安排 (Reconstruction Surtax Expiry & Post-2037 Successor Regime)
- **概念**：來自 Source Code Review（2026-10-13）——`ActiveTaxCalculationEngine`裡三處計算所得稅（在職、退職、年金）都無條件把`baseIncomeTax`乘上`1.021`當作復興特別所得税附加，完全不管模擬跑到哪一年。復興特別所得税的法定課徵期間明文寫到2037年（令和19年）為止，理論上系統應該在2038年以後就不再加計這筆稅——但這套FP系統的定位是現金流模擬，不是報稅軟體，連「精確卡在2037年到期」這個層級的稽核精確度都不是這個工具該追求的；更不用說2038年以後日本政府可能會延長期限、調降稅率、或另開「防衛特別所得税」填補財源缺口，日本稅制幾乎每年都在修法調整，如果每一個可能的未來修法都要事先查證並反映進模擬邏輯，會沒完沒了、永遠追不完。
- **功能**：若未來真的要處理，兩層可以一起做：(1) 在`TaxConstants`新增課徵截止年常數，三處計算改成年度判斷，2037年以後不再加計這2.1%；(2) 若屆時已有正式立法定案的替代方案（例如防衛特別所得税的生效日期、稅率、是否與復興特別所得税並存或互相替代），再評估要不要把那個時間點之後的稅制安排也納入。
- **暫緩理由**：使用者確認FP現金流模擬工具不該追到這個層級的稅法細節，不需要排入任何 Plan，也不需要花時間查證2037年以後的替代方案，先記錄方向即可。

