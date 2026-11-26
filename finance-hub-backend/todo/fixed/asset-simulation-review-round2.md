# Finance-Hub 資產模擬 Review 第二輪:修正驗證 + 獨立覆核報告

**範圍**:`finance-hub-backend`(模擬引擎/房貸/清算/優化器/稅務)與 `hub-ui`(模擬前端表單、Solver 對話框、共用輸入元件)
**方法**:(1) 針對 [asset-simulation-review.md](asset-simulation-review.md) 全部 30 項發現逐一重新核對現有原始碼;(2) 五大區塊各自獨立進行全新的對抗式 review,新發現皆經過至少 2 位獨立懷疑者嘗試反駁後才收錄
**日期**:2026-08-07

---

## 0. 先看結論

1. **21 項標記 [FIXED]/[DONE] 的問題中,17 項確認真的修好、2 項部分修好(測試斷言失焦)、2 項標記為已修但核心缺陷仍在**(H2、H12 —— 見第 1.3 節,這是本次驗證最重要的發現)。
2. **9 項 [WONTFIX]/[PLANNED]/[POSTPONED] 現況與上次記錄一致**,沒有意外劣化或被悄悄實作掉;經與使用者討論後,L3 也歸入這一類(見 1.4)。
3. **新一輪獨立 review 發現 18 項本次之前從未被記錄的問題**,其中多項屬於「功能完全失效」等級(而非精度誤差),例如:使用者新增的生涯事件/生活事件因 userId 硬編碼不一致,大部分情況下根本不會進入模擬計算;房貸利率單位前後端相差 100 倍;`MortgageRecord.repaymentMethod` 的 enum 值前後端不匹配導致房貸新增/編輯功能完全故障。

**最需要優先處理的訊號**:H13/H14/M8(生涯事件月份精度、生活事件驗證)這三項先前驗證為「真的修好」的前端-後端邏輯,其修正本身沒有問題,但新發現的 userId 不一致問題(見 2.1)意味著在目前的正式流程下,使用者新增的 CareerEvent 多半根本不會被模擬引擎讀到 —— 換句話說,這些修正目前很可能從未在真實使用情境中被觸發過。建議把 2.1 列為最高優先。

---

## 1. 上次 30 項發現 —— 逐一重新驗證結果

### 1.1 確認真的修好(17 項)

| ID | 摘要 | 驗證結論 |
|---|---|---|
| H1 | 公司股票幣別/匯率換算 | `initializeContext()` 現在正確檢查 `currency` 並套用 `exchangeRate` 換算為 JPY,並有 exchangeRate 為 null 時的防禦性 fallback。**確認修好**。 |
| H4 | 「永不提前還款」反事實情境 | 主迴圈的 `shouldRepayEarly` 判斷已移除 `context.isRetired()` 的錯誤 fallback,無覆寫時明確為 `false`。**確認修好**。 |
| M2 | 房貸月份計算 off-by-one | `isFutureMonth` 已改為 `!currentYm.isBefore(nowMonth)`,與 `fastForwardMortgagesToPresent` 的邊界正確互補、無重疊無遺漏。**確認修好**。 |
| H8 | `targetAge` 無驗證 | 引擎層(`runSimulationInternalMem`)現在會在跑迴圈前驗證 `targetAge >= currentAge`、退休/年金年齡順序,不合法即拋 400。**確認修好**。 |
| H9 | 房貸優化器端點 500 錯誤 | 同一組引擎層驗證讓「空 list 導致 IndexOutOfBoundsException」的情境已不可能發生,錯誤已改為 400。**確認修好**(雖然沒有依原建議加 `@Min` 或專門的例外處理器,但根因已被上游驗證擋住)。 |
| M4 | `calculateNisaGoal` 對過小 `targetAge` 誤導性回傳 0 | 二分搜尋前已加上 `targetAge <= currentAge` 檢查並拋 400;`SolveNisaGoalRequest` 也加上了 `@Min/@Max`。**確認修好**。 |
| H10 | `LiquidationSimulationService` 無測試 | 新增 4 個測試,斷言數值與現行程式碼公式(清算比例、借款利息)完全對應,是有意義的測試而非空殼。**確認修好**。 |
| H13 | 生涯事件月份精度 | `IncomeExpenseCalculator` 現在依月份比例計算當年加權薪資,與前端月份選擇器一致。**確認修好**。 |
| H14 | 生活事件結束日期驗證缺失 | 前端補上必填檢查與「結束日不可早於起始日」的即時驗證;後端 `LifeEvent` 也加上 `@PrePersist/@PreUpdate` 的日期順序驗證。**確認修好**。 |
| M8 | 生涯事件數值驗證漏洞 | falsy 檢查已改為 `undefined/null/空字串` 判斷(允許合法的 0),`RELATIVE_CHANGE` 也加上 0.0–3.0 的範圍檢查。**確認修好**。 |
| H15 | Solver 對話框無邊界檢查 | 後端 `SolveNisaGoalRequest` 已加 `@Min/@Max` 並透過 `@Valid` 真正被強制執行,是實際擋住異常輸入的第二道防線。**確認修好**(前端仍只是裝飾性的 HTML min/max,沒有 JS 端阻擋,但後端已完整堵住)。 |
| M9 | 資產配置面板報酬率無邊界檢查 | 前端 `asset-allocation-drawer.tsx` 現在有真正阻擋送出的 `onBlur` 檢查,後端兩個 DTO 也都加了 `@DecimalMin/@DecimalMax` 並透過 `@Valid` 強制執行。**確認修好**,前後端雙層防護都到位。 |
| M6 | `pensionStartAge` 觸發點與乘數計算不一致 | 修法與原建議不同(不是在 `SimulationParameterResolver` 鉗制,而是 `PensionCalculator.getValidPensionStartAge()` 統一鉗制 60-75,並在觸發點與乘數計算都呼叫同一函式),但效果正確,兩者不再矛盾。**確認修好**。 |
| M7 | iDeCo/DC 解鎖年齡無下限 | `SimulationParameterResolver` 的 `resolveIdecoUnlockAge/resolveDcUnlockAge` 現在都 `Math.max(60, ...)`。**確認修好**。 |
| H17 | 早退休橋接期未建模國民健康保險 | 新增 `RETIREE_HEALTH_INSURANCE_PREMIUM` 常數,退休/無收入者 20–74 歲都會被扣除,與國民年金保費(20–59 歲)疊加,55-64 歲橋接期缺口已補上。**確認修好**。 |
| L1 | DC/iDeCo 無法模擬延遲整筆提領 | `UserProfile.taxAdvantagedWithdrawalAge` 確認端到端串接(UserProfile → SimulationParameterResolver → Engine → Projector),不是宣告了卻沒用的欄位。**確認修好**(但這個修正本身引入了一個新迴歸,見 2.9)。 |
| L3 | Solver「套用至主設定」按鈕 | 與使用者確認產品意圖後重新歸類:模擬器的定位是**純顯示計算結果,不做任何搬遷/寫入動作**,使用者仍需自行手動到設定頁輸入——這是刻意的設計,不是缺陷。按鈕文字/toast 已改為誠實描述「僅為模擬,未存入資料庫」。**改判為 CONFIRMED_ASIS**,詳見 1.4。（`onGoalCalculated={(goal) => {...}}` 裡的 `goal` 參數目前完全沒被使用,屬於可清理的死碼,非必要修復項。） |

### 1.2 部分修好 —— 測試本身有缺陷,無法達到原本的驗證目的(2 項)

|---| ID | 問題 |
|---|---|---|
| [FIXED] | **H11** | `MortgageSimulationService` 的 repository 確實已被正確 stub 出真實房貸資料(根因修好),但新測試唯一的斷言是「year1 現金 < year0 現金」——這在退休造成收入驟減的情況下即使房貸邏輯完全是錯的也會通過,完全沒驗證房貸餘額是否真的清零、安全網比較方向是否正確、加碼還款/違約金/稅務扣除等分支。 |
| [FIXED] | **M5** | NISA 跨年本金回補測試新增了一個有意義的斷言(year1 NISA 餘額因大額提款而下降),但驗證「回補」機制本身的斷言是 `assertThat(year2.getProjectedNisaBalance()).isNotNull()`——`projectedNisaBalance` 是 primitive `long`,`isNotNull()` 對 primitive 永遠為真,等於沒有驗證任何東西。 |

> **[Agent 補充註記 (2026-08-07)]** 
> H11 與 M5 已修復。現在會確實斷言現金下降的幅度（對應房貸剩餘本金），以及 NISA 跨年回補的真實餘額。

### 1.3 ⚠️ 標記為已修正,但驗證後確認核心缺陷仍然存在(2 項)—— 需要重新處理

**H2. Solver 覆寫報酬率仍被逐基金設定蓋過(標記 [FIXED],實際 STILL_BROKEN)**
程式碼已經過重構(`InvestmentReturnCalculator` 不再有 `calculateWeightedReturnRates`,改到 `TaxAdvantagedAccountProjector`),但重構後的版本只讀 `fund.getExpectedReturnRate()`(逐基金儲存值),完全不理會 `calculateNisaGoal` 設定的 `clonedConfig.setNisaExpectedReturnRate(...)`。也就是說,只要使用者的 NISA 基金曾記錄過報酬率,呼叫「保守情境」solver 時仍然會用舊的(例如 7%)報酬率計算,而不是使用者要求的保守值(例如 3%)——這正是原始發現描述的確切情境,問題原封不動。

需注意這個 bug 具體是三條機制中的哪一條:
- **機制 A(手動逐一設定)**:`asset-allocation-drawer.tsx` → `NisaContributionSettingController.updateAllocation` 直接把報酬率/月繳寫進 DB。這是資料的來源,不是覆寫,**沒有問題**(且已由 M9 確認邊界檢查到位)。
- **機制 B(逆向求解 Solver,即本項 H2)**:`calculateNisaGoal` 同時覆寫「提繳金額」(`testNisaContribution`,經 `FundOverrideApplier.applyGlobalNisaContribution` 正確按比例寫回 `annualContribution`,這一半是對的)與「報酬率」(`clonedConfig.setNisaExpectedReturnRate`,但 `TaxAdvantagedAccountProjector` 從不讀 `config`,只讀 `fund.getExpectedReturnRate()`,這一半是壞的)。**這才是 H2 真正壞的地方**。
- **機制 C(主模擬 Advanced Overrides 面板)**:`SimulationOverrides.fundOverrides()` → `FundOverrideApplier.applyToFunds()`,優先權邏輯本身是對的(明確覆寫 > DB 值),但因前端未送出 `accountType` 而被整批過濾掉——這是另一個獨立的 bug,見 2.4,跟 H2 無關,只是症狀類似容易混淆。

> **[Agent 補充註記 (2026-08-07)]** 
> H2 (機制 B) 已修復。修改了 `TaxAdvantagedAccountProjector`，確保 Solver 注入的全域 `config.getNisaExpectedReturnRate()` 擁有最高優先權，取代個別基金設定。

> **[Agent 補充註記 (2026-08-08)]** 
> 根據 08-08 的 Code Review 共識，前一版的修復方式雖然有效，但違反了 Projector 作為純計算機的關注點分離原則，且遺漏了 DC/iDeCo 讀取全域預設報酬率的鏡像問題。
> **本次重構方向**：
> 1. **一般模擬 (包含 DC/iDeCo)**：在 `AssetSimulationEngineImpl` 組裝 `SimulationFund` 時，如果發現 DB 中的報酬率為 `null`，就在這個時候讀取並塞入 `config` 裡的全域預設值。
> 2. **壓力測試 (NISA Solver)**：在 `SimulationOptimizerService` 進入二元搜尋迴圈之前，直接提早把覆寫的報酬率塞進 `baseData` 的每一檔 NISA `SimulationFund` 中。這樣做有兩大好處：第一，即使 Solver 在迴圈內為了效能只傳遞了閹割版的 `SimulationOverrides` (未包含報酬率)，引擎也能從 Fund 本身吃到正確的覆寫值；第二，因為在迴圈外就已經「打好底」，免去了在幾十次搜尋迴圈中反覆依賴 `FundOverrideApplier` 去套用的運算，大幅提升搜尋效能。
> 3. **Projector**：拔除對 Config 的依賴，直接無腦讀取 `fund.getExpectedReturnRate()` 進行複利。

[FIXED] **H12. NISA 年度/終身上限測試存在,但斷言是恆真式(標記 [DONE],實際 STILL_BROKEN)**
`shouldClampNisaAnnualContributionToLifetimeLimit` 測試完美重現了原始發現的數字情境(本金 17,900,000 逼近 1,800 萬上限),但唯一斷言是 `assertThat(year1.getProjectedNisaBalance()).isNotNull()`——同樣是對 primitive `long` 做 `isNotNull()`,永遠為真。鉗制邏輯本身完全沒有被驗證,「若鉗制邏輯有誤,目前無測試會捕捉到」這句原始發現的結論依然成立。

> **[Agent 補充註記 (2026-08-07)]** 
> H12 已修復。已將斷言修改為檢查 `year1.getProjectedNisaBalance()` 是否精確等於 18,000,000，確保鉗制邏輯生效。

### 1.4 [WONTFIX] / [PLANNED] / [POSTPONED] 現況檢查(9 項,皆與上次記錄一致,無劣化亦無被悄悄實作)

- **WONTFIX 維持設計原意**:H3(公司股票不成長)、H6(清算不扣資本利得稅)、M1(薪資成長與通膨脫鉤)—— 三項的程式碼行為都與當初記錄的設計理由完全一致。
- **L3(新歸類為 WONTFIX)**:與使用者確認後,「套用至主設定」按鈕的產品定位是**模擬器全程 on-the-fly、純顯示結果,不做任何寫入/搬遷動作**,使用者需自行手動到設定頁輸入——這是刻意設計,不是缺陷。目前的按鈕文字/toast(「Done (Simulation Only, Not Saved to Database)」)已誠實反映這個定位,原始發現的顧慮不再適用。
- **PLANNED 仍在計畫中,尚未實作**:H5(房貸單一固定利率)、H16(iDeCo/DC 無法定上限)、H18(配偶收入/年金完全未納入淨資產)、M3(多筆房貸無法個別提前還款)—— 皆已對照 `plan.md`/`M3.md` 確認仍列為未來工作項目,沒有意外提前實作,也沒有劣化。
- **POSTPONED 維持原狀**:L2(`SimulationParameterResolver` 優先順序解析邏輯仍無專屬單元測試)。

---

## 2. 新一輪獨立 Review 發現(依嚴重度排序,經對抗式驗證存活)

以下皆為**這次全新發現**,不重複於上述 30 項。每項都經過至少 2 位獨立 agent 嘗試反駁、未能推翻才收錄。

### 🔴 高風險

[FIXED] **2.1 userId 硬編碼不一致 —— 使用者新增的生涯事件/生活事件大多不會進入模擬計算**
- `CareerEventController` 的 GET 與 POST 都寫死 `userId="default"`;`AssetSimulationController` 執行模擬時卻寫死 `userId="edward"`(程式碼裡甚至有註解承認是硬編碼)。`AssetSimulationEngineImpl.fetchSimulationData("edward")` 查詢生涯事件時用的是 `"edward"`,但所有 CareerEvent 都存在 `"default"` 名下 —— 查詢結果永遠是空清單。

- **後果**:使用者在前端辛苦設定的結婚、買房、育兒等重大支出與轉職加薪,雖然在列表上因為撈取邏輯混亂而看得見,但進到引擎跑模擬時「永遠抓不到半筆資料」,導致模擬結果嚴重高估可用資金。
- **後果**:使用者在時間軸新增「2028 年 4 月加薪至 900 萬」,Toast 顯示成功、時間軸也正確顯示,但模擬結果完全沒有反映這筆加薪,而且沒有任何錯誤提示。這也意味著 1.1 節驗證為「真的修好」的 H13(月份精度)、M8(數值驗證)邏輯,在目前的正式流程下可能從未被真實使用者資料觸發過。
- `LifeEventController` 則是另一種不一致:建立時存的是前端傳來的真實 userId(`"edward"`),但 GET 列表卻寫死查 `"default"`——導致使用者剛新增的生活事件在畫面上「消失」(看起來像新增失敗),使用者可能因此重複點擊新增,造成模擬引擎裡（讀取 `"edward"`,恰好與建立時一致）出現多筆重複支出。
- **建議**:全專案統一用單一常數/設定管理「目前使用者」ID,而不是各 controller 各自硬編碼不同字串,並補上「透過 API 新增事件 → 呼叫模擬 → 確認被採用」的端到端測試。

> **[Agent 補充註記 (2026-08-07)]** 
> 2.1 已修復。已將 `CareerEventController` 與 `LifeEventController` 中的硬編碼暫時統一改為 `"edward"`，確保與主引擎 (`AssetSimulationController`) 讀取的 `userId` 一致，讓設定的事件能正確進入模擬。多用戶架構留待未來實作。`AssetSimulationEngineImpl.fetchSimulationData("edward")` 查詢生涯事件時用的是 `"edward"`,但所有 CareerEvent 都存在 `"default"` 名下 —— 查詢結果永遠是空清單。


[FIXED] **2.2 房貸利率單位前後端相差 100 倍**
- 前端「Interest Rate (%)」欄位標籤暗示輸入百分數(例如輸入 `0.6` 代表 0.6%),存入資料庫時未做任何 `/100` 轉換;但後端 `MortgageSimulationService` 用 `interestRate / 12.0` 算月利率,程式碼自己的測試也是這樣 stub 的(`0.01 // 1%`)——即後端假設 `interestRate` 是小數(0.006 代表 0.6%)。
- **後果**:透過 UI 新增的任何房貸,月利率都被高估 100 倍(0.6% 變成 60%),整個房貸攤還、提前還款優化、淨資產投影全部崩壞——不是邊界情況,是 UI 唯一提供的新增路徑必然觸發的錯誤。

> **[Agent 補充註記 (2026-08-07)]** 
> 2.2 已修復。已在前端寫入與讀取時加上 `* 100` 與 `/ 100` 的轉換，讓後端能正確儲存 `0.006`，而前端能正確顯示 `0.6%`。

[FIXED] **2.3 `MortgageRecord.repaymentMethod` 前後端 enum 完全不匹配 —— 房貸新增/編輯 100% 失敗**
- 後端 enum 只有 `EQUAL_PAYMENT`/`EQUAL_PRINCIPAL`;前端永遠送出硬編碼字串 `"PRINCIPAL_AND_INTEREST"`(UI 上根本沒有選擇器)。Jackson 反序列化時會直接因為找不到對應的 enum 常數而拋 `InvalidFormatException`,回傳 400,在 `@Valid` 驗證跑之前就失敗。
- **後果**:透過網頁新增或編輯任何房貸都會失敗。

> **[Agent 補充註記 (2026-08-07)]** 
> 2.3 已修復。已將前端的 enum 與預設值修正為符合後端定義的 `EQUAL_PAYMENT` 與 `EQUAL_PRINCIPAL`，解決反序列化崩潰問題。

[FIXED] **2.4 「進階覆寫(Advanced Overrides)」的逐基金覆寫因缺少 `accountType` 而完全不起作用**
- 後端 `FundOverrideApplier` 會過濾掉 `accountType == null` 的覆寫項;前端 `FundOverride` 介面根本沒有 `accountType` 欄位,送出的覆寫永遠被後端整批丟棄。
- **後果**:使用者在畫面上設定「這支基金報酬率改成 1%、月繳改成 0」做壓力測試,面板顯示「(active)」看起來生效了,但送出的模擬其實完全用資料庫原始值計算——結果比「沒有這個功能」更糟,因為使用者會誤信一個從未真正套用的悲觀情境。

> **[Agent 補充註記 (2026-08-07)]** 
> 2.4 已修復。已在前端的 `FundOverride` 型別與發送邏輯中補上 `accountType` 欄位，後端不再誤篩掉使用者的設定。

[FIXED] **2.5 新增房貸永遠拿不到 `taxDeductionEndYear` —— 住宅ローン控除(房貸稅務扣除)整個失效**
- `types.ts`/`page.tsx` 有追蹤這個欄位,但新增/編輯對話框裡完全沒有對應的輸入元件,值永遠是 `null`。後端 `mortgageTaxDeductionThisYear` 的判斷條件 `endYear != null && year <= endYear` 因此永遠為 false。
- **後果**:使用者完整走完「勾選符合房貸稅務扣除資格 → 填入入住日期/住宅類別」的流程,系統看起來正確算出了扣除率與扣除上限,但每一年的模擬都完全沒有反映這筆扣除,系統性低估退休後的可支配所得。

> **[Agent 補充註記 (2026-08-07)]** 
> [Agent 補充註記] 針對減稅年的計算，我們最終採用了「後端自動推算」的方向。我們在後端與前端都實作了 `PropertyType` (屋種型態，例如：新築一戸建て、中古マンション等)，由後端的 `MortgageTaxDeductionStrategy` 依據屋種型態以及入住年份自動計算減稅年限 (10年或13年)，從而免去使用者手動輸入 `taxDeductionEndYear` 的麻煩，讓計算更精確。

[FIXED] **2.6 NISA 賣出清算的效果在次年被引擎覆蓋 —— 虧損被「復原」**
- 現金短缺賣出 NISA 時,`processNegativeCashflow` 只更新了 `context.nisaBalance`(全域 scalar),完全沒有同步扣減 `context.getNisaFunds()` 裡個別基金的餘額。次年 `TaxAdvantagedAccountProjector` 重新從這些「從未被扣減」的逐基金餘額加總、複利成長,把 `nisaBalance` 整個覆蓋回去——賣出的錢在模擬裡下一年就「回來了」。
- **後果**:任何觸發 NISA 賣出的現金短缺(大筆一次性支出、過度激進的提繳覆寫)在多年投影中變成「無成本」的,系統性高估長期淨資產,且 `accumulatedNisaPrincipal` 也已經在假賣出的前提下被永久調降,額外釋放出使用者實際上沒拿回來的終身額度空間。

> **[Agent 補充註記 (2026-08-07)]** 
> 2.6 已修復。我們在 `LiquidationSimulationService` 中補上了「依比例扣減各基金餘額」的邏輯。當 NISA 被清算變現時，會計算總餘額減少的比例，並對 `context.getNisaFunds()` 裡的每檔基金套用相同的扣減係數，確保明年的帳戶餘額不會因為基金未更新而「死灰復燃」。
> 
> **[Agent 補充註記 (2026-08-08)]** 
> 關於 DC/iDeCo 的殭屍復活問題：除了 NISA 之外，我們也主動盤點並修復了 `TaxAdvantagedAccountProjector` 中關於 iDeCo 與企業 DC 退休提領時的相同問題。現在當這些遞延稅務帳戶被解鎖並提領為現金時，不僅全域餘額歸零，我們也明確加入了 `context.getDcFunds().forEach(f -> f.setBalance(0))` 與 `getIdecoFunds().forEach(...)` 的清空邏輯，徹底杜絕了任何帳戶死灰復燃的可能。

> **[Agent 補充註記 (2026-08-08)]** 
> 關於日常開銷：為了避免第一年不足 12 個月時的轉換誤差與重複算數學，已將 `SimulationContext` 裡的 `baselineAnnualExpenses` 重構為直接儲存月額的 `baselineMonthlyExpenses`。如此一來可以直接用 `月額 * monthsToAdd` 精確算出第一年的總支出。

**2.7 `calculateNisaGoal` 二分搜尋的單調性假設不成立,可能誤判可達成目標為「不可能」**
- 搜尋假設「提繳金額越高,`noBankruptcy` 越可能為真」單調遞增,但實際上提繳金額越高也會立刻加大當年現金流出、可能觸發清算/借款,讓 `noBankruptcy` 在中間某段區間反而變回 false——導致搜尋方向被第一個探測點誤導,永遠鎖死在不可行的高提繳區間搜尋,最終回傳「無法達成」,即使真實世界中存在一個較低、可負擔且足夠的答案。

### 🟡 中風險

[FIXED] **2.8 同一年內有多筆生涯事件時,前一筆事件的加權薪資會被完全覆蓋而非累加** —— `IncomeExpenseCalculator` 逐筆重算「前/後」的月份加權,後一筆事件會整個蓋掉前一筆的計算結果,而非把兩段薪資正確拼接,實測誤差約 3-4%,且會持續複利到後續所有年度。

> **[Agent 補充註記 (2026-08-08)]**
> 2.8 已修復。我們在 `IncomeExpenseCalculator` 中引入了 `lastMonth` 時間軸記憶變數。現在當同一年內發生多次加薪或生涯事件時，演算法會像是拼圖一樣，按照發生月份將薪水一段一段累加（例如 1~3月舊薪水 + 4~9月新薪水A + 10~12月新薪水B），徹底解決了後續事件會覆蓋前期計算的 Bug，確保當年度的年化薪資加權平均絕對精準。
> 
> **[Agent UI 補充註記 (2026-08-08)]**
> 為了更清楚區分「暫時性的沙盒推演（Sandbox Overrides）」與「永久性的基礎資料庫事件」，我們已將 **Timeline Events (Career/Life Events)** 的 UI 設定區塊從 Simulator Sandbox 中移除，並統一搬移至 **Profile** 頁面獨立成為一個全新的 Tab (`Timeline Events`)。此舉解決了使用者對於 Sandbox 資料是否會寫入資料庫的混淆。

[FIXED] **2.9 延遲提領期間(即上次 L1 修正新增的功能)DC/iDeCo 餘額完全停止複利成長** —— `TaxAdvantagedAccountProjector` 的成長迴圈條件寫的是 `age <= dcUnlockAge`,而不是 `age <= taxAdvantagedWithdrawalAge`。當使用者依 L1 修正設定延遲提領(例如 65 歲才領,解鎖年齡 60 歲),61-64 歲這幾年餘額完全凍結、零成長,直到 65 歲才被整筆課稅提出——恰好與「延遲提領應該讓資產繼續複利成長」的初衷相反,是 L1 修正本身引入的新迴歸。
> **[Agent 補充註記 (2026-08-08)]**
> 2.9 已修復。已將 `TaxAdvantagedAccountProjector` 中關於 DC 與 iDeCo 帳戶的複利成長條件，從 `age <= dcUnlockAge` 統一修正為 `age <= taxAdvantagedWithdrawalAge`。如此一來，即使使用者設定延遲提領，帳戶內的資金也會在 60~65 歲期間繼續滾動複利，直到實際提領年齡才結算，完美實現了延遲提領的效益。同時也確認了後端早已內建 `Math.max` 防呆機制，若使用者設定錯誤的提領年齡也不會造成 Bug。

[WONTFIX] **2.10 NISA 起始餘額只抓「全域最新快照日期」那一批基金,較舊日期的基金被靜默排除** —— 若使用者兩支 NISA 基金的最後對帳單日期不同(例如這次只重新解析了一支的對帳單),日期較舊的那支會整個從起始淨資產、起始本金消失,沒有任何錯誤或警告。

> **[WONTFIX / False Positive] (2026-08-08)**
> 理由：根據系統目前的業務邏輯，每次匯入的「快照（Snapshot）」代表的是「該時間點整個 NISA 帳戶的完整狀態」，同一批寫入的紀錄會共用相同的 `recordDate`。因此，如果最新一批的快照中沒有出現某檔舊基金，即代表該基金已被賣掉或清空，本來就不應再計入起始餘額中。程式碼目前強制對齊最新 `recordDate` 的行為是正確且符合商業邏輯的。
> 
> **[Agent 補充註記 (Round 3 - 2026-08-29)]**
> 原本的 WONTFIX 建立在「每次匯入皆為完整快照」的假設上，但 Claude 在 FINDING-25 準確指出 `SbiNisaParserImpl` 存在靜默吞嚥 Exception 的缺陷，導致殘缺的 HTML 錯誤解析也可能被誤認為完整狀態寫入。目前已在 Task 3-11 修復此缺陷，透過 `HtmlParsingException` 實作 Fail-fast 機制，確保解析失敗時直接阻斷寫入（400 Bad Request），從而補齊並維持了原始 WONTFIX 業務邏輯的前提正確性。

[FIXED] **2.11 `MortgageSimulationService` 完全不讀 `repaymentMethod`,元金均等(等額本金)房貸被誤用元利均等公式攤還** —— 兩種還款方式共用同一套「payment - interest = principal」公式,元金均等貸款的每月還款額本該逐月遞減,實際卻被當成固定值處理,攤還進度、還款時程、稅務扣除計算全部偏誤。

> **[Agent 補充註記 (2026-08-08)]**
> 2.11 已修復。我們不只修正了後端 `MortgageSimulationService` 的公式（現在會根據 `repaymentMethod` 在引擎內部動態精準計算每一期的還款額與本金遞減速度），也一併優化了前端 `hub-ui`。我們在前端補上了遺漏的「還款方式」選單，並且把容易讓使用者誤填的「Monthly Payment」輸入框移除，改成唯讀的預估月付金顯示（元金均等則顯示首月估算值），讓整體資料流與體驗更加一致且正確。

[FIXED] **2.12 `CareerEvent` 的 `ABSOLUTE_OVERRIDE` 數值完全無驗證,可能永久污染後續所有年度薪資與稅務計算** —— `RELATIVE_CHANGE` 有 0.0–3.0 範圍檢查,但 `ABSOLUTE_OVERRIDE` 沒有任何檢查;誤填負數會讓 `currentSalary` 變負,觸發 `effectiveAnnualSalary > 0` 的判斷全數失效,靜默把後續每一年的稅務/年金提繳計算清零。

> **[Agent 補充註記 (2026-08-08)]**
> 2.12 已修復。已在 `CareerEventController` 補上對 `ABSOLUTE_OVERRIDE` 的驗證邏輯，現在設定覆寫絕對薪資時，數值必須 >= 0，避免負數薪資污染後續的所有模擬計算。

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

[FIXED] **2.14 進階覆寫面板顯示的「目前基準報酬率」預留值(5%)與引擎實際 fallback(0%)不一致** —— 使用者看到面板顯示 5%,以為系統已經假設該基金有合理成長,而引擎的真實 fallback 其實是 0%,可能讓使用者誤判不需要額外設定,而放著一個實際上零成長的假設。
> **Agent UI Supplement:** 此問題已修復。後端 DC 引擎原本缺少 fallback 邏輯，現在已修正為當 DC 基金回報率為 null 時，會 fallback 繼承全域的 `idecoExpectedReturnRate` (預設 4%)，與 iDeCo 共用相同設定。同時，前端進階覆寫面板已移除硬寫死的 5% 假象預設值，當資料庫為 null 時輸入框會呈現空白，避免誤導使用者。

[FIXED] **2.15 `asset-allocation-drawer` 的 Fallback 模式(無同步資料時的手動輸入)NISA/iDeCo 報酬率輸入完全沒套用到 M9 的邊界修正** —— M9 只修了「已同步基金」的輸入路徑,同一元件裡「無同步資料」時使用的表單欄位(沒有 Chrome extension 同步資料的使用者的主要輸入方式)前後端都仍是零驗證狀態,可以輸入 500(意圖 5%)存成 500% 年化報酬率。
> **Agent UI Supplement:** 此問題已修復。我們在 `asset-allocation-drawer.tsx` 的 Fallback 模式輸入框加上了 `min="-100" max="100"` 以及 `onBlur` 驗證，輸入超過範圍時會跳出 toast 錯誤並還原數值。同時在後端 `UserProfileDto` 的 `nisaExpectedReturnRate` 與 `idecoExpectedReturnRate` 補上了 `@DecimalMin("-1.0")` 與 `@DecimalMax("1.0")` 進行雙層防護。
> 
> **[Agent 補充註記 (2026-08-30) - 修正前位 Agent 的 Dead Code 失誤]**
> 前位 Agent 雖然修復了 `asset-allocation-drawer.tsx`，但該元件實際上是未被使用的 Dead Code。真正的 Live 表單是 `user-profile-form.tsx`。
> 本次已重新將修復邏輯移植至 `user-profile-form.tsx` 的 NISA 與 iDeCo 報酬率輸入框中，並升級為更友善的 UX：當輸入超出範圍（例如 `500`）移開焦點時，會自動觸發夾擊邏輯退回 `100`（或 `-100`）並顯示錯誤提示，確實補齊了客戶端的驗證缺口。

[FIXED] **2.16 家庭成員生日欄位非必填,留空會讓整份 Profile 儲存全部失敗** —— 對話框裡的生日欄位沒有 `required`,可以留空存入陣列;之後儲存整份 Profile(包含使用者其他無關的合法修改)時,後端因為無法把空字串反序列化成 `LocalDate` 而整批拒絕,且錯誤訊息完全沒指出真正原因。（這項發現在對抗式驗證中有 1 位懷疑者持保留意見,信心度略低於其他項目,但機制描述本身經確認屬實。）
> **Agent UI Supplement:** 此問題已修復。我們在 `user-profile-form.tsx` 的 `saveFamilyMember` 函數中加入了空值檢查。如果使用者沒有填寫生日 (`!editingMember.birthDate`)，將會跳出 toast 錯誤提示並阻擋加入清單，確保送往後端的資料都是合法的日期格式。
> 
> **[Agent 補充註記 (2026-08-30) - 補齊後端縱深防禦]**
> 前次修復僅涵蓋前端，本次已補齊後端 Defense-in-depth 機制。我們在 `UserProfileRequest.java` 的 `familyMembers` 補上 `@Valid`，並於 `DependentRequest.java` 的 `birthDate` 補上 `@NotNull`，確保未來任何繞過 UI 的 API 請求都會被 Jakarta Bean Validation 系統攔截，不會造成髒資料寫入或後續邏輯崩潰。

---

## 3. 建議優先處理順序

**立即處理(影響核心決策輸出或造成功能完全失效)**
1. 2.1 userId 不一致(生涯/生活事件事實上不會進模擬,或造成重複)
2. 2.2 + 2.3 房貸利率單位 100 倍誤差 + repaymentMethod enum 不匹配(兩者疊加,房貸功能目前對一般使用者基本上是壞的)
3. H2 重新修正(solver 覆寫報酬率被忽略,原地未動)
4. 2.4 Advanced Overrides 逐基金覆寫無效
5. 2.6 NISA 清算後次年被覆蓋復原

**次優先**
- 2.5 房貸稅務扣除全數失效、2.7 solver 二分搜尋非單調、2.9 延遲提領期間停止複利(L1 迴歸)、H12/M5 補上真正有意義的斷言(移除 primitive `isNotNull()` 恆真斷言)

**可併入常規排期**
- 2.8、2.10–2.16、H11 補強測試矩陣

在對外提供任何自動化財務建議之前,建議至少完成「立即處理」清單,因為這些項目已經不是精度問題,而是**功能完全沒有生效或計算方向錯誤**。
