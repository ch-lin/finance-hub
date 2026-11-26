# PLANNED 清單

> 本清單彙整所有標記為 PLANNED / POSTPONED 且已知未來會處理的項目。
> 依使用者 Note 5 規則：只要在 `plan.md` 有對應項目就交叉引用；若沒有對應項目，仍列在此處並註記「plan.md 未列」。

---

### Plan 01 — 教育費用計算改為以月為單位（逐月計算消除學制切換誤差與大學末期學費遺失）
- **來源**：`EducationExpenseCalculator` 商業邏輯重構需求（對應 `Plan-01.md`、`Plan-01 Phase 1`、`Plan-01 Phase2`）
- **文件**：[Plan-01.md](Plan-01.md)
- **狀態**：✅ 已完成（交付已Review，2026-10-09）
- **簡述**：現有教育費計算整年只認一種學制，導致年中換學制（如 3 月高中畢業、4 月上大學）費用算錯，且大學最後數月學費憑空消失。重構為逐月判斷學制與年級加總，精準計算年度教育總支出。

### Plan 02 — 多使用者架構、家族共享與隱私控制
- **來源**：多使用者架構升級需求（原 Phase 1 Epic）
- **文件**：[Plan-02-多使用者架構、家族共享與隱私控制.md](Plan-02-多使用者架構、家族共享與隱私控制.md)
- **狀態**：✅ 已實作完成（交付待驗證）
- **簡述**：系統從單人使用升級為家族多成員環境。包含建立 `MultiUserBaseEntity`、Hibernate Filter 多租戶隔離、家族群組與三級隱私控制、清除所有 hardcode 使用者名稱（如 "edward"）並對齊 Auth Service JWT `userId`、前端 View Scope 切換與家族管理頁面。單筆明細隱私寫入技術債移至 Plan 19，模擬器雙人現金流整合移至 Plan 03。

### Plan 03 — 家族財務服務與模擬器整合 (Family Financial Service & Simulator Integration)
- **來源**：`asset-simulation-review-round1.md` H7 / H18（多重收入來源 / 配偶薪資、年金與資產整合）
- **文件**：[Plan-03-家族財務服務與模擬器整合.md](Plan-03-家族財務服務與模擬器整合.md)
- **狀態**：PLANNED
- **簡述**：引入 `FamilyFinancialService` 與 `SpouseYearlySnapshot`，支援 Sandbox 模式（Dependent 設定或薪資估算）與 Link 模式（實體帳號連結），將配偶之財務貢獻解耦納入家庭現金流模擬器與最佳化引擎，同時完整解決 H7（配偶薪資遺漏）與 H18（配偶年金與資產未納入淨資產）。

### Plan 04 — Grid Search 支援多筆房貸各自獨立搜尋清償年份
- **來源**：Issue-08-01 審查擴充構想
- **文件**：[Plan-04-grid-search-multi-mortgage-dimension.md](Plan-04-grid-search-multi-mortgage-dimension.md)
- **狀態**：PLANNED
- **簡述**：記錄 Grid Search 對每筆房貸獨立搜尋最佳清償年份的架構構想（擴充 Chromosome 與維度展開），以及維度組合空間指數爆炸（$O(\text{年限}^N)$）的風險防範與常數調整建議。

### Plan 05 — 支援 2024/2026 年後住宅貸款扣除的「子育て世帯・若者夫婦世帯」加碼條件
- **來源**：令和 6 年 / 令和 8 年稅制改正住宅貸款扣除
- **文件**：[Plan-05-mortgage-child-rearing-support.md](Plan-05-mortgage-child-rearing-support.md)
- **狀態**：PLANNED
- **簡述**：依日本令和 6 年及令和 8 年稅制改正，住宅貸款扣除針對育兒家庭（未滿 19 歲親屬）與年輕夫婦（配偶未滿 40 歲）提供借入限度額加碼；重構 `MortgageTaxDeductionStrategy` 接收家庭成員資訊以精確計算退稅金額。

### Plan 06 — 信用卡交易與 Amazon 訂單完成對帳後，從未更新訂單的已核銷金額
- **來源**：Source Code Review (2026-09-09) — 原 `issue-06.md`
- **文件**：[Plan-06.md](Plan-06.md)
- **狀態**：PLANNED（使用者暫緩 Deferred）
- **簡述**：解決 `ReconciliationServiceImpl.linkTransaction` 建立 `TransactionLink` 後未更新 `order.reconciledAmount`，導致 `getUnreconciledBalance()` 永遠顯示全額未核銷的問題，並補足防止多筆交易累積連結金額超過 `billedAmount` 之防呆機制。

### Plan 07 — 支出搜尋 API 接受 keyword 與 isEssential 篩選參數，但實際上完全沒有套用
- **來源**：Source Code Review (2026-09-09)
- **文件**：[Plan-07.md](Plan-07.md)
- **狀態**：PLANNED（使用者暫緩 Deferred）
- **簡述**：解決 `ExpenseServiceImpl.searchExpenseItems` 接收 `keyword` 與 `isEssential` 參數卻未在 `Specification` 中加入對應 Predicate 的缺陷，補上文字搜尋與必要支出篩選邏輯。

### Plan 08 — 無 iDeCo/DC 法定月繳上限防呆
- **來源**：`asset-simulation-review-round1.md` H16 / FINDING-31
- **文件**：[Plan-08-no-statutory-ideco-dc-contribution-limit.md](Plan-08-no-statutory-ideco-dc-contribution-limit.md)
- **狀態**：PLANNED（暫緩執行 Deferred）
- **簡述**：`TaxAdvantagedAccountProjector` 缺乏對 iDeCo/DC 之法定上限約束（如自營業 6.8 萬、企業員工 1.2 萬或跨帳戶節流），造成使用者若設定超額繳納會虛增所得稅扣除額；比照 NISA 引入法定上限常數與節流檢查。

### Plan 09 — DC/iDeCo 解鎖年齡若解耦時防範退職所得控除雙重享受 (Double-Dipping Prevention)
- **來源**：FINDING-12 討論與 FINDING-37 / Phase 3 Task 3 衍生
- **文件**：[Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md](Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)
- **狀態**：PLANNED（潛在死碼防範）
- **簡述**：日本稅法要求同一年領取多筆退職金（企業DC、iDeCo等）必須「合算」扣除年資；目前系統因兩者提領年齡寫死相同而偶然正確，記錄若未來解耦提領年齡時，需防範同一年或相鄰年度退職扣除重複計算的稅務瑕疵。

### Plan 10 — SimulationOptimizerServiceOldImpl.findBestMortgageEarlyRepaymentYear 已退化為死碼
- **來源**：Issue-08-01 審查附帶發現
- **文件**：[Plan-10-dead-findBestMortgageEarlyRepaymentYear-degenerate-loop.md](Plan-10-dead-findBestMortgageEarlyRepaymentYear-degenerate-loop.md)
- **狀態**：PLANNED
- **簡述**：舊版最佳化服務中之 `findBestMortgageEarlyRepaymentYear` 因缺少 overrides 欄位已退化為死迴圈無效程式碼，且前端無任何呼叫端；待新版 Global Optimizer 驗證穩定後連同 controller endpoint 一併刪除。

### Plan 11 — 個人年金保險（PENSION 類型）保費被排除在所有稅務扣除計算之外
- **來源**：Source Code Review (2026-09-09) — 原 `issue-02.md` / FINDING-45
- **文件**：[Plan-11-individual-pension-insurance-premium-not-deducted.md](Plan-11-individual-pension-insurance-premium-not-deducted.md)
- **狀態**：PLANNED（使用者暫緩 Deferred）
- **簡述**：日本「個人年金保険料控除」為法定獨立扣除類別（上限 4 萬円），目前在職薪資計算之 switch 將 `PENSION` 忽略，退休/年金計算亦未掛載，低估長期可支配現金流；補上專屬分支與上限。

### Plan 12 — 讓 GA 在試算房貸提前還款時支援「減少月付金額」策略
- **來源**：房貸提前還款多策略優化需求
- **文件**：[Plan-12.md](Plan-12.md)
- **狀態**：PLANNED
- **簡述**：目前最佳化引擎試算提前還款僅支援「縮短年限」（做法A），擴充 GA 染色體與適應度函式使其同時支援「減少月付金額」（做法B，以現有 `EarlyRepaymentType` 公式試算），並依最佳現金流結果擇優推薦。

### Plan 13 — 支援未滿 3 歲（月級顆粒度，如 18 個月 / 24 個月）托育與早讀學制模型
- **來源**：Issue 134 討論與早讀托育需求拆分
- **文件**：[Plan-13-early-childhood-entrance-age-months.md](Plan-13-early-childhood-entrance-age-months.md)
- **狀態**：PLANNED
- **簡述**：支援如 Aoba 目黑校區 K2（18 個月）卡位早讀或 1 歲認可保育園送托，以月齡（`entranceAgeMonths`）為基準動態銜接至滿 6 歲（72 個月）小學，消除教育真空斷層。

### Plan 14 — 全域學制設定 Command 與 @ConfigurationProperties 徹底解耦重構計畫
- **來源**：Issue 134 架構覆核發現之壞味道（Fixed-110 遺留）
- **文件**：[Plan-14-education-config-command-decoupling.md](Plan-14-education-config-command-decoupling.md)
- **狀態**：PLANNED
- **簡述**：將 `CreateEducationConfigCommand` / `UpdateEducationConfigCommand` 徹底與 Spring Boot 的 `EducationConfigDefaultProperties` 解耦，建立專屬純淨的 `TrackConfigCommand`、`PreschoolConfigCommand` 等類別族，消除跨層依賴壞味道，回歸 Clean Architecture 最高規範。

### Plan 15 — E-Trade ESPP 與 RSU 持股明細獨立查詢/刪除端點及前端 UI 實作計畫
- **來源**：Question 117 架構討論與細粒度持股管理需求
- **文件**：[Plan-15-etrade-stock-lot-endpoints-and-ui.md](Plan-15-etrade-stock-lot-endpoints-and-ui.md)
- **狀態**：PLANNED
- **簡述**：提供專屬的 `GET /lots/espp`、`GET /lots/rsu`、`DELETE /lots/espp/{id}`、`DELETE /lots/rsu/{id}` 端點，並在前端提供專屬 Tabs 表格與安全刪除確認對話框，補足持股維護與調帳能力。

### Plan 16 — 高所得者個人住民稅基礎控除高所得級距遞減實作計畫
- **來源**：Question 118 覆核審計與 Question 120 時差模式延伸
- **文件**：[Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md](Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md)
- **狀態**：✅ 已完成（交付已Review，2026-10-09）
- **簡述**：針對年薪 2,500 萬高所得受薪階級（核心使用者），解決個人住民稅基礎控除寫死 43 萬、跨越 2,400 萬～2,500 萬門檻時未依《地方稅法》第314條之2遞減歸零（29萬/15萬/0円）之漏稅 Bug——這部分已確認正確實作運作。文件原本宣稱的「前年所得課稅時差機制」查證後確認從未實作，已移至[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)，狀態open、等未來評估雙引擎架構。

### Plan 17 — 全系統核心業務邏輯與邊界單元測試全覆蓋計畫
- **來源**：原 L2 測試債擴充與法規定案後之系統性品質強化需求
- **文件**：[Plan-17-system-wide-unit-test-coverage.md](Plan-17-system-wide-unit-test-coverage.md)
- **狀態**：PLANNED（排定於全數法規修正完成後統一執行）
- **簡述**：鑑於系統多次法規重構後欠缺細粒度單元測試，原 L2（`SimulationParameterResolver` 優先權解析欠缺測試）僅為其中一環。為免在法規頻繁變動期增加維護成本，定調於「所有法規修正全數完成定案之後」，全面補齊包含參數優先級、稅務/社保扣除、房貸雙策略、NISA/DC 帳戶投影、最佳化求解器等全系統核心業務邏輯之無死角單元測試防護網。

### Plan 18 — Finance Extractor Chrome Extension 跨站點金融與薪資資料抽取擴充計畫
- **來源**：家庭成員資料同步與多使用者落地需求（原 Backlog B4 升級）
- **文件**：[Plan-18-chrome-extension-data-extractor.md](Plan-18-chrome-extension-data-extractor.md)、子題[Plan-18-01](Plan-18-01-extension-preview-reconcile-ui.md)（Extension重新匯入時的預覽/逐欄位確認機制）
- **狀態**：PLANNED
- **簡述**：配合 Plan 02/03 多使用者與家族財務模擬，解決無法要求非技術家人手動另存 HTML/CSV 並上傳至 Docker 容器的極差使用者體驗問題，同時根本解決延遲載入快照不完整隱患。以 Manifest V3 打造高層次模組化 Extractor 介面，在瀏覽即時渲染頁面時一鍵透過 JWT API 安全同步後端；詳細支援站點清單留待啟動時動態盤點定案。Plan-18-01補充：重新匯入時用「DB現有值 vs 頁面最新值」逐欄位比對預覽，讓使用者決定保留舊值或採用新值，解決像保單`policyName`/`annualPremium`/`endDate`這類同一欄位兩種來源都能寫的混合案例，不需要後端自己判斷。

### Plan 19 — 個別項目隱私層級寫入支援與前端設定 UI
- **來源**：Plan 02 遺留之設計與技術債（Item-level Privacy Write Support）
- **文件**：[Plan-19-item-level-privacy-level-write-support.md](Plan-19-item-level-privacy-level-write-support.md)
- **狀態**：PLANNED
- **簡述**：補齊所有領域（銀行/信用卡交易、購物、薪資、年金、NISA、DC等）Controller Request DTO 與 Service Command 缺乏 `privacyLevel` 參數之缺陷，並於前端交易與帳戶編輯對話框新增「完全公開 / 僅計入總額 / 私密隱藏」下拉選單，讓使用者能手動調整個別私房花費之隱私層級。

### Plan 20 — 支援中古住宅（既存住宅）之房貸稅制借入限度額與扣除期間模型
- **來源**：question-123 房貸稅制法規審查與國稅廳 No.1214 規範
- **文件**：[Plan-20-mortgage-used-housing-support.md](Plan-20-mortgage-used-housing-support.md)
- **狀態**：PLANNED
- **簡述**：依日本國稅廳 No.1214 規範，中古住宅（既存住宅）法定扣除期間為 10 年，借入限度額（認定住宅 3,000 萬、一般住宅 2,000 萬）與新築住宅不同；重構 `MortgageTaxDeductionStrategy.calculateMaxAmount` 接收 `PropertyType` 以正確套用中古住宅稅制模型。

### Plan 21 — 模擬引擎月級別時間精度（住民稅前年所得課稅 + 現金流年中波動偵測），open狀態，等未來評估雙引擎架構
- **來源**：Plan-16 Review（原issue-138，住民稅前年所得）往外盤點發現同一限制同時存在於基礎控除、配偶特別控除（question-120）、扶養親屬控除（question-119/121）三個模組；另外併入原question-133（現金流年中波動偵測，源自issue-106討論延伸），兩者都指向「模擬引擎只有年、沒有月」這同一個底層架構缺口
- **文件**：[Plan-21-inhabitant-tax-prior-year-income-engine.md](Plan-21-inhabitant-tax-prior-year-income-engine.md)、子題[Plan-21-02](Plan-21-02-basic-deduction-prior-year-income.md)（基礎控除）。原子題Plan-21-01（配偶特別控除）內容混入了跟月級別無關的獨立問題，已刪除並改記錄在[question-144](../pending%20fix/question-144.md)
- **狀態**：OPEN（不是待實作任務，也不是封存定案；保留完整技術分析，等未來評估「年單位快速模擬＋月單位詳細模擬」雙引擎架構時回頭參考）
- **簡述**：日本住民稅是用「前一年所得」課「今年的稅」，且每年6月才切換新稅額；同時，破產偵測跟GA打分也都只看年底結算數字，看不到年中現金一度見底又補回正數的情況。這兩個需求的解法本質相同——系統目前完全沒有「前一年所得」這個狀態，也沒有月份這一層。已評估「前年所得」對30~40年總現金流的影響大機率量不出實質差異，且修正需要把模擬引擎從年級別重寫成月級別，對GA/Grid Search最佳化引擎的效能衝擊過大（運算量暴增12倍），單一引擎內個案修補的投資報酬率過低；保留為open，供未來若評估「雙引擎架構」（年單位引擎維持現狀、另建月單位詳細引擎）時作為技術分析起點。

### Plan 22 — E-Trade 股票匯入依股票代號限定刪除與多公司持股隔離架構計畫
- **來源**：Question 158 審查討論（原持股與獎酬歷史無條件全域 `deleteAll()` 覆蓋問題）
- **文件**：[Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md](Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md)
- **狀態**：PLANNED（防禦型架構升級）
- **簡述**：現行 E-Trade 在庫持股與獎酬歷史匯入時採用全表 `deleteAll()`，若使用者曾任職不同雇主並留有庫存（如前公司 `GOOG` 與新公司 `NVDA` 並存），分次匯入新公司檔案會無聲抹除前公司在庫持股。重構為依上傳檔案包含之「股票代號（Symbol）」進行 Scope-based 局部覆寫（`deleteByOwnerUserIdAndSymbolIn`），在剔除已出售批次維持庫存正確性的同時，確保跨公司持股與多租戶安全隔離。

### Plan 23 — 銀行交易新增「帳號」欄位，解決同一銀行多帳戶（本人／配偶／小孩）互相覆蓋的問題
- **來源**：Source Code Review（2026-10-13）發現——`BankTransaction`/`BankStatement`domain實體從頭到尾沒有帳號欄位，`bankName`同時身兼「哪家銀行」跟「哪個帳戶」兩個角色
- **文件**：[Plan-23-bank-account-identifier-for-multi-account-same-bank.md](Plan-23-bank-account-identifier-for-multi-account-same-bank.md)
- **狀態**：PLANNED（使用者已拍板要做，細節待規劃）
- **簡述**：目前銀行餘額彙總邏輯依賴`bankName`字串當唯一身分，一旦本人、配偶、小孩在同一家銀行各自開戶，系統只會抓到其中一個帳戶的最新餘額，其他帳戶的餘額會從模擬起始淨資產裡完全消失。需要擴充domain新增帳號欄位，兩個待確認的設計細節：帳號是否需要搭配分行才算真正唯一識別、以及如何跟尚未落地的配偶Link模式（Plan-03）歸戶串接；兩支現有銀行解析器目前皆無帳號擷取邏輯，待取得真實頁面樣本後再設計擷取方式。

### Plan 24 — Grid Search最佳化引擎的NISA成長型/定期定額兩個鎖定旗標，搜尋迴圈目前沒有分別檢查（潛在死碼防範，性質同Plan-09）
- **來源**：Source Code Review（2026-10-13）發現
- **文件**：[Plan-24-grid-search-nisa-lock-flags-independent-check-missing.md](Plan-24-grid-search-nisa-lock-flags-independent-check-missing.md)
- **狀態**：PLANNED（潛在死碼防範，目前不是會實際發生的bug）
- **簡述**：`Chromosome`裡`nisaGrowthTargetLocked`跟`nisaTsumitateTargetLocked`是兩個獨立旗標，架構上允許分別鎖定；但`GridSearchEngine`的NISA搜尋迴圈沒有分別檢查這兩個旗標，目前因為唯一呼叫端把兩者綁在一起設成同值才沒出事。GA引擎（`Chromosome.mutate()`）對同樣兩個欄位的處理是正確、各自獨立檢查的，只有Grid Search這一側漏掉；一旦未來讓使用者可以分別鎖定這兩個NISA維度，Grid Search會悄悄忽略使用者明確鎖定的那一維。

### Plan 25 — 移除「背景排程自動掃描匯入」機制（TaskScheduler定時觸發），避免之後有人誤以為這是活著的功能、重新投入修正成本
- **來源**：使用者確認PM很久以前已決定移除這套定時排程機制（既有紀錄：`Fixed-81.md`、`WONTFIX-18.md`、`Plan-18`皆佐證此方向），這次Source Code Review審查年金/薪資服務層時發現這條路徑的`ownerUserId`為null存檔失敗bug，使用者指出這個早已決定要移除的功能「怎麼擅自被復活」才促成這份Plan
- **文件**：[Plan-25-remove-scheduled-auto-import-mechanism.md](Plan-25-remove-scheduled-auto-import-mechanism.md)
- **狀態**：PLANNED（移除範圍已界定，待排入時程）
- **簡述**：只移除「定時自動排程」這一層（`JobSchedulerService`/`JobSchedulerServiceImpl`/`SchedulerController`後端三支程式碼、`application.properties`裡的`auto-start-import-scheduler`/`cron-expression`設定、前端`use-finance-tools.ts`的排程開關UI），不動「手動觸發掃描」（`ImportTaskController`/`TransactionSourceTaskController`，issue-140已記錄這條路徑會繼續保留使用）。移除後，排程Job缺少HTTP context導致`ownerUserId`為null的bug（原issue-184）隨之自然消失，不需要另外修正。

### Plan 26 — 新增「清空所有分類」的endpoint，一次性刪除`TransactionCategory`整張表，連帶把所有引用它的交易/項目的`category_id`一併設成null
- **來源**：核對[issue-180](../pending%20fix/issue-180.md)系列（job import merge邏輯）銀行/信用卡/訂單項目三處merge方法、確認分類關聯性不受merge邏輯衝擊時，衍生出的新功能討論
- **文件**：[Plan-26-clear-all-categories-endpoint.md](Plan-26-clear-all-categories-endpoint.md)
- **狀態**：PLANNED（不急，兩個落地方向已列出優缺點，待使用者拍板）
- **簡述**：查證確認`category_id`外鍵（`BankTransaction`/`CreditCardTransaction`/`BaseOrderItem`三處皆指向`TransactionCategory`）都沒有指定`onDelete`行為，直接刪除被引用的分類會直接丟外鍵例外；現有單筆`deleteCategory`端點也沒有處理這個問題，是一個獨立的既有缺口。這份Plan列出兩個落地方向：方向A是Service層依序批次update三個表的`category_id`為null、再刪除分類（優點是邏輯顯式寫在應用層，缺點是每多一個引用分類的新實體就要手動維護這個清單）；方向B是資料庫外鍵加上`ON DELETE SET NULL`、交給資料庫自動處理（優點是同時解決單筆刪除的既有缺口、不需要應用層額外程式碼，缺點是屬於隱性行為、排查問題時較難一眼看出）。兩個方向可以疊加，不互斥。

### Plan 28 — 住民稅6月切換輕量引擎，不需要Plan-21那種月級別模擬引擎大改
- **來源**：191（退休當年稅務分段計算）review過程中，討論`PreviousYearTaxRecord`裡「前一年所得」欄位目前無人讀取時，使用者提出的輕量方案；切換月份是6月這點經使用者查證確認（住民稅依前一年所得課徵、地方政府約5月完成核定、6月開始適用新稅額，不分特別徵收/普通徵收），不是沿用Plan-21原文未附來源的斷言
- **文件**：[Plan-28-inhabitant-tax-june-switch-lightweight-engine.md](Plan-28-inhabitant-tax-june-switch-lightweight-engine.md)
- **狀態**：PLANNED（技術方案已查證確認可行，待排入時程；跟191無人讀取的三個舊欄位無關，那三個欄位已確認直接移除見[issue-192](../pending%20fix/issue-192.md)，不是定義累加規則後保留）
- **簡述**：Plan-21判斷「住民稅前年/去年所得課稅、6月切換」需要模擬引擎重寫成月級別才能解決，前提是「系統完全沒有歷史所得這個狀態」——這份Plan指出，191落地後`PreviousYearTaxRecord`這個跨年記錄已經在正確傳遞，缺的只是兩個住民稅相關欄位，不是整套機制。真實時序是：某一年1-5月用的是「去年6月已經算好的稅額」（直接繼承，不重算），6-12月用的是「用去年所得新算出來的稅額」（每年只需要新呼叫一次既有的`calculateInhabitantTax`純函數）——兩者依5/12與7/12比例混合成整年總額，年末把新算出的稅額跟今年所得往下傳給明年。起算年前半段不需要靠這套公式猜，`applyYearEndAdjustment`既有機制本來就會拿真實YTD住民稅去跟這個理論總額比較修正。這個方案同時讓Plan-21-02（基礎控除）、question-120（配偶特別控除）、question-119/121（扶養親屬控除）這幾個子題描述的現象一併被正確處理，不需要分開修。Plan-21需求二（現金流年中波動偵測，性質是逐月追蹤、沒有這種輕量解法）不受影響，維持open留在Plan-21。

### Plan 29 — 模擬沙盒新增「假設DC/iDeCo每月總提繳額變動」情境，用parser已經抓到的真實配分比例分配到各基金
- **來源**：[issue-189](../pending%20fix/issue-189.md)（DC/iDeCo配分比例`allocationPercentage`查證）衍生——查證「這個欄位現在有沒有用」之後，進一步被問「將來有沒有可能用到」，發現模擬引擎已有概念相同、服務對象是NISA的既有功能（`testNisaGrowthMonthlyContribution`等），DC/iDeCo目前沒有對應版本
- **文件**：[Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md](Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)
- **狀態**：PLANNED（跟issue-189「要不要開放編輯DB」是獨立的兩件事，這份不碰真實資料、風險較低，可以獨立推進）
- **簡述**：`FundOverrideApplier.applyGlobalContribution`現有NISA沙盒情境，因為NISA網頁沒有配分比例欄位，只能從現有金額反推比例分配「假設總提繳額變成X元」的情境。DC/iDeCo網頁實際上有這個比例（parser已經從`掛金の配分割合`頁面把它當真實事實抓下來，存在`allocationPercentage`欄位裡），只是目前模擬引擎完全沒用到。這份Plan提議讓`SimulationOverrides`新增`testDcMonthlyContribution`/`testIdecoMonthlyContribution`兩個欄位，比照NISA現有模式新增對應的分配邏輯，但改用資料庫裡的真實比例分配、不用反推——比反推更準確，因為反映的是使用者真正設定的分配方式。`SimulationFund`目前的欄位清單裡沒有容納配分比例的欄位，需要新增一個欄位並在`DefinedContributionContextBuilder`建構時帶入。沒有傳入覆寫值時，完全不影響現有行為（維持使用DB裡原有的`monthlyContributionAmount`），呼應「預設沒有覆蓋就是採用DB的百分比,不手動去算」的原則。

### Plan 30 — 未滿60歲提早退休（FIRE）支援自主繼續提繳iDeCo，利用小規模企業共済等掛金控除沖銷股票配當所得
- **來源**：討論[issue-193](../pending%20fix/issue-193.md)（退休當年iDeCo提繳被整年錯誤歸零）時衍生的進階功能需求——使用者指出沒薪水的FIRE族依然可以提繳iDeCo沖銷股息所得節稅，跟193的bug修復解耦、獨立立案
- **文件**：[Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md](Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md)
- **狀態**：PLANNED（跟issue-193「退休即停繳」的基準行為是獨立的擴充功能，不互相等待）
- **簡述**：日本居民未滿60歲提早退休後自動轉為國民年金第1號被保險人，依法可以持續提繳iDeCo到60歲，額度上限還會提高到每月6.8萬日圓，提繳款由個人銀行帳戶扣款、跟原雇主脫鉤——現行模擬引擎為了簡化，退休即停止投入新資金這個基準假設，讓這群高資產FIRE族群用iDeCo掛金控除沖銷股息所得的節稅效益完全無法被模擬。issue-193確認採用「退休即停繳」當基準行為，這份進階情境獨立交給Plan-30處理，需要新增一個使用者自己決定退休後要不要繼續扣、扣多少的全新輸入通道。

### Plan 31 — 企業型DC的「公司提繳」跟「員工自己加碼matching」現狀互斥，不能同時並存、也不能單獨關掉其中一半
- **來源**：查證[issue-194](../pending%20fix/issue-194.md)（企業型DC退休當年提繳整年歸零）時，使用者主動提出的新問題——DC支不支援公司提繳跟員工matching同時算、公司提繳但員工不加碼這兩種情境
- **文件**：[Plan-31-dc-employer-employee-matching-contribution-not-separable.md](Plan-31-dc-employer-employee-matching-contribution-not-separable.md)
- **狀態**：PLANNED（查證確認現狀完全不支援，是架構上的互斥二選一，不是部分支援的小缺口）
- **簡述**：`DefinedContributionContextBuilder`對DC提繳的處理是`if (latestDcOpt.isPresent())` / `else if`互斥結構——已有真實DC帳戶快照時，提繳金額完全來自帳戶快照的合計總額，`DefinedContributionUserSettings.monthlyDcMatchingContribution`這個使用者自選加碼設定完全不會被讀取；沒有真實帳戶時才會用這個設定值，但建的是一個全新虛構帳戶，不是跟真實提繳疊加。這代表使用者想模擬「公司提繳繼續、我自己不跟著matching了」這種情境，現在完全沒有管道可以表達——即使parser本身有分開解析出雇主/員工兩塊提繳金額，這兩個數字在到達模擬引擎之前就已經被合併壓扁成一個總額，無法再拆開。

### Plan 32 — 隔年進現金的退稅和補稅，畫面上沒有來源說明
- **來源**：複核 [issue-190](../pending%20fix/issue-190.md) 時確認 190 的稅額 Tooltip 不用做，但隔年現金變動沒有說明這件事還在。191 已把它記成獨立功能。
- **文件**：[Plan-32-deferred-tax-cash-has-no-explanation.md](Plan-32-deferred-tax-cash-has-no-explanation.md)
- **狀態**：PLANNED（只留記錄，不是實作規格）
- **簡述**：年中退休的在職段稅差（`pendingWorkingSegmentTaxAdjustment`）和房貸確定申告退稅（`pendingKakuteiShinkokuRefund`）隔年只加進 `currentLiquidCash`。年度 API 沒有這兩筆金額，前端只看得到現金總額。兩筆來源不同，說明不能共用。不要改回 190 的稅額 Tooltip。

### Plan 33 — DC/iDeCo重新匯入時，基金組合跟使用者設定的目標配分比例對不上就自動清掉那組設定
- **來源**：[question-198](../pending%20fix/question-198.md)定案——189新增的`DcTargetAllocationSetting`只在使用者設定當下驗證過一次跟基金清單是否吻合，之後使用者如果在券商網站上真的換過投資標的，舊設定會悄悄變成陳舊、系統沒有任何機制偵測或回退，這是系統結構性限制（無法從舊組合猜出新標的該分配多少比例），不是bug
- **文件**：[Plan-33-dc-ideco-target-allocation-auto-invalidate-on-fund-lineup-change.md](Plan-33-dc-ideco-target-allocation-auto-invalidate-on-fund-lineup-change.md)
- **狀態**：PLANNED（198已指出三個方向不代為決定，這份Plan採用198建議的方向1；要不要現在就做，還是等Plan-29消費端真正落地才處理，留給使用者決定）
- **簡述**：兩條既有匯入路徑（瀏覽器擴充功能的`saveSnapshot`、檔案掃描的`execute`/`processFile`）完成匯入後，各自比對這次匯入後的`fundCode`集合跟使用者設定`allocations`的key集合是否完全相等，不相等就整組清掉（新增`invalidateTargetAllocationIfFundLineupChanged`方法），不嘗試部分保留。檔案掃描路徑只在`pageType == ALLOCATION`時才需要檢查，`MEMBER_INFO`/`STATUS`分頁不會改變基金組合。讓Plan-29的讀取邏輯可以簡化成「存在就用、不存在就fallback事實欄位」，不需要在讀取端重新驗證。

---

## 快速查表

| 項目 | 實作狀態 | 完成日期 | Claude Review | plan.md 對應 | 對應任務 / 文件 |
|---|:---:|:---:|:---:|:---:|---|
| Plan 01 | ✅ 已完成 | 2026-10-07 | 已Review | 有 | Phase 2 / 教育模型重構（[Plan-01.md](Plan-01.md)） |
| Plan 02 | ✅ 已完成 | 2026-10-07 | 待 Review | 有 | Phase 1 / 多使用者架構（[Plan-02-多使用者架構、家族共享與隱私控制.md](Plan-02-多使用者架構、家族共享與隱私控制.md)） |
| Plan 03 | ⏳ 待處理 | - | - | 有 | Phase 2 / Task 1（[Plan-03-家族財務服務與模擬器整合.md](Plan-03-家族財務服務與模擬器整合.md)，涵蓋 H7 / H18） |
| Plan 04 | ⏳ 待處理 | - | - | 有 | Backlog / B1（[Plan-04-grid-search-multi-mortgage-dimension.md](Plan-04-grid-search-multi-mortgage-dimension.md)） |
| Plan 05 | ⏳ 待處理 | - | - | 無 | —（[Plan-05-mortgage-child-rearing-support.md](Plan-05-mortgage-child-rearing-support.md)） |
| Plan 06 | ⏸️ 暫緩 | - | - | 無 | —（[Plan-06.md](Plan-06.md)，同 issue-06） |
| Plan 07 | ⏸️ 暫緩 | - | - | 無 | —（[Plan-07.md](Plan-07.md)） |
| Plan 08 | ⏸️ 暫緩 | - | - | 有 | Backlog / B5（[Plan-08-no-statutory-ideco-dc-contribution-limit.md](Plan-08-no-statutory-ideco-dc-contribution-limit.md)，同 H16 / FINDING-31） |
| Plan 09 | ⏳ 待處理 | - | - | 有 | Phase 3 / Task 3（[Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md](Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)，FINDING-37 警告） |
| Plan 10 | ⏳ 待處理 | - | - | 無 | —（[Plan-10-dead-findBestMortgageEarlyRepaymentYear-degenerate-loop.md](Plan-10-dead-findBestMortgageEarlyRepaymentYear-degenerate-loop.md)） |
| Plan 11 | ⏸️ 暫緩 | - | - | 無 | —（[Plan-11-individual-pension-insurance-premium-not-deducted.md](Plan-11-individual-pension-insurance-premium-not-deducted.md)，同 FINDING-45） |
| Plan 12 | ⏳ 待處理 | - | - | 有 | Backlog / B1 延伸（[Plan-12.md](Plan-12.md)） |
| Plan 13 | ⏳ 待處理 | - | - | 有 | [Plan-13-early-childhood-entrance-age-months.md](Plan-13-early-childhood-entrance-age-months.md) |
| Plan 14 | ⏳ 待處理 | - | - | 有 | [Plan-14-education-config-command-decoupling.md](Plan-14-education-config-command-decoupling.md) |
| Plan 15 | ⏳ 待處理 | - | - | 有 | [Plan-15-etrade-stock-lot-endpoints-and-ui.md](Plan-15-etrade-stock-lot-endpoints-and-ui.md) |
| Plan 16 | ✅ 已完成 | 2026-10-09 | 已Review | 有 | [Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md](Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md) |
| Plan 17 | ⏳ 待處理 | - | - | 無 | —（[Plan-17-system-wide-unit-test-coverage.md](Plan-17-system-wide-unit-test-coverage.md)，涵蓋原 L2） |
| Plan 18 | ⏳ 待處理 | - | - | 有 | Backlog / B4（[Plan-18-chrome-extension-data-extractor.md](Plan-18-chrome-extension-data-extractor.md)） |
| Plan 19 | ⏳ 待處理 | - | - | 有 | Plan-02 技術債（[Plan-19-item-level-privacy-level-write-support.md](Plan-19-item-level-privacy-level-write-support.md)） |
| Plan 20 | ⏳ 待處理 | - | - | 無 | —（[Plan-20-mortgage-used-housing-support.md](Plan-20-mortgage-used-housing-support.md)） |
| Plan 21 | 🔓 Open（等雙引擎架構評估） | - | - | 無 | —（[Plan-21-inhabitant-tax-prior-year-income-engine.md](Plan-21-inhabitant-tax-prior-year-income-engine.md)，原issue-138 + question-133） |
| Plan 22 | ⏳ 待處理 | - | - | 無 | —（[Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md](Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md)） |
| Plan 23 | ⏳ 待處理 | - | - | 無 | —（[Plan-23-bank-account-identifier-for-multi-account-same-bank.md](Plan-23-bank-account-identifier-for-multi-account-same-bank.md)） |
| Plan 24 | 🔒 潛在死碼防範 | - | - | 無 | —（[Plan-24-grid-search-nisa-lock-flags-independent-check-missing.md](Plan-24-grid-search-nisa-lock-flags-independent-check-missing.md)） |
| Plan 25 | ⏳ 待排入時程 | - | - | 無 | —（[Plan-25-remove-scheduled-auto-import-mechanism.md](Plan-25-remove-scheduled-auto-import-mechanism.md)，原issue-184） |
| Plan 26 | ⏳ 待處理（不急，待拍板） | - | - | 無 | —（[Plan-26-clear-all-categories-endpoint.md](Plan-26-clear-all-categories-endpoint.md)） |
| Plan 27 | ⏳ 待處理 | - | - | 無 | —（[Plan-27-system-wide-manual-editing-ui.md](Plan-27-system-wide-manual-editing-ui.md)，源自issue-183延伸決定） |
| Plan 28 | ⏳ 待處理 | - | - | 無 | —（[Plan-28-inhabitant-tax-june-switch-lightweight-engine.md](Plan-28-inhabitant-tax-june-switch-lightweight-engine.md)，源自191 review延伸決定） |
| Plan 29 | ⏳ 待處理 | - | - | 無 | —（[Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md](Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)，源自issue-189延伸決定） |
| Plan 30 | ⏳ 待處理 | - | - | 無 | —（[Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md](Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md)，源自issue-193延伸決定） |
| Plan 31 | ⏳ 待處理 | - | - | 無 | —（[Plan-31-dc-employer-employee-matching-contribution-not-separable.md](Plan-31-dc-employer-employee-matching-contribution-not-separable.md)，源自issue-194延伸決定） |
| Plan 32 | ⏳ 待處理 | - | - | 無 | —（[Plan-32-deferred-tax-cash-has-no-explanation.md](Plan-32-deferred-tax-cash-has-no-explanation.md)，源自issue-190複核；plan.md 未列） |
| Plan 33 | ⏳ 待處理 | - | - | 無 | —（[Plan-33-dc-ideco-target-allocation-auto-invalidate-on-fund-lineup-change.md](Plan-33-dc-ideco-target-allocation-auto-invalidate-on-fund-lineup-change.md)，源自question-198定案；plan.md 未列） |







