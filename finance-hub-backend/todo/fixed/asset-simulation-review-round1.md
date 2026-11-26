# Finance-Hub 資產模擬（Asset Simulation）商業邏輯 Review 報告

**Review 對象**：`finance-hub-backend`（模擬引擎、優化器、稅務計算）與 `hub-ui`（模擬前端表單、Solver 對話框）
**方法**：三種獨立視角對抗式驗證，僅收錄三方一致確認為真實且重要的發現
**日期**：2026-08-06

---

## 1. 執行摘要

資產模擬引擎在**單一收入者、單一貨幣、單一稽核路徑**的簡單情境下架構完整（NISA/iDeCo 累進上限、房貸攤還、退休金乘數等核心公式均已實作），但一旦使用者的實際情況偏離這個「理想單身受薪者」假設——持有外幣資產、有配偶或第二收入來源、持有應稅股票、想模擬「不提前還款」等反事實情境——邏輯就會系統性地產出**看似合理但實際錯誤**的結果，且多數錯誤路徑完全沒有測試覆蓋，屬於靜默失效（silent failure）而非顯性錯誤。最嚴重的三類風險依序為：（1）**幣別/匯率完全未處理**造成美股帳戶價值低估近兩個位數；（2）**優化器（Solver）的反事實情境未被真正模擬**（「永不提前還款」等於「退休時還款」），導致提前還款/繼續投資的決策建議在邏輯上是錯的；（3）**稅務優惠帳戶法定上限（iDeCo/DC 月繳）與強制性社會保險費（國民健康保險）完全未建模**，使早退休橋接期與高額提繳情境的現金流被系統性低估/高估。整體而言，模擬引擎的「單一情境試算」可信度尚可，但「情境比較」與「邊界輸入防護」兩個維度存在結構性缺口，建議在對外提供財務建議或自動化決策前優先修復。

---

## 2. 完整發現清單（依嚴重度排序）

### 🔴 High（18 項）

---

**H1. [FIXED] 公司股票餘額未套用幣別與匯率換算**
- **位置**：`AssetSimulationEngineImpl.java:464-466, 293-294`
- **問題**：`initializeContext()` 直接讀取 `ETradeAccountSnapshot.currentAccountValue` 加總進淨資產，完全未檢查 `currency` 欄位、未套用 `exchangeRate` 欄位——這兩個欄位存在的目的正是為了標記數值是 USD 還是 JPY。
- **為何是商業邏輯考慮不周**：`ETradeBenefitHistoryHtmlParserImpl.extractCurrency()` 會在解析到以 `$` 開頭的「Total Account Value」文字時設定 `currency="USD"` 並保留原始美元金額——這是真實 E*TRADE 對帳單常見的呈現方式。模擬引擎把所有非美元項目（現金、NISA、DC）都當作日圓加總，若公司股票是美元計價卻不轉換，等於把 $50,000 當成 ¥50,000，而不是約 ¥7,500,000（以 150 日圓/美元估算）。
- **情境**：任何員工的 E*TRADE 對帳單被以美元格式解析並存入資料庫，其淨資產推算會低估約兩個位數。
- **建議修正**：在加總前檢查 `currency`，若為 USD 則乘以 `exchangeRate`（或即時匯率）換算為 JPY 後再納入 `currentNetWorth`。

---

**H2. [FIXED] Solver 覆寫的報酬率被逐基金設定的儲存值靜默忽略**
- **位置**：`InvestmentReturnCalculator.java:44-61`（DC/iDeCo 於 64-84 鏡像同樣問題）
- **問題**：`SimulationOptimizerService.calculateNisaGoal` 透過複製 config 並設定 `clonedConfig.setNisaExpectedReturnRate(0.03)` 來測試保守情境，但 `calculateWeightedReturnRates` 中 `double rate = settingRate != null ? settingRate : globalNisaRate;`——只有「該基金沒有記錄任何報酬率」時才會用到覆寫值；只要使用者曾對任一基金設定過報酬率（即使是舊的、樂觀的假設），覆寫就完全不起作用。
- **為何是商業邏輯考慮不周**：NISA 帳戶的核心價值在於免稅複利成長，「情境比較」（樂觀 vs 保守報酬假設）是使用者評估提繳金額是否足夠退休的關鍵工具。若覆寫值形同虛設，使用者以為自己在測試 3% 保守情境，實際上引擎仍在用舊有的 7% 假設計算，等於**用錯誤的風險假設得出結論**。
- **情境**：使用者為每支基金記錄過去的預期報酬率（如 0.07），呼叫「保守情境下每月需提繳多少 NISA」的二分搜尋 API，得到的建議金額實際上是以 7% 報酬率算出，嚴重低估真正保守情境下所需的月繳金額。
- **建議修正**：讓 override 具有比逐基金設定更高的優先權，或明確區分「全域預設值」與「情境測試覆寫」兩種語義，並在 API 回應中標示實際採用的報酬率供前端顯示。

---

**H3. [WONTFIX] 公司股票餘額在整個模擬週期內完全不增值（僅能被動減少）**
- **位置**：`AssetSimulationEngineImpl.java:141-321`（主迴圈）+ `LiquidationSimulationService.java:27-37`
- **問題**：`context.companyStockBalance` 初始化後，在整個模擬主迴圈中沒有任何地方對其套用投資報酬率成長；唯一會改變它的地方是 `LiquidationSimulationService` 中應付現金短缺時的**只減不增**清算路徑。
- **為何是商業邏輯考慮不周**：與 NISA/DC 不同，公司股票（RSU/ESPP）是實際會隨市場波動、公司成長而增值的資產類別，模擬引擎卻讓它在數十年投影中維持絕對常數，這在數學上等同於假設報酬率恆為 0%——沒有任何設定入口能讓使用者表達對這類資產的成長/衰退預期。
- **情境**：35 歲使用者持有 ¥10,000,000 公司股票，30 年投影到 65 歲，該項目在每一年的輸出中都精確等於 ¥10,000,000；同額度放入 NISA 以 5% 年化複利則成長至約 ¥43,000,000。持有公司股票的使用者，其總淨資產投影被系統性低估。
- **建議修正**：為公司股票新增可設定的預期報酬率（或至少提供「視為現金」vs「視為投資組合」的選項），並在主迴圈中套用複利成長。

> [!NOTE]
> **WONTFIX 理由**：實務上，理財規劃通常建議將歸屬的 RSU 或 ESPP 盡快變現並轉入 NISA 等多元化投資組合，以避免單一公司風險。因此系統不鼓勵、也不特別模擬公司股票的長期複利成長，而是將其視為類似流動現金的短期停泊資產。而且基本上你無法預測股票是否能成長，依賴單股會導致模擬預測失真造成財務風險。

---

**H4. [FIXED] （合併：核心引擎 medium + 房貸元件 high）「永不提前還款」的反事實情境從未被真正模擬，實際上等同於「退休時還款」**
- **位置**：`SimulationOptimizerService.java:104-116`、`MortgageSimulationService.java:199-224`（`processEarlyRepaymentAtRetirement`）、`AssetSimulationEngineImpl.java:281-287`
- **問題**：`findBestMortgageEarlyRepaymentYear` 為建模「永不提前還款、改為繼續投資」的候選情境，會設定 `testYear = null` 並建構 `new SimulationOverrides(null, null, null, null)`。但引擎內部的判斷邏輯是 `shouldRepayEarly = (overrides != null && overrides.mortgageEarlyRepaymentYear() != null) ? ... : context.isRetired();`——當 `mortgageEarlyRepaymentYear()` 為 `null` 時，引擎並不會理解為「不還款」，而是落入 else 分支，在退休那一年**照樣**觸發整筆清償。
- **為何是商業邏輯考慮不周**：房貸提前還款 vs. 繼續投資是日本理財規劃中經典的槓桿決策——當房貸利率遠低於投資報酬率時（例如日本常見的 1% 房貸利率 vs 5%+ 投資報酬），理論最優解就是「永不提前還款」。但因為 `null` 被同時用來表示「沒有明確指定」與「明確指定為不還款」兩種語義，優化器**永遠**無法真正模擬出這個候選項，因此永遠不可能推薦「不要提前還款」，即使數學上這才是最優解。
- **情境**：房貸自然到期年齡 85 歲、退休年齡 65 歲，優化器比較各個提前還款年齡與「永不還款」基準線，但「永不還款」的模擬結果與「65 歲還款」幾乎完全相同（因為兩者實際上都在 65 歲觸發清償），導致優化器給出邏輯錯誤但看似合理的「最佳還款年齡」建議。
- **建議修正**：為 `SimulationOverrides` 增加明確的三態語義（例如 enum：`USE_DEFAULT` / `NEVER_REPAY` / `REPAY_AT_YEAR(int)`），避免用 `null` 同時承載兩種互斥意圖。

---

**H5. [PLANNED] 固定與浮動利率房貸全程套用單一常數利率，無利率重設或再融資機制**
- **位置**：`MortgageSimulationService.java:31, 45, 104, 125, 152`（`interestRate` 為單一常數使用）；`MortgageRecord.rateType`（`InterestRateType`）在整個 simulation package 從未被讀取；`grep -r refinanc` 全專案無結果
- **問題**：無論房貸標記為 `FIXED` 或 `VARIABLE`，攤還計算都用今天記錄的單一利率跑完 20-35 年的整個投影期，沒有固定期滿後的利率重設事件、沒有浮動利率的隨機/情境化變動，也完全沒有再融資的程式路徑。
- **為何是商業邏輯考慮不周**：日本房貸市場常見「固定 10 年後轉浮動」或純浮動利率產品，利率風險是房貸決策（尤其是「提前還款 vs 繼續投資」比較）中的關鍵變數；把利率視為 30 年不變的常數，等同於完全消除利率風險這個決策維度。
- **情境**：使用者的房貸為 10 年固定後轉浮動，30 年投影全程鎖定今天的固定期利率，任何「固定期滿後利率上升」的情境都無法被表達，系統性低估浮動利率借款人的利率風險，也讓「提前還款 vs 繼續投資」的比較建立在錯誤的利率假設上。
- **建議修正**：至少支援固定期滿後依 `rateType` 觸發利率重設事件（可設定為使用者輸入的「預期到期利率」），中長期可考慮情境化/隨機利率模型。

---

**H6. [WONTFIX] 應稅帳戶（公司股票）清算完全未扣除資本利得稅**
- **位置**：`LiquidationSimulationService.java:27-37`（程式碼註解寫著「Assuming 0% capital gains per user preference」）；全專案 `grep` 無任何資本利得稅常數或邏輯
- **問題**：現金短缺時強制清算公司股票，模型將 100% 售股款項計入現金，但**沒有任何地方存在可設定的資本利得稅偏好**——這只是一句誤導性的程式碼註解，不是真實功能。日本股票資本利得稅實際稅率約為 20.315%（國稅+地方稅合計）。
- **為何是商業邏輯考慮不周**：NISA 帳戶本身確實免稅（這是其存在的稅務優惠意義），但公司股票（一般應稅券商/ESPP 持股）不是免稅帳戶，將兩者用同一套「0% 稅」邏輯處理，等於抹除了 NISA 相對於一般應稅帳戶最核心的稅務優勢比較基礎。
- **情境**：任何觸發清算的現金短缺年度，可用流動性都被高估約 20%；更嚴重的是，這個一致性錯誤會系統性偏袒「繼續投資應稅股票」優於「提前還款房貸」的優化器建議，因為應稅帳戶的真實稅務拖累在模型中完全消失。
- **建議修正**：導入資本利得稅常數（可先用日本現行 20.315% 作為預設，並區分帳戶類型：NISA 免稅、iDeCo 提領時另計、一般應稅帳戶按利得課稅），在 `LiquidationSimulationService` 清算應稅資產時扣除。

> [!NOTE]
> **WONTFIX 理由**：準確計算資本利得稅需要追蹤每一筆股票買入的歷史成本（Cost Basis）與未實現損益，這會讓模擬引擎與資料模型變得極度複雜。目前的系統設計方針是：假設使用者在輸入「公司股票餘額」時，提供的已經是「扣除預期稅額後的淨值」，以此來簡化清算模型。同時，課稅的部分是針對獲利的部分，無法預測股票是否獲利或賠損，就算內線交易也不可能準確保證。

---

**H7. [PLANNED] 資料模型無法表達多重且時間受限的收入來源（副業、雙薪家庭主收入者本人以外的收入）**
- **位置**：`IncomeExpenseCalculator.java:34`；`SimulationContext.currentAnnualSalary` 為單一 scalar；`CareerEvent`（`ABSOLUTE_OVERRIDE`/`RELATIVE_CHANGE`）只能覆寫這單一收入流
- **為何是商業邏輯考慮不周**：`LifeEvent` 只建模支出，從未建模收入；沒有 `IncomeSource` 這種具有自己起訖年齡的實體。真實理財規劃中「45-55 歲經營副業」「雙薪家庭」是常見情境，目前只能把第二收入硬塞進單一薪資軌道，與主薪資自身的成長率/生涯事件同年疊加時會互相覆寫而非加總。
- **情境**：使用者想模擬「45 歲開始經營副業到 55 歲結束」，只能透過與主薪資碰撞衝突的 `CareerEvent` 勉強近似，或完全無法建模，導致這類常見規劃場景被系統性忽略。
- **建議修正**：引入獨立的 `IncomeSource` 實體，具備自己的起訖年齡、成長率與稅務屬性，與主薪資分開累加。

---

**H8. [FIXED] `targetAge` 無驗證，小於現齡時靜默回傳「成功」的空陣列**
- **位置**：`AssetSimulationController.java:35`（`GET /simulation/asset-simulation/{targetAge}`）；`AssetSimulationEngineImpl.runSimulationInternalMem` 主迴圈 `for (int age = context.getCurrentAge(); age <= targetAge; age++)`
- **為何是商業邏輯考慮不周**：API 對外沒有任何輸入邊界防護，`targetAge < 現齡` 這種明顯無意義的請求會回傳 `HTTP 200 ApiResponse.success([])`，前端無法區分「這是真的沒有資料」還是「請求本身無效」。`pensionStartAgeOverride`/`targetRetirementAgeOverride` 之間也沒有順序性驗證（例如退休年齡早於現齡、養老金早於退休）。
- **情境**：前端誤傳 `targetAge=10`（現齡 30 歲），後端不報錯，直接回傳空陣列，前端可能誤判為「沒有可用資料」而非「輸入錯誤」，掩蓋真正的整合性問題。
- **建議修正**：在 Controller 層加入 `@Min` 與明確的跨欄位驗證（`targetAge >= currentAge`、`pensionStartAge >= retirementAge` 等），回傳 400 而非靜默的 200。

---

**H9. [FIXED] `/mortgage-early-repayment-year` 端點在 `targetAge` 過小時拋出未處理的 500**
- **位置**：`SimulationOptimizerService.java:82, 126`（`findBestMortgageEarlyRepaymentYear`）；`WebGlobalExceptionHandler` 未註冊 `IndexOutOfBoundsException`
- **為何是商業邏輯考慮不周**：與 H8 同源但表現形式不同——這裡因為 `simulation.get(simulation.size() - 1)` 對空 list 取值而直接拋出 `IndexOutOfBoundsException`，且該例外未被全域例外處理器捕捉，最終以未映射的 500 回應給前端，而非清楚的「無效目標年齡」400 錯誤。
- **情境**：`targetAge` 查詢參數只有 `defaultValue = "90"`、沒有 `@Min`，呼叫端傳入 `targetAge=0`（或任何 ≤ 現齡的值）即會導致伺服器端未捕捉異常。
- **建議修正**：與 H8 共用同一套輸入驗證機制，並在 `WebGlobalExceptionHandler` 中補上防禦性的兜底處理，避免內部實作細節（IndexOutOfBoundsException）直接洩漏為 500。

---

**H10. [DONE] `LiquidationSimulationService` 完全沒有單元測試覆蓋**
- **位置**：`LiquidationSimulationService.java:20-65`；無對應測試檔案；`AssetSimulationEngineImplTest` 也從未觸發負現金流分支
- **為何是商業邏輯考慮不周**：清算瀑布（賣股票 → 賣 NISA → 以 `NEGATIVE_CASH_BORROWING_RATE` 借款）以及跨年度的 NISA 本金回補簿記，是模擬引擎中最複雜、最容易出錯的分支之一，卻是完全的測試盲區——H1、H6 等發現之所以能存在多年未被發現，正是因為這條路徑從未被任何測試驅動過。
- **情境**：假設 companyStockBalance=500,000、nisaBalance=2,000,000、accumulatedNisaPrincipal=1,500,000，某年現金缺口達 3,000,000：預期股票全數賣出（500k）、NISA 賣出 2,000,000（依比例回補約 1,500,000 本金額度）、剩餘 500,000 缺口轉為借款並計息。若清算順序、本金比例計算或利息四捨五入邏輯有誤，目前不會有任何測試失敗。
- **建議修正**：補齊涵蓋「多層清算」「跨年度本金回補」「借款計息」邊界案例的單元測試。

---

**H11. [DONE] `MortgageSimulationService` 完全沒有端到端測試覆蓋**
- **位置**：`MortgageSimulationService.java:199-224`；`AssetSimulationEngineImplTest` 中 `mortgageRecordRepository.findAll()` 從未被 stub 出實際資料，Mockito 預設回傳空 list
- **為何是商業邏輯考慮不周**：整個房貸攤還、加碼還款、提前還款違約金、房貸稅務扣除、以及「退休時是否提前還款」的決策分支，因為測試中從未提供任何 `MortgageRecord`，等於這整條核心商業邏輯是**零測試覆蓋**狀態，H4、H5、H6 等房貸相關 bug 之所以存活，直接根源即在此。
- **情境**：退休使用者 currentLiquidCash=20,000,000、baselineAnnualExpenses=4,000,000（安全網=8,000,000）、房貸餘額=10,000,000。20M ≥ 10M+8M=18M，理應觸發提前還款、清零房貸並扣減流動現金。若安全網比較邏輯方向寫反，或還款後付款陣列未被清零（導致次年仍被誤扣款），目前沒有任何測試會發現。
- **建議修正**：補齊涵蓋攤還、加碼、違約金、稅務扣除、退休觸發還款的端到端測試矩陣。

---

**H12. [DONE] NISA 年度上限與終身上限完全未經測試驗證**
- **位置**：`AssetSimulationEngineImplTest.java:169-176` 對應 `AssetSimulationEngineImpl.java` 中 `TaxConstants.NISA_ANNUAL_LIMIT`/`NISA_LIFETIME_LIMIT` 的節流邏輯
- **為何是商業邏輯考慮不周**：NISA 的免稅額度上限是這個帳戶類型的法定核心規則（終身額度 1,800 萬日圓），若節流邏輯本身有 bug（比較錯常數、忘記 clamp、跨年重複計算本金），使用者會在數十年投影中被**靜默地**允許超額免稅提繳，產生過度樂觀的免稅成長投影，且沒有任何測試守護這條規則。
- **情境**：起始 NISA 本金 17,900,000（接近 18,000,000 上限），期望年繳 2,400,000：預期 `annualNisa` 應被鉗制到 100,000（剩餘額度），之後年度應變為 0。若鉗制邏輯有誤，目前無測試會捕捉到。
- **建議修正**：新增專門測試涵蓋「接近上限」「剛好觸頂」「跨年疊加」等邊界案例。

---

**H13. [FIXED] 生涯事件（Career Event）UI 收集月份精度，後端只讀取年份**
- **位置**：`event-timeline-manager.tsx:190`（`type="month"` 輸入）；`IncomeExpenseCalculator.java:48`：`if (event.getEventYearMonth().getYear() == context.getYear())`
- **為何是商業邏輯考慮不周**：UI 用月份選擇器暗示系統支援月級精度的加薪/調薪時間點，但後端判斷條件只比對年份，任何在年中生效的調整都會被視為「整年生效」，這是 UI 承諾與後端實作不一致的典型案例，且不會有任何錯誤提示——使用者以為模擬精確，實際上系統把 12 月生效的加薪當成 1 月生效一樣處理。
- **情境**：使用者新增 2027-12 生效的 `ABSOLUTE_OVERRIDE` 加薪事件，預期只有 12 月受影響，實際上整個 2027 年度的年薪/淨資產投影都被視為已加薪，產生比 UI 呈現的日期選擇明顯偏高的結果。
- **建議修正**：在 `IncomeExpenseCalculator` 中依月份按比例計算生效年度的加權薪資，或至少在 UI 明確標示「僅支援年度精度」。

---

**H14. [FIXED] 生活事件（Life Event）結束日期缺乏驗證，可能造成反序列化失敗或靜默零效果**
- **位置**：`event-timeline-manager.tsx:104-118`（`handleAddLife`）；`LifeEvent.endYearMonth` 為 `@NotNull`；`IncomeExpenseCalculator.calculateExpensesForYear` 迴圈條件（第 91 行）
- **為何是商業邏輯考慮不周**：前端必填檢查僅涵蓋 `startYearMonth`、`eventName`、`costPerOccurrence`，完全遺漏 `endYearMonth`。這造成兩種故障模式：(1) MONTHLY/YEARLY 頻率事件若未填結束日，會送出空字串觸發後端 `@NotNull` 反序列化失敗（不友善的錯誤訊息）；(2) 結束日期早於起始日期時請求會成功，但 `year >= eventStartYear && year <= eventEndYear` 永遠不成立，事件被儲存、顯示在時間軸上「看起來是啟用中的」，卻在每一年模擬中貢獻 ¥0——使用者完全不會被告知這個事件實際上是無效的。
- **情境**：使用者為週期性支出（如子女教育費）填錯結束日期順序，時間軸顯示正常，但淨資產投影完全沒有反映這筆支出，使用者可能因此誤信自己的財務規劃比實際更寬鬆。
- **建議修正**：前端必填檢查納入 `endYearMonth`，並加入「結束日期必須晚於起始日期」的即時驗證與視覺警示。

---

**H15. [FIXED] Solver 設定對話框的輸入數值完全沒有邊界檢查**
- **位置**：`solver-settings-dialog.tsx:88-122`；後端 `SolveNisaGoalRequest` 無 `@Min`/`@Max`
- **為何是商業邏輯考慮不周**：`safetyNetAmount` 可輸入負數，會讓二分搜尋的成功判定 `dto.getProjectedNetWorth() >= safetyNet` 變成恆真，導致 solver 回傳「每月只需提繳 ¥0」——這是一個對使用者而言看起來權威、實際上毫無意義的壓力測試結果。`inflationOverride`/`nisaReturnOverride` 同樣沒有上下限，輸入 1000（意圖打 1000%）或 -100（-100%）會直接餵入 `Math.pow(1.0 + inflationRate, yearsFromNow)`，在 60 年投影中可能溢位或產生荒謬結果，伺服器端無任何拒絕機制。
- **情境**：使用者手誤輸入負數安全網或誇大通膨率，得到看似專業但實則毫無參考價值的「建議每月提繳金額」，且系統不會提出任何警示。
- **建議修正**：前端加入合理範圍限制（例如通膨率 -5%~15%、報酬率 -20%~30%），後端 DTO 加上 `@Min`/`@Max` 作為第二層防護。

> **[Agent 補充註記 (Round 3 - 2026-08-29)]**
> **[WONTFIX (Deprecated)]**: 後端的 `@Min`/`@Max` 防護已如期實作並成功攔截。而前端 `nisa-goal-solver-card.tsx` 缺少 `<form>` 導致 `min`/`max` 失效的問題 (FINDING-27)，因為整個 Legacy Optimizer 模組即將被廢棄刪除 (同 FINDING-22)，因此不再針對即將刪除的前端元件投入修復成本。

---

**H16. [PLANNED] iDeCo/DC 月繳金額沒有任何法定上限檢查**
- **位置**：`InvestmentReturnCalculator.java:119-138`；相較之下 NISA 在 `AssetSimulationEngineImpl` 中明確透過 `TaxConstants.NISA_ANNUAL_LIMIT`/`NISA_LIFETIME_LIMIT` 進行節流
- **為何是商業邏輯考慮不周**：iDeCo（個人型確定提繳）在日本依職業身分有明確法定月繳上限（自營業者 68,000 日圓、有企業年金的受僱者 12,000 日圓等），這是稅務優惠帳戶制度設計的核心邊界，NISA 已經正確實作這種上限節流，但 iDeCo/DC 完全沒有對應機制。
- **情境**：使用者（或 solver 覆寫）設定月繳 100,000 日圓，遠超自營業者 68,000 或企業員工 12,000 的法定上限，模擬引擎仍照常複利成長並計算稅務扣除，`annualIdecoContribution` 直接餵入 `ActiveTaxCalculationEngine.calculate` 的退休扣除額計算，同時高估 DC/iDeCo 餘額與所得稅節省效果。
- **建議修正**：依使用者職業身分/企業年金狀態導入 iDeCo/DC 月繳法定上限常數，並與 NISA 的節流邏輯採用一致的架構。

---

**H17. [FIXED] 早退休橋接期完全未建模國民健康保險（Kokumin Kenko Hoken）保費**
- **位置**：`AssetSimulationEngineImpl.java:226-250`
- **為何是商業邏輯考慮不周**：日本退休後在領取厚生年金前的「橋接期」，強制性社會保險費除了國民年金（Kokumin Nenkin）保費外，還有國民健康保險保費——這對持有資產的早退休者往往是比國民年金保費更大的支出項目（常見 30 萬至 60 萬日圓/年以上，且持續到 74 歲），目前引擎僅計入國民年金保費，60-64 歲期間甚至完全不計任何社會保險費用。
- **情境**：使用者 55 歲退休、65 歲開始領取年金，55-59 歲僅被扣約 203,760 日圓/年（國民年金），60-64 歲甚至完全不扣任何社會保險費，實際上國民健康保險保費在整個橋接期都持續適用，退休現金流與所需退休金規模因此被系統性低估。
- **建議修正**：新增國民健康保險保費計算（可依所得級距簡化建模），適用於退休至 74 歲之間所有未受僱年度。

---

**H18. [PLANNED] 配偶/家屬自身的收入、養老金累積與資產完全未納入淨資產投影**
- **位置**：`IncomeExpenseCalculator.java`（無對應模型）；`SpouseDeductionEngine`/`DependentDeductionEngine` 僅影響稅務計算
- **為何是商業邏輯考慮不周**：目前配偶/家屬（`FamilyMember`）唯一的作用是提供稅務扣除加成（視配偶為低收入/無收入受扶養者），但若配偶本身有獨立收入並累積自己的厚生年金，這部分收入流、儲蓄與未來年金福利完全從淨資產投影中消失——對雙薪家庭而言，這不是精度問題而是**遺漏了半個家庭的財務狀況**。
- **情境**：雙薪家庭配偶年收入 5,000,000 日圓並累積自己的厚生年金，系統仍按「低收入受扶養配偶」給予稅務扣除加成，但配偶的所得、儲蓄與未來年金福利完全未被計入家庭淨資產走勢，導致投影只反映主要收入者一人的財務狀況。
- **建議修正**：擴充資料模型以獨立追蹤配偶的收入、儲蓄與年金累積，並將其正確加總進家庭層級的淨資產（同時仍保留現有的稅務扣除邏輯）。

---

### 🟡 Medium（9 項）

---

**M1. [WONTFIX] 薪資成長率與通膨率完全脫鉤，預設值組合會人為製造「破產」假象**
- **位置**：`IncomeExpenseCalculator.java:40-44`（薪資成長）vs `63-70`（支出通膨）
- **為何是商業邏輯考慮不周**：支出每年依 `config.getInflationRate()`（預設 0.02）複利成長，但薪資成長率讀取 `profile.getSalaryGrowthRate()`，若使用者未設定該可選欄位則預設為 **0.0**——完全沒有機制將名目薪資成長與同一份通膨假設綁定。這是典型的「兩個看似獨立的可選欄位在預設值組合下產生非預期交互作用」的設計缺陷。
- **情境**：`profile.getSalaryGrowthRate()` 為 null（許多使用者不會特別設定），薪資 30 年不變，而支出以 2% 複利成長，30 年後名目支出上漲約 1.8 倍、薪資完全不變，`netSavings` 必然隨時間趨向負值——引擎會回報一位財務健康的使用者「即將破產」，這純粹是預設值組合的人工產物，不是真實財務風險。
- **建議修正**：若 `salaryGrowthRate` 未設定，預設值應與 `inflationRate` 掛鉤（而非硬編碼 0.0），或在 UI 明確要求使用者確認此欄位。

> [!NOTE]
> **WONTFIX 理由**：總體經濟的通膨是客觀事實，但個人的名目薪資成長並沒有保證（尤其是考量到日本過去數十年的薪資停滯現象）。若系統在未經使用者同意下，自動將薪資成長與通膨掛鉤，可能會給出過於樂觀的資產投影。最保守且負責的作法是：讓支出確實反映通膨壓力，以此提醒使用者若薪資不成長，實質購買力就會下降。

---

**M2. [FIXED] （合併：核心引擎 low + 房貸元件 medium）房貸模擬在當月付款上有一個月的計算缺口**
- **位置**：`MortgageSimulationService.java:16-32`（`fastForwardMortgagesToPresent`）與 `107-162`（`simulateMortgagesForYear`, `isFutureMonth` 判斷於第 110 行）
- **為何是商業邏輯考慮不周**：`fastForwardMortgagesToPresent` 只將餘額推進到「現在」的前一個月（`monthsElapsed` 不含當月），而 `simulateMortgagesForYear` 只在 `currentYm.isAfter(nowMonth)`（嚴格晚於現在）時才套用當月本息——兩個條件的邊界剛好互相錯開，導致「現在」這個月的本息扣款**兩邊都沒有處理到**，是典型的邊界值（off-by-one）遺漏。
- **情境**：設 `nowMonth = 2026-08`，8 月的本息既不在 fast-forward 階段被扣（只推進到 7 月），也不在年度模擬階段被扣（`isAfter` 判斷為 false）。當年度的房貸現金流出因此少算一期付款，且結轉到下一年的房貸餘額永久性地比真實世界高出約一期付款金額，微幅墊高後續每年的利息計算，影響貫穿整個貸款剩餘年限。
- **建議修正**：將 `isFutureMonth` 的判斷改為 `!currentYm.isBefore(nowMonth)`（即包含當月），並確認 `fastForwardMortgagesToPresent` 與 `simulateMortgagesForYear` 的邊界正好互補、不重疊、不遺漏。

---

**M3. [PLANNED] 多筆房貸的提前還款決策被強制視為單一整體，無法個別指定**
- **位置**：`MortgageSimulationService.java:199`（`processEarlyRepaymentAtRetirement`）；`SimulationOptimizerService.java:114`（`findBestMortgageEarlyRepaymentYear`）
- **為何是商業邏輯考慮不周**：資料模型正確支援多筆房貸各自獨立攤還計息（`MortgageRecord`/`SimulationData` 為 list），但提前還款的控制面（`SimulationOverrides.mortgageEarlyRepaymentYear: Integer`）是套用在所有房貸加總餘額上的單一全域旗標——利率風險分析做到了個體層級，但決策層級卻退化成整體近似。
- **情境**：使用者持有主宅房貸（利率 1%）與投資物件房貸（利率 3%），無法詢問「只提前還掉高利率的那一筆」；`findBestMortgageEarlyRepaymentYear` 也只搜尋單一 `testYear` 套用在合併資金池上，「該不該優先清償高利率貸款」這個真實世界中很常見且有明確數學正解的問題無法被模型回答。
- **建議修正**：將 `mortgageEarlyRepaymentYear` 從單一 `Integer` 擴充為每筆房貸獨立的還款計畫（例如 `Map<mortgageId, Integer>`），並讓優化器支援逐筆搜尋。

---

**M4. [FIXED] `calculateNisaGoal` 對過小 `targetAge` 靜默回傳誤導性的「0」**
- **位置**：`SimulationOptimizerService.java:55`；`POST /nisa-goal` 的 `SolveNisaGoalRequest.targetAge` 無驗證
- **為何是商業邏輯考慮不周**：與 H8/H9 同一根源（`targetAge` 缺乏跨欄位驗證），但這裡的故障模式更隱蔽——`targetAge` 過小使模擬結果列表為空，`simulation.stream().allMatch(...)` 對空 stream 恆真（vacuous truth），導致 `noBankruptcy` 永遠為 true，二分搜尋收斂到 `best = 0`，API 回傳 `ApiResponse.success(0)`，字面意義是「你完全不需要提繳 NISA 就能達成目標」——這是一個**由未驗證輸入產生的偽計算結果**，而非真實運算輸出。
- **情境**：前端誤傳過小的 `targetAge`，使用者收到「每月需提繳 ¥0」的結論並可能依此做出實際財務決策。
- **建議修正**：與 H8/H9 共用同一套輸入驗證；此外二分搜尋應在輸入無效時明確回傳錯誤而非讓空 stream 的 vacuous truth 污染判斷邏輯。

---

**M5. [DONE] NISA 本金跨年回補機制完全未經測試驗證**
- **位置**：`AssetSimulationEngineImplTest.java:144-149` 對應 `AssetSimulationEngineImpl.java` 中 `nisaPrincipalToRecoverNextYear` 的處理
- **為何是商業邏輯考慮不周**：日本 NISA 制度的一項重要特性是「終身額度會隨賣出而釋出」——賣出部分持股後，其對應本金額度會在次年恢復可用。這個規則的實作（負現金流年度賣出 NISA 時排入下年度回補、次年開頭從 `accumulatedNisaPrincipal` 扣減後再計算剩餘額度）完全沒有測試驗證，若回補步驟被跳過、重複或未經 `Math.max` 下限保護，終身額度的可用空間會在數十年複利期間悄悄失準。
- **情境**：第 N 年大額負現金流賣出部分 NISA（排入回補 800,000），第 N+1 年檢查終身上限時，預期 `accumulatedNisaPrincipal` 先扣減 800,000 再計算剩餘額度；若此步驟出錯，目前無測試會發現。
- **建議修正**：新增跨年度測試案例，涵蓋回補金額的加總、下限保護與多年疊加情境。

---

**M6. [FIXED] `pensionStartAge` 未鉗制在 60-75 法定範圍，與年金乘數計算內部假設不一致**
- **位置**：`AssetSimulationEngineImpl.java:229`（直接使用 `age >= pensionStartAge`）；`PensionCalculator.calculatePensionMultiplier`（內部自行鉗制在 60-75）
- **為何是商業邏輯考慮不周**：日本公共年金制度規定提前/延遲領取的合法範圍為 60-75 歲，年金金額的增減幅（提前減額、延遲加算）也是依此範圍計算的。目前系統有兩處各自處理年齡邊界的地方——主迴圈的發放觸發條件（未鉗制）與乘數計算（已鉗制）——一旦上游 `pensionStartAge` 超出合法範圍，兩者會**互相矛盾**：以錯誤的年齡觸發發放,卻用鉗制後的年齡計算金額。
- **情境**：設定/覆寫值使 `pensionStartAge=50`，引擎會在 50 歲就開始發放年金，但金額卻是依 60 歲的乘數（無折減）計算——等於提早 10 年領到「未折減」的全額年金，這在制度上不可能發生。
- **建議修正**：在 `SimulationParameterResolver.resolvePensionStartAge` 層級統一鉗制到 60-75，讓觸發條件與金額計算共用同一個已驗證的年齡值。

---

**M7. [FIXED] iDeCo/DC 解鎖年齡無下限檢查，可低於法定 60 歲**
- **位置**：`SimulationParameterResolver.java:89-105`（`resolveIdecoUnlockAge`/`resolveDcUnlockAge`）
- **為何是商業邏輯考慮不周**：日本法律明確禁止 60 歲前提領 DC/iDeCo 資金（隨開始提繳年齡推遲可能延至 65 歲），這是稅務優惠帳戶「鎖定期」規則的法定下限，目前設定值被逐字接受，沒有任何最低值防護。
- **情境**：設定錯誤或誤填 `idecoUnlockAge=45`，`TaxAdvantagedAccountProjector.processAccountsForYear` 會在 45 歲就強制將整筆 iDeCo 餘額清算入現金，產生一個法律上不可能發生的提前整筆提領情境。
- **建議修正**：在 `SimulationParameterResolver` 加入下限鉗制（60 歲，可依開始提繳年齡動態調整至 65 歲上限）。

---

**M8. [FIXED] 生涯事件數值驗證存在漏洞：合法的 0 被阻擋，倍數型輸入無範圍檢查**
- **位置**：`event-timeline-manager.tsx:71-74, 208`；`IncomeExpenseCalculator.java:51`
- **為何是商業邏輯考慮不周**：`!careerForm.value` 的 falsy 檢查會連帶阻擋合法的 0 值輸入（例如 `ABSOLUTE_OVERRIDE` 設為 ¥0 代表無薪、或 `RELATIVE_CHANGE` 倍數 0 代表無薪假），這是輸入驗證邏輯選錯判斷方式的典型案例。同時 `RELATIVE_CHANGE` 要求輸入倍數（1.2 代表 +20%）僅透過 placeholder 提示，沒有任何範圍檢查，後端 `currentSalary = (long) (currentSalary * event.getEventValue())` 逐字套用。
- **情境**：使用者誤將 20（意圖表達「+20%」）輸入到 `RELATIVE_CHANGE` 的 Value 欄位，實際套用為 20 倍薪資（2000% 成長），沒有任何前端範圍檢查或確認步驟，整條投影因此崩壞。
- **建議修正**：將必填檢查改為 `value === undefined || value === null`（而非 falsy），並為 `RELATIVE_CHANGE` 增加合理倍數範圍檢查（例如 0.1-3.0）與確認提示。

---

**M9. [FIXED] 資產配置面板（NISA/DC）的預期報酬率輸入無邊界檢查**
- **位置**：`asset-allocation-drawer.tsx:192-202, 241-251`；`NisaContributionSettingController.UpdateNisaAllocationRequest`、`DefinedContributionController.UpdateAllocationRequest` 皆為無 `@Min`/`@Max` 的裸 `Double`
- **為何是商業邏輯考慮不周**：Return (%) 輸入框有 `step="0.1"` 但無 min/max，前端會將輸入值除以 100 轉換為小數比例後 PATCH 至後端，後端 DTO 同樣沒有驗證，形成前後端雙重防護皆缺失的狀態。
- **情境**：使用者手誤輸入 500（意圖 5.00%），實際轉換並儲存為 5.0（500% 年化報酬率），此值會在後續每一年的投影中複利套用，產生明顯荒謬但完全不會被系統拒絕的多十年淨資產曲線。
- **建議修正**：前端輸入加上合理範圍（例如 -10%~20%），後端 DTO 加上 `@DecimalMin`/`@DecimalMax` 作為第二層防護。

---

### 🟢 Low（3 項）

---

**L1. [FIXED] DC/iDeCo 無法模擬「解鎖後延遲整筆提領」的稅務優化策略**
- **位置**：`LiquidationSimulationService.java:1-66`（整體行為）；`TaxAdvantagedAccountProjector.processAccountsForYear:59-89`
- **為何是商業邏輯考慮不周**：`LiquidationSimulationService` 正確地不去動用鎖定期內的 DC/iDeCo（尊重法定限制），但解鎖後也完全沒有機會被當作可選擇的清算來源——`TaxAdvantagedAccountProjector` 會在恰好達到解鎖年齡時**無條件**將 100% 餘額整筆課稅並轉入現金。日本實務上「延遲領取以改變退職所得扣除額計算」是常見的稅務優化策略，目前架構完全無法表達。
- **情境**：使用者想模擬「60 歲解鎖但延遲到 65 歲才整筆提領」，系統不支援，60 歲當下就被強制整筆結算課稅並轉現金，所有使用者的 DC/iDeCo 策略被迫套用相同的「解鎖即提領」假設。
- **建議修正**：允許解鎖後餘額繼續留在帳戶內投資，並讓 `LiquidationSimulationService` 將其視為一個具年齡條件的可選清算來源。

> [!NOTE]
> **修正說明**：我們在前一次的修改中，已經為 `UserProfile` 加上了 `taxAdvantagedWithdrawalAge` 參數，並整合到 `SimulationParameterResolver` 與 `TaxAdvantagedAccountProjector`。現在使用者可以手動設定提領年齡（例如延遲到 65 歲），系統不會再強制於 60 歲直接提領。

---

**L2. [POSTPONED] `SimulationParameterResolver` 的優先順序解析邏輯沒有專屬單元測試**
- **位置**：無 `SimulationParameterResolverTest`；`SimulationParameterResolver.java:20-105`
- **為何是商業邏輯考慮不周**：覆寫 > 個人檔案 > 全域設定的優先順序解析，是退休年齡、養老金起始年齡、預期壽命、DC/iDeCo 解鎖年齡等多項關鍵參數的共同基礎，目前只透過引擎測試間接、隱性地觸及預設路徑，優先順序本身從未被顯性驗證。
- **情境**：呼叫 `resolveRetirementAge(profileValue=62, configValue=65, override=58)` 預期回傳 58（override 優先）；若未來重構不小心把優先順序寫反（例如 profile 優先於明確 override），現有測試套件不會有任何失敗，即使這會改變真實的退休/年金規劃輸出。
- **建議修正**：新增針對優先順序解析邏輯本身的專屬單元測試，涵蓋各參數的三層優先權組合。

---

**L3. [FIXED] Solver「套用至主設定」按鈕沒有實際套用任何設定**
- **位置**：`page.tsx:152-157`
- **為何是商業邏輯考慮不周**：`onGoalCalculated={(goal) => { toast.success(...) }}` 只顯示一則成功提示，沒有任何狀態更新、PATCH 請求或自動開啟設定面板的行為——這是 UI 承諾（按鈕文字「套用至主設定」）與實際行為完全不符的案例。
- **情境**：使用者跑完 Worst-Case Solver 得到建議提繳金額，點擊「套用至主設定」，若沒有仔細閱讀 toast 內容（提示需自行到設定面板手動儲存），會誤以為系統已經自動更新其 NISA 設定，但後端完全沒有任何變更。
- **建議修正**：讓按鈕真正呼叫對應的 PATCH API 更新設定，或至少自動開啟設定面板並預填計算結果，而非僅顯示文字提示。

---

## 3. 未發現問題的維度

本次三方對抗式驗證涵蓋以下維度：模擬引擎核心計算、房貸/清算/優化器元件、模擬領域模型與 API 契約、前端 UI 表單與 Solver 對話框、測試覆蓋率、以及跨維度的完整性審查（稅務優惠帳戶規則、社會保險、家庭結構等）。

**在上述所有審查維度中，未發現任何「經三方驗證後確認為零問題」的維度——每一個被審查的維度都至少產出一項經驗證的真實發現。** 換言之，本次 review 沒有可以明確標示為「已通過驗證、無需處理」的空白清單；所有列出的 30 項發現即為目前 review 覆蓋範圍內的全部已知風險。

需特別說明的是，本次審查**未涵蓋**（而非「涵蓋後確認無問題」）的範圍包括：資料庫層級的併發/交易一致性、模擬計算的效能與大規模歷史資料下的可擴展性、以及模擬結果的前端視覺化呈現正確性——這些屬於本次 review 範疇之外，若需要對外提供正式財務建議，建議列入下一階段的審查計畫。

---

## 4. 總結與優先處理建議

### 4.1 整體評估

模擬引擎的**單一使用者、單一貨幣、單一情境**核心計算路徑（NISA/iDeCo 上限節流、基本攤還公式、稅務扣除計算）具備合理的架構基礎，但三類結構性缺口貫穿整份報告：

1. **反事實情境（"what-if"）從未被真正模擬**——`null` 被同時用來表示「無意見」與「明確選擇某個選項」，導致優化器的比較基準線失真（H4、M3、M4）。
2. **稅務優惠帳戶與一般帳戶的邊界被混淆**——公司股票被當作免稅資產處理（H6），NISA 覆寫值被逐基金設定悄悄蓋過（H2），法定提繳上限只在 NISA 落實而 iDeCo/DC 完全缺失（H16）。
3. **輸入邊界防護幾乎不存在**——從 API 層的 `targetAge`（H8、H9、M4）到前端表單的數值範圍（H15、M8、M9），系統性地信任所有輸入為合理值。

### 4.2 優先處理建議（依「風險 × 修復成本」排序）

**第一優先（高風險、修復成本相對低，建議立即處理）**
- H1 公司股票幣別/匯率換算（單點修正，影響面極廣）
- H8/H9/M4 `targetAge` 系列輸入驗證（三處共用同一套驗證邏輯，一次性修復可解決三項發現）
- H2 Solver 覆寫報酬率被逐基金設定蓋過（邏輯修正，不涉及資料結構變更）
- M2 房貸月份計算缺口（明確的 off-by-one，範圍小、影響持續累積）

**第二優先（高風險，需要一定的架構調整）**
- H4/M3 房貸提前還款反事實情境與多筆房貸個別決策（建議一併重新設計 `SimulationOverrides` 的語義）
- H6 應稅帳戶資本利得稅（需要新增稅務常數與帳戶類型判斷，但邏輯獨立、風險可控）
- H16 iDeCo/DC 法定提繳上限（可參考現有 NISA 節流實作模式）
- H10/H11 補齊 `LiquidationSimulationService`/`MortgageSimulationService` 測試（測試補齊本身風險低，但是後續所有修復的驗證基礎，應與對應修復同步進行）

**第三優先（中高風險，涉及資料模型擴充，建議規劃為獨立迭代）**
- H3 公司股票報酬率建模
- H7 多重收入來源模型
- H18 配偶/家屬獨立財務軌跡
- H17 國民健康保險保費建模

**第四優先（低風險或使用體驗類，可併入常規開發排期）**
- H13/H14 前端-後端精度與驗證不一致（月份精度、結束日期驗證）
- H15/M8/M9 各類輸入邊界檢查（前後端雙層防護，建議統一制定數值範圍規範後批量修復）
- M6/M7 年金起始年齡與 DC/iDeCo 解鎖年齡的法定範圍鉗制
- L1/L2/L3 低優先級的功能完整性與測試缺口

建議在對外提供任何自動化財務建議（尤其是 Solver 的「最佳還款年齡」「NISA 目標提繳」等決策型輸出）之前，至少完成第一、第二優先項目的修復與對應測試補齊，因為這些發現直接影響**決策建議的方向性正確性**，而非僅是數值精度問題。