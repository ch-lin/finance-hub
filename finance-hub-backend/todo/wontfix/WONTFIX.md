# WONTFIX 清單

> 本清單彙整所有經過確認、**刻意不修** 的項目（設計決策 / 法規正確 / 架構取捨），
> 供未來回顧是否要重新評估。每項附上來源檔案、判定理由與判定日期（若文件內有記載）。
> 本清單**不包含**尚待處理或仍在爭議中的項目 — 那些會出現在本輪 review 產出的 FINDING-xx-0x.md 或本次報告的判斷段落中。

---

## 1. `asset-simulation-review-round1.md` 原生 WONTFIX

### H3 — Company stock (RSU/ESPP) 永不增值
- **判定**：WONTFIX（設計決策）
- **理由**：財務規劃最佳實踐是將 RSU/ESPP 盡快變現並轉入 NISA 等稅務優惠帳戶，系統無法（也不應該）預測單一股票的未來成長率；把它模擬成 0% 成長是刻意保守的設計，避免對單一股票做無依據的成長假設。


**H3. [WONTFIX] 公司股票餘額在整個模擬週期內完全不增值（僅能被動減少）**
- **位置**：`AssetSimulationEngineImpl.java:141-321`（主迴圈）+ `LiquidationSimulationService.java:27-37`
- **問題**：`context.companyStockBalance` 初始化後，在整個模擬主迴圈中沒有任何地方對其套用投資報酬率成長；唯一會改變它的地方是 `LiquidationSimulationService` 中應付現金短缺時的**只減不增**清算路徑。
- **為何是商業邏輯考慮不周**：與 NISA/DC 不同，公司股票（RSU/ESPP）是實際會隨市場波動、公司成長而增值的資產類別，模擬引擎卻讓它在數十年投影中維持絕對常數，這在數學上等同於假設報酬率恆為 0%——沒有任何設定入口能讓使用者表達對這類資產的成長/衰退預期。
- **情境**：35 歲使用者持有 ¥10,000,000 公司股票，30 年投影到 65 歲，該項目在每一年的輸出中都精確等於 ¥10,000,000；同額度放入 NISA 以 5% 年化複利則成長至約 ¥43,000,000。持有公司股票的使用者，其總淨資產投影被系統性低估。
- **建議修正**：為公司股票新增可設定的預期報酬率（或至少提供「視為現金」vs「視為投資組合」的選項），並在主迴圈中套用複利成長。

> [!NOTE]
> **WONTFIX 理由**：實務上，理財規劃通常建議將歸屬的 RSU 或 ESPP 盡快變現並轉入 NISA 等多元化投資組合，以避免單一公司風險。因此系統不鼓勵、也不特別模擬公司股票的長期複利成長，而是將其視為類似流動現金的短期停泊資產。而且基本上你無法預測股票是否能成長，依賴單股會導致模擬預測失真造成財務風險。




### H6 — 課稅帳戶變現時未扣除資本利得稅
- **判定**：WONTFIX（設計假設）
- **理由**：系統假設使用者輸入的餘額已經是稅後淨額（net-of-tax），刻意不做成本基礎（cost basis）追蹤以避免引入過度複雜的資本利得稅計算模組。


**H6. [WONTFIX] 應稅帳戶（公司股票）清算完全未扣除資本利得稅**
- **位置**：`LiquidationSimulationService.java:27-37`（程式碼註解寫著「Assuming 0% capital gains per user preference」）；全專案 `grep` 無任何資本利得稅常數或邏輯
- **問題**：現金短缺時強制清算公司股票，模型將 100% 售股款項計入現金，但**沒有任何地方存在可設定的資本利得稅偏好**——這只是一句誤導性的程式碼註解，不是真實功能。日本股票資本利得稅實際稅率約為 20.315%（國稅+地方稅合計）。
- **為何是商業邏輯考慮不周**：NISA 帳戶本身確實免稅（這是其存在的稅務優惠意義），但公司股票（一般應稅券商/ESPP 持股）不是免稅帳戶，將兩者用同一套「0% 稅」邏輯處理，等於抹除了 NISA 相對於一般應稅帳戶最核心的稅務優勢比較基礎。
- **情境**：任何觸發清算的現金短缺年度，可用流動性都被高估約 20%；更嚴重的是，這個一致性錯誤會系統性偏袒「繼續投資應稅股票」優於「提前還款房貸」的優化器建議，因為應稅帳戶的真實稅務拖累在模型中完全消失。
- **建議修正**：導入資本利得稅常數（可先用日本現行 20.315% 作為預設，並區分帳戶類型：NISA 免稅、iDeCo 提領時另計、一般應稅帳戶按利得課稅），在 `LiquidationSimulationService` 清算應稅資產時扣除。

> [!NOTE]
> **WONTFIX 理由**：準確計算資本利得稅需要追蹤每一筆股票買入的歷史成本（Cost Basis）與未實現損益，這會讓模擬引擎與資料模型變得極度複雜。目前的系統設計方針是：假設使用者在輸入「公司股票餘額」時，提供的已經是「扣除預期稅額後的淨值」，以此來簡化清算模型。同時，課稅的部分是針對獲利的部分，無法預測股票是否獲利或賠損，就算內線交易也不可能準確保證。



### H15 — 
**H15. [FIXED] Solver 設定對話框的輸入數值完全沒有邊界檢查**
- **位置**：`solver-settings-dialog.tsx:88-122`；後端 `SolveNisaGoalRequest` 無 `@Min`/`@Max`
- **為何是商業邏輯考慮不周**：`safetyNetAmount` 可輸入負數，會讓二分搜尋的成功判定 `dto.getProjectedNetWorth() >= safetyNet` 變成恆真，導致 solver 回傳「每月只需提繳 ¥0」——這是一個對使用者而言看起來權威、實際上毫無意義的壓力測試結果。`inflationOverride`/`nisaReturnOverride` 同樣沒有上下限，輸入 1000（意圖打 1000%）或 -100（-100%）會直接餵入 `Math.pow(1.0 + inflationRate, yearsFromNow)`，在 60 年投影中可能溢位或產生荒謬結果，伺服器端無任何拒絕機制。
- **情境**：使用者手誤輸入負數安全網或誇大通膨率，得到看似專業但實則毫無參考價值的「建議每月提繳金額」，且系統不會提出任何警示。
- **建議修正**：前端加入合理範圍限制（例如通膨率 -5%~15%、報酬率 -20%~30%），後端 DTO 加上 `@Min`/`@Max` 作為第二層防護。

> **[Agent 補充註記 (Round 3 - 2026-08-29)]**
> **[WONTFIX (Deprecated)]**: 後端的 `@Min`/`@Max` 防護已如期實作並成功攔截。而前端 `nisa-goal-solver-card.tsx` 缺少 `<form>` 導致 `min`/`max` 失效的問題 (FINDING-27)，因為整個 Legacy Optimizer 模組即將被廢棄刪除 (同 FINDING-22)，因此不再針對即將刪除的前端元件投入修復成本。



### M1 — 薪資成長率與通膨脫鉤
- **判定**：WONTFIX（保守設計）
- **理由**：名目薪資成長並非保證存在，系統刻意不假設薪資會跟隨通膨成長，屬於保守預設（conservative-by-design），避免高估未來現金流。


**M1. [WONTFIX] 薪資成長率與通膨率完全脫鉤，預設值組合會人為製造「破產」假象**
- **位置**：`IncomeExpenseCalculator.java:40-44`（薪資成長）vs `63-70`（支出通膨）
- **為何是商業邏輯考慮不周**：支出每年依 `config.getInflationRate()`（預設 0.02）複利成長，但薪資成長率讀取 `profile.getSalaryGrowthRate()`，若使用者未設定該可選欄位則預設為 **0.0**——完全沒有機制將名目薪資成長與同一份通膨假設綁定。這是典型的「兩個看似獨立的可選欄位在預設值組合下產生非預期交互作用」的設計缺陷。
- **情境**：`profile.getSalaryGrowthRate()` 為 null（許多使用者不會特別設定），薪資 30 年不變，而支出以 2% 複利成長，30 年後名目支出上漲約 1.8 倍、薪資完全不變，`netSavings` 必然隨時間趨向負值——引擎會回報一位財務健康的使用者「即將破產」，這純粹是預設值組合的人工產物，不是真實財務風險。
- **建議修正**：若 `salaryGrowthRate` 未設定，預設值應與 `inflationRate` 掛鉤（而非硬編碼 0.0），或在 UI 明確要求使用者確認此欄位。

> [!NOTE]
> **WONTFIX 理由**：總體經濟的通膨是客觀事實，但個人的名目薪資成長並沒有保證（尤其是考量到日本過去數十年的薪資停滯現象）。若系統在未經使用者同意下，自動將薪資成長與通膨掛鉤，可能會給出過於樂觀的資產投影。最保守且負責的作法是：讓支出確實反映通膨壓力，以此提醒使用者若薪資不成長，實質購買力就會下降。



### L3 — Solver「Apply to main settings」按鈕缺失
- **判定**：WONTFIX / CONFIRMED_ASIS（round2.md §1.4 重新分類，非 bug）
- **理由**：Simulator 定位為純展示工具（pure-display），不應該有自動寫回主設定的行為，維持現狀即為正確設計。



**L3. [FIXED] Solver「套用至主設定」按鈕沒有實際套用任何設定**
- **位置**：`page.tsx:152-157`
- **為何是商業邏輯考慮不周**：`onGoalCalculated={(goal) => { toast.success(...) }}` 只顯示一則成功提示，沒有任何狀態更新、PATCH 請求或自動開啟設定面板的行為——這是 UI 承諾（按鈕文字「套用至主設定」）與實際行為完全不符的案例。
- **情境**：使用者跑完 Worst-Case Solver 得到建議提繳金額，點擊「套用至主設定」，若沒有仔細閱讀 toast 內容（提示需自行到設定面板手動儲存），會誤以為系統已經自動更新其 NISA 設定，但後端完全沒有任何變更。
- **建議修正**：讓按鈕真正呼叫對應的 PATCH API 更新設定，或至少自動開啟設定面板並預填計算結果，而非僅顯示文字提示。




---

## 2. `asset-simulation-review-round2.md` 原生 WONTFIX

[WONTFIX] **2.7 `calculateNisaGoal` 二分搜尋的單調性假設不成立,可能誤判可達成目標為「不可能」**
- 搜尋假設「提繳金額越高,`noBankruptcy` 越可能為真」單調遞增,但實際上提繳金額越高也會立刻加大當年現金流出、可能觸發清算/借款,讓 `noBankruptcy` 在中間某段區間反而變回 false——導致搜尋方向被第一個探測點誤導,永遠鎖死在不可行的高提繳區間搜尋,最終回傳「無法達成」,即使真實世界中存在一個較低、可負擔且足夠的答案。
- 即將棄用之功能


### 2.10 — SBI NISA 匯入排除當日 snapshot
- **判定**：WONTFIX（2026-08-08 判定，2026-08-29 Round 3 補充註記確認前提仍成立）
- **理由**：這是正確的業務邏輯（避免重複計算當日快照），並非 bug。
- **關聯**：其「no atomicity guarantee」的獨立疑慮已被追蹤為 FINDING-25，並已透過 `HtmlParsingException` fail-fast 機制修正（見本輪對 FINDING-25 的驗證）。


[WONTFIX] **2.10 NISA 起始餘額只抓「全域最新快照日期」那一批基金,較舊日期的基金被靜默排除** —— 若使用者兩支 NISA 基金的最後對帳單日期不同(例如這次只重新解析了一支的對帳單),日期較舊的那支會整個從起始淨資產、起始本金消失,沒有任何錯誤或警告。

> **[WONTFIX / False Positive] (2026-08-08)**
> 理由：根據系統目前的業務邏輯，每次匯入的「快照（Snapshot）」代表的是「該時間點整個 NISA 帳戶的完整狀態」，同一批寫入的紀錄會共用相同的 `recordDate`。因此，如果最新一批的快照中沒有出現某檔舊基金，即代表該基金已被賣掉或清空，本來就不應再計入起始餘額中。程式碼目前強制對齊最新 `recordDate` 的行為是正確且符合商業邏輯的。
> 
> **[Agent 補充註記 (Round 3 - 2026-08-29)]**
> 原本的 WONTFIX 建立在「每次匯入皆為完整快照」的假設上，但 Claude 在 FINDING-25 準確指出 `SbiNisaParserImpl` 存在靜默吞嚥 Exception 的缺陷，導致殘缺的 HTML 錯誤解析也可能被誤認為完整狀態寫入。目前已在 Task 3-11 修復此缺陷，透過 `HtmlParsingException` 實作 Fail-fast 機制，確保解析失敗時直接阻斷寫入（400 Bad Request），從而補齊並維持了原始 WONTFIX 業務邏輯的前提正確性。



### 2.13 — Optimizer 未套用 Sandbox 覆寫值
- **判定**：WONTFIX（架構決策）
- **理由**：Optimizer 與 Sandbox 覆寫在 UX 上被刻意拆分為不同分頁（tabs），避免使用者混淆「AI 最佳化結果」與「手動覆寫情境」，屬於刻意的 UX/架構切割。


[WONTFIX] **2.13 「優化建議」面板的房貸提前還款建議完全不套用使用者在 Advanced Overrides 設定的退休年齡/年金/基金覆寫** —— 主圖表用完整的 `SimulationOverrides` 呼叫模擬,旁邊的優化建議卡片卻是呼叫另一個完全不吃 overrides 參數的端點,永遠用個人檔案的預設退休年齡計算——兩個並排顯示的卡片實際上基於不同的假設情境,UI 上毫無提示。

> **[Agent 補充註記 (2026-08-08)]**
> 2.13 我們決定 **WONTFIX (不將沙盒覆寫傳入求解器)**。
> 經過團隊確認，這牽涉到「正向模擬沙盒 (Simulator)」與「逆向求解最佳化工具 (Optimizer)」在 UX 與架構定位上的本質差異。
> 求解器本來就應該基於使用者的「真實 Baseline」去進行壓力測試與求解，而非基於隨意滑動的假想沙盒。
> 為了徹底解決 UI 誤導的問題，我們決定進行 **UX 重構**：
> 1. 將 Simulator 頁面拆分為兩個獨立的 Tabs：「Sandbox (沙盒)」與「Optimizer (最佳化工具)」。
> 2. 將 Optimization Insights (房貸最佳化) 與 NISA 目標求解都移入 Optimizer Tab，明確標示其為基於真實資料的獨立運算工具。
> 3. 在 Sandbox 面板中，補上遺漏的 `mortgageEarlyRepaymentYear` 欄位，讓使用者可以「手動」在沙盒中假想並測試提前還款的結果。
> 透過架構分離，兩者的假設情境將不再混淆。



---

## 3. `claude feedback/` FINDING 檔案中確認轉為 WONTFIX

### FINDING-09 — NISA Goal Solver 錯誤單調性假設（binary search monotonicity）
- **判定**：WONTFIX（待刪除的舊版功能）
- **理由**：`SimulationOptimizerServiceOldImpl.calculateNisaGoal` 屬於舊版 Optimizer，確認即將被新版 `GlobalOptimizerService` 取代並整支刪除，不再投入資源修復。
- **同源項目**：FINDING-22（已降級為 heads-up）、FINDING-27（下同）、round2.md 的 `2.7`。

### FINDING-27 — NISA Goal Solver 前端邊界值僅為裝飾（decorative bounds）
- **判定**：WONTFIX (Deprecated)
- **理由**：整支舊版 Optimizer 前端（`nisa-goal-solver-card.tsx`）將隨舊版 Optimizer 一併刪除，前端邊界檢查修正已無意義。

### FINDING-23 — NISA 再投資僅檢查全域 18,000,000 上限，無單筆基金上限
- **判定**：WONTFIX / CONFIRMED_ASIS（確認不是 bug，是正確的法規實作）
- **理由**：日本新 NISA 制度中「生涯非課稅限度額（1,800萬）」是綁定在個人帳戶層級的全域上限，**法規上完全沒有「單一基金上限」的規定**。FINDING-23 檔案本身的 Round 3 補充註記已正確指出這點，並同時明確標註「`asset-simulation-gemini-review-round1.md` 中的 2.4 極度可能是幻覺造成的 feedback」——這與使用者本輪 Note 3 的警示完全吻合，本次獨立審查亦同意此判定。
- **備註（可選 Feature Request，非 bug）**：若未來想讓使用者能自訂「單一基金再投資上限」或「依原始比例分配再投資」，可另外開一張 Feature Request，但不屬於本次 review 範圍的缺陷。

### FINDING-25 / FINDING-25-01 — SBI NISA 匯入缺少「頁面完整渲染」的 completeness 檢查
- **判定**：WONTFIX（**暫定政策，非永久結案** — 供未來重新評估，見 [FINDING-25-02.md](../claude%20feedback/FINDING-25-02.md)）
- **理由**：原始建議方向（比對前後兩次快照的基金集合差異來偵測「疑似漏抓」）已被開發者正確駁回——無法區分「頁面未完整渲染」與「使用者真實出售/更換基金」，會對後者產生偽陽性。開發者反提的替代方案（在 client/scraper 端加入渲染等待機制）經查證後**在本專案中不適用**：全專案沒有任何 headless browser / Playwright / Puppeteer 之類的自動化擷取工具，NISA 匯入完全是使用者手動另存 HTML 檔案後放入資料夾（`HtmlArchiveUtils.findHtmlFiles`），沒有「scraper 端」可以掛渲染等待邏輯；同時全專案也沒有任何 SBI NISA HTML 測試 fixture，無法驗證「頁面固定渲染成長/つみたて區塊容器（即使該分類為空）」這項結構性假設是否成立，因此也無法在缺乏真實樣本的情況下設計一個有根據的結構完整性檢查。
- **已完成的部分**：`SbiNisaParserImpl` 對「已偵測到但格式錯誤」的資產列已正確加入 `HtmlParsingException` fail-fast（見 FINDING-25-01 的 `[FIXED / REBUTTAL]`，本輪重新驗證確認順序正確）。本 WONTFIX 僅涵蓋「完全缺漏／延遲載入的整個區塊」這一更難偵測的殘餘情境。
- **暫定方針**：維持現況，不新增阻斷性或比對性邏輯——為了涵蓋每一種可能的部分渲染情境而投入工程成本，對一個「使用者即是資料輸入/審閱者本人」的個人理財模擬工具而言不成比例。此判定僅在此情境被實際觀察到發生時才需要重新評估，屆時會有真實的殘缺 HTML 樣本可用於設計真正有依據的修正，而非憑空的推測性方案。
- **可選、非必要的低成本替代**：在每次成功匯入時以 `INFO` 等級記錄匯入的基金數量／名稱，作為人工偶爾查看 log 時的被動線索，不阻斷、不比對，成本近乎零。
- **未來自然解法（非本次決策的一部分，僅供追蹤）**：目前 E-Trade／銀行對帳單／Amazon/Sofmap/Rakuten 皆已有專屬 Chrome Extension 直接讀取已完整渲染的即時頁面並上傳解析後的 JSON，唯獨 NISA 仍停留在「使用者手動另存 HTML」的匯入方式。若未來也為 NISA 建立對應的 Extension，本項完整性問題很可能會隨之自然消失——並非因為新增了任何比對邏輯，而是因為 Extension 抓取的是已完成渲染的即時 DOM（包含延遲載入區塊），而非可能不完整的靜態 HTML 快照。
- **plan.md 對應**：`plan.md` Backlog **B4「Finance Extractor Chrome Extension 擴充」**（`plan.md:117-119`）已明確將 SBI NISA 列為未來擴充目標之一，說明文字為「實作 Salary (Payroll), SBI NISA, iDeCo/DC, Pension, E-Trade 等 HTML 抽取並打 API 傳送給後端」，目前狀態為 Deferred（暫緩，理由：「現有 Extension 已支援主要資料來源，擴充屬於長期工程投資，Phase 1 完成後再決定方向」）。即上述「未來自然解法」並非空泛推測，而是已有明確的 backlog 項目對應；交叉記錄於 [PLANNED.md](../planned/PLANNED.md)。

---

## 判定索引（快速查表）

| 來源 | 項目 | 分類 |
|---|---|---|
| round1.md | H3 | 設計決策 |
| round1.md | H6 | 設計假設 |
| round1.md | M1 | 保守設計 |
| round1.md | L3 | CONFIRMED_ASIS |
| round2.md | 2.10 | 正確業務邏輯 |
| round2.md | 2.13 | 架構決策 |
| FINDING-09 | — | 待刪除功能 |
| FINDING-27 | — | 待刪除功能 |
| FINDING-23 | — | 法規正確（駁回原始疑慮） |
| FINDING-25 / 25-01 | — | 暫定政策（temporary，可重新評估；已有 plan.md B4 對應，見 PLANNED.md） |
| Fixed-132 / Issue 104, 105 | [WONTFIX-27](WONTFIX-27-rejection-of-fast-forward-catch-up-mechanisms.md) | 核心架構鐵則（全面廢除向當前時間補算機制，模擬起點維持快照原始時間點） |

