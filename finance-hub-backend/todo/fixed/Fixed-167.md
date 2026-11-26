# [FIXED] Issue 167：NISA歷史快照檢視器瀏覽過去的對帳單日期時，顯示（而且可以編輯）的是「目前最新」的定期定額設定，不是那個日期當時真正設定的金額

## 這份issue怎麼被發現的
這是這次Source Code Review針對前端業務邏輯進行審查時發現的問題。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts:60-68`（`settingsByKey`，依`accountType_fundName`這個組合鍵，只保留每個基金最新一筆`NisaContributionSetting`——這段註解明確說明這個「只留最新」是刻意的，但只是針對設定本身這張表格而設計）
- 同檔案`:71-79`（`mergedHistory`，把`settingsByKey`裡的「最新設定」套用到**每一筆**歷史快照紀錄上，比對條件只有`accountType_fundName`，完全沒有拿`record.recordDate`去跟設定的生效日期比對）
- `hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-table.tsx`（渲染任何一筆歷史快照時，`monthlyEquivalentAmount`這一格是用可編輯的`InlineCurrencyEditor`，綁定的是`record.contributionSettingId`，送出PATCH到`/api/finance/assets/nisa/settings/{id}/allocation`）
- `hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-snapshot-viewer.tsx`（讓使用者從下拉選單自由選擇任何一個歷史`recordDate`去檢視）

## 現狀
`mergedHistory`把「每個基金目前最新的定期定額設定」套用到**所有**歷史快照紀錄上，完全不管這筆快照紀錄本身的日期是哪一天。使用者透過快照檢視器選擇任何一個過去的日期，表格上顯示的定期定額金額，永遠是「現在最新」的那個設定值，不是那個時間點真正設定的金額——而且這一格是可以直接編輯的，編輯動作實際上是透過`contributionSettingId`去更新**目前唯一那筆最新設定**，不是針對這個特定的歷史快照。

## 為何是問題
使用者一月份把つみたて每月定期定額金額從1萬円調高到3萬3333円(填滿年度上限)，九月份又調整過一次設定。現在打開快照檢視器選擇一月份的快照——表格上顯示的卻是最新（九月設定後）的金額，不是一月份當時真正設定的1萬円，看起來好像一月份的歷史資料本身就記載著後來才設定的數字。更嚴重的是：如果使用者誤以為自己正在編輯「一月份的歷史設定」而去修改這一格，實際上PATCH送出去改動的是**現在唯一生效的那筆設定**（也會連動影響NISA投影跟模擬器之後的計算），不是任何歷史紀錄本身——畫面上看起來像是在改歷史資料，但實際動到的是現在跟未來都在用的那筆設定，而且畫面上完全沒有任何提示告知使用者這一點。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：依快照自己的日期去比對當時真正生效的設定**
- 做法：改成找「生效日期 ≤ 這筆快照的`recordDate`」裡最新的那一筆設定，而不是不分日期一律套用最新設定；只有真正最新那個日期的快照才保留可編輯。
- 優點：讓每一筆歷史快照顯示的都是當時真正的設定值，符合「歷史紀錄」這個名稱本身的期待。
- 缺點：需要額外處理設定本身的「生效日期」概念，目前的資料結構是否已經足夠支援需要先確認。

**方向B：維持「永遠顯示最新設定」的做法，但只讓最新日期那一列可以編輯，其他列改成只顯示、不可編輯，並明確標示「目前設定」而不是暗示這是歷史值**
- 做法：如果「每一列都顯示現在的設定值」其實是刻意的語意(例如：這一欄代表「目前的標準提繳指示」，不是「這天歷史上的提繳金額」)，至少把其他列改成不可編輯，並調整文字說明清楚標示。
- 優點：改動範圍比方向A小，不需要新增生效日期的概念。
- 缺點：沒有解決「使用者想知道某個歷史時間點真正設定是多少」這個需求，只是避免誤編輯。

**方向C：維持現狀，但針對非最新日期的格子加上視覺提示**
- 做法：不改變合併邏輯跟是否可編輯，純粹在非最新日期的列上加上提示文字（例如「這是目前設定，非當時歷史值」），降低誤解風險。
- 優點：改動最小。
- 缺點：使用者仍然可以在誤解的情況下編輯到不該動的現在/未來生效設定。

---

## 審查回覆與 PM 裁定（回絕方向 A、B、C，正式拍板方向 D）

經 PM 與開發團隊深入檢討本 issue 之本質，確認本問題的核心在於**「未來的投資提繳計畫（Contribution Settings）」**與**「過去的資產庫存事實（Historical Snapshots）」**被錯誤地揉雜在同一個表格之中。針對原先提出的三個修正方向，全數回絕，理由如下：

### 1. 原提案回絕理由（Rejection Rationale for Options A, B, C）

* **回絕方向 A（依快照日期比對生效設定）**：
  * **回絕理由**：
    1. **本末倒置**：使用者開啟歷史快照（時光機）的核心目的是檢視「當時的資產事實（持有口數、取得成本、評價額、損益）」，過去某個月設定扣款多少並非快照表格的核心責任。
    2. **工程複雜度過高且缺乏資料模型支撐**：目前後端資料庫僅儲存每次快照與設定的 `recordDate`，並不存在完整區間的「生效起訖日（Effective Period）」概念，硬在前端或後端推算時段只會引入脆弱的推論邏輯。
    3. **未解決混雜問題**：即便比對出歷史設定，它依然把「未來設定」與「歷史資產」混在同一張表，且「只有最新日期可編輯」的特殊邊界在操作直覺上極不一致。

* **回絕方向 B（維持全表顯示最新，僅最新列可編輯）**：
  * **回絕理由**：
    1. **語意破裂與時空錯亂未解**：使用者切換到 2024 年的歷史月份時，表格中依然赤裸裸地印著 2026 年當前的最新定額扣款數字，即便反灰不可編輯，依然在畫面中傳遞了錯誤的時空資訊。
    2. **治標不治本**：僅僅是把輸入框反灰防誤觸，沒有從根本上釐清「歷史時光機」與「未來設定」的職責邊界。
    3. **憑空虛構的幻覺偽需求（嚴厲駁斥第 29 行）**：Claude 在方向 B 的缺點中振振有詞寫道 *「沒有解決『使用者想知道某個歷史時間點真正設定是多少』這個需求」*。**真實使用者（PM 本人）正式駁斥：本系統目前唯一且核心的使用者，從來沒有、也絕對不會想要知道過去某個歷史時間點到底設定了多少定期定額！** 在歷史對帳單裡，真正在意且具有客觀意義的只有「當時買進了多少口數」與「當下帳戶市值是多少」；過去扣了多少早就沉澱為在庫資產，根本沒有任何回顧歷史設定值的業務價值。Claude 到底憑什麼替使用者通靈、憑空腦補出這個根本不存在的幽靈需求？此處純屬典型的 LLM 自嗨過度腦補。

* **回絕方向 C（維持現狀僅加視覺提示）**：
  * **回絕理由**：
    1. **掩耳盜鈴**：使用者依然可以透過可點擊的編輯框去觸發 PATCH 請求，修改到連動未來 30 年長期模擬的唯一全域設定。
    2. **完全無法消除操作風險**：僅靠小字標籤無法防止使用者的誤操作與心理混淆。

---

### 2. 拍板定案：方向 D（UI Session 徹底分區與職責解耦）

**核心理念**：從產品語意與 UI 佈局上，將**「管理未來的定期定額投資計畫」**與**「檢視過去的投資組合快照時光機」**徹底拆分為兩個獨立的 Session（卡片區塊），並以明確的視覺分隔線（`<Separator />`）進行區隔。

#### 介面設計規範

```
┌─────────────────────────────────────────────────────────────┐
│ 📌 Session 1: 當前定期定額投資計畫 (Active Contribution Plan)  │
│ 說明：管理目前生效的每月定期定額提繳金額，作為未來 30 年資產投影之依據   │
├─────────────────────────────────────────────────────────────┤
│ 基金名稱          │ 帳戶類別   │ 扣款頻率 │ 每月提繳金額 (可編輯 ✎)│
│ eMAXIS Slim S&P500│ つみたて   │ 每月     │ [ ¥33,333 ]        │
│ eMAXIS Slim 全世界│ 成長投資枠 │ 每日     │ [ ¥50,000 ]        │
└─────────────────────────────────────────────────────────────┘

══════════════════════ [ 視覺分隔線 Separator ] ═════════════════════

┌─────────────────────────────────────────────────────────────┐
│ 🕒 Session 2: 歷史投資組合快照 (Portfolio Snapshots)          │
│ 選擇快照日期：[ 2024-01-15 ▾ ]                               │
├─────────────────────────────────────────────────────────────┤
│ 基金名稱 │ 保有效數 │ 取得成本 │ 當日市價 │ 評價額 │ 預期報酬率(可調)│
│ (此處為純粹的歷史庫存時光機，徹底移除定期定額編輯框，絕不誤動未來設定)   │
└─────────────────────────────────────────────────────────────┘
```

#### 具體工程異動範圍（`hub-ui`）
1. **新建 Session 1 組件**：`hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx`
   - 以 `<Card>` 包裹，專門展示當前所有生效中的 `NisaContributionSetting`。
   - 包含基金名稱、帳戶類別（つみたて / 成長）、扣款頻率（每日 / 每月）、以及每月換算金額。
   - **將原本歷史表格中的 `InlineCurrencyEditor` 遷移至此處**，專門在此更新未來提繳計畫（`PATCH /api/finance/assets/nisa/settings/{id}/allocation`）。
2. **淨化 Session 2 歷史快照表格**：`hub-ui/src/app/finance/portfolios/nisa/components/nisa-table.tsx`
   - **徹底移除 `Monthly Contrib.` 欄位**，不再掛載任何定期定額編輯框。
   - 保留：帳戶類別、基金名稱（含色彩點）、保有口數、取得單價、當日基準價額、本金、評價額、評價損益，以及該標的專屬的 `ExpectedReturnRateEditor`。
   - 確保快照表格 100% 回歸「客觀歷史事實回顧」，徹底杜絕時空錯亂與誤改未來設定之風險。
3. **頁面重構**：`hub-ui/src/app/finance/portfolios/nisa/page.tsx`
   - 依序排列：
     - Session 1：`NisaContributionSettings`（當前定額投資計畫）
     - `<Separator />`（清晰視覺分隔線）
     - Session 2：`NisaSnapshotViewer`（歷史快照檢視器）
     - Session 3：圖表區（30 年資產投影圖與歷史走勢圖）

---

### 3. 實作完成記錄（待 Review / 待驗證）

已依照上述方向 D 完成前端 UI 改造實作：

#### 異動檔案清單
1. **Hook 擴充與 Endpoint 封裝**：[`hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts)
   - 導出去重且排序之 `activeContributionSettings`，專供 Session 1 當前定期定額卡片消費。
   - **Endpoint 統一封裝**：將原本散落在 UI 元件中的 PATCH 操作收攏至 hook 內，封裝並導出 `updateMonthlyContribution`（定額提繳金額修改）與 `updateReturnRate`（預期報酬率修改），落實職責分離與一致的錯誤/Toast 處理。
2. **新建 Session 1 組件**：[`hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)
   - 建立獨立的「定期定額投資計畫 (Active Contribution Plan)」卡片，顯示基金名稱、帳戶類別、扣款頻率、設定金額與每月換算金額。
   - 直接調用 `useNisaData().updateMonthlyContribution`，集中管理當前定期定額提繳金額。
3. **淨化 Session 2 歷史快照**：[`hub-ui/src/app/finance/portfolios/nisa/components/nisa-table.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-table.tsx)
   - 徹底移除 `Monthly Contrib.` 欄位與編輯邏輯，回歸純粹的「客觀歷史事實時光機」（保留在手口數、取得單價、當日基準價額、本金、評價額、損益、預期年化報酬率）。
   - 預期報酬率編輯改為調用 `useNisaData().updateReturnRate`，移除所有組件內的裸 fetch。
4. **主頁面組合**：[`hub-ui/src/app/finance/portfolios/nisa/page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/page.tsx)
   - 依序渲染 Session 1（當前定期定額投資計畫） $\to$ `<Separator />`（清晰分隔線） $\to$ Session 2（歷史投資組合快照） $\to$ Session 3（圖表區）。
