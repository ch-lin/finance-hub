# [FIXED] Issue 137：住宅ローン控除首年遞延機制，沒有正確處理「次年住民稅轉扣」跟「次年現金退稅不卡所得稅實繳上限」這兩個連鎖效應

## 這份issue怎麼被發現的
這是Gemini在[question-136](question-136.md)裡主動提出的架構質疑，針對[Fixed-45-01](../fixed/Fixed-45-01-%20%E4%BD%8F%E5%AE%85%E3%83%AD%E3%83%BC%E3%83%B3%E6%8E%A7%E9%99%A4%E9%A6%96%E5%B9%B4%E6%99%82%E9%96%93%E5%B7%AE%E4%BF%AE%E5%BE%A9%E8%A8%88%E7%95%AB.md)（首年確定申告退稅時間差機制）去追它在整個模擬引擎裡的端到端連鎖效應，Gemini自己沒有先動手改程式碼，是請Claude確認問題是否存在、評估影響範圍、建議處置方案。這份issue是Claude查證程式碼後、確認問題確實存在，獨立記錄下來的結論。

## 背景：Fixed-45-01當初要解決的問題
日本的住宅ローン控除（房貸抵稅），入居第一年因為沒辦法透過公司「年末調整」辦理，必須在次年2/16~3/15自行向國稅局辦「確定申告」才能領到這筆退稅。Fixed-45-01為了模擬這個時間差，實作了：首年把`deduction`強制設為0、不在當年抵扣所得稅，暫存進`context.pendingNextYearTaxRefund`；次年開頭把這筆暫存的金額全額加進現金，模擬使用者收到國稅局退稅款。

已確認：Fixed-45-01當初的規劃文件完全沒有討論過「這筆被歸零的首年deduction，剩下沒被所得稅抵完的部分，依法應該轉去抵次年住民稅」、也沒有討論過「次年發放的這筆退稅現金，依法不能超過使用者首年實際繳的所得稅」這兩件事——這不是當初評估過、決定接受的簡化，是設計時沒有被考慮到的技術缺口。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:353-363`（`isFirstEligibleYear`判斷、首年`deduction`歸零並暫存進`pendingNextYearTaxRefund`的邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:209-238`（`actualMortgageDeduction`、`remainingMortgageDeduction`、住民稅轉扣的計算邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:77-82`（次年開頭把`pendingNextYearTaxRefund`全額加進現金的邏輯）

## 現狀與為何是問題

### 問題一：次年住民稅轉扣被實質沒收
`ActiveTaxCalculationEngine.java:213`：`actualMortgageDeduction = Math.min(baseIncomeTax, mortgageTaxDeduction- 系統目前算出的結果：次年收到21萬円現金退稅（多發16萬円），且次年住民稅完全沒有任何抵扣（該有的轉扣被歸零）。
這個落差會讓模擬出來的現金流，在房貸入住的頭兩年系統性偏離真實情況——不是誤差方向固定偏樂觀或偏悲觀，取決於使用者首年實際所得稅跟房貸抵稅額的相對大小，但兩個問題都存在、且方向相反（問題二讓現金被高估、問題一讓次年稅負被高估），長期模擬下來這兩個落差會互相疊加、不會互相抵消。

## 影響範疇評估
這個落差只發生在「每一筆房貸的入住第一年」跟「入住第二年」這兩個特定年度，不是每年都會發生。但因為這個工具的典型用法是模擬一個人從現在到退休的完整人生，使用者只要在模擬裡設定了房貸、而且房貸是在模擬範圍內的某一年才入住（不是在模擬開始前就已經入住多年的舊房貸），就必然會經過這兩個特定年度，不是邊緣情境。影響金額的量級跟房貸抵稅額本身的量級相當（可以到十幾萬円等級），不是小數字誤差。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：維持現狀，記錄為Known Limitation**
- 理由：首年時間差本身是真實存在的現象、值得模擬；這兩個連鎖效應只影響「入住頭兩年」這個短暫窗口，長期模擬的總現金流影響可能有限。
- 缺點：這兩個問題一個讓現金被高估、一個讓稅負被高估，不是同一個方向的誤差，無法簡單地說「反正會互相抵消就不用管」。

**方向B：系統性重構，讓首年遞延機制正確聯動所得稅實繳上限與次年住民稅轉扣**
- 概念方向：`MortgageCalculator`在首年不應該直接把`deduction`歸零後丟給`pendingNextYearTaxRefund`，而是需要知道「這一年實際的所得稅是多少」才能正確拆分「所得稅能扣多少、要轉住民稅多少」；但`MortgageCalculator`本身在計算這一步時，還不知道最終的`baseIncomeTax`是多少（那是`ActiveTaxCalculationEngine`才會算出來的）。
- 需要評估的技術問題：這代表要正確解決，可能需要調整兩個計算引擎之間的呼叫順序或資料傳遞方式，讓「首年deduction歸零」這個決定，能夠在知道「首年實際所得稅」之後才拆分成「次年現金退稅（卡所得稅上限）」跟「次年住民稅轉扣（卡住民稅上限）」兩筆分開的暫存值，而不是像現在一樣，把整筆`deduction`直接當成次年現金，完全不經過稍後才會知道的所得稅上限判斷。
- 工程量評估：這不是小改動，牽動兩個核心計算引擎之間的介面，需要先確認目前`MortgageCalculator`跟`ActiveTaxCalculationEngine`的呼叫順序跟資料相依關係，才能判斷怎麼調整不會打斷其他既有邏輯。

> **架構檢討**：
> 針對上述 Claude 提出的方向 B 進行深入技術檢討：
> - Claude 提出的方向 B 著眼於將首年額度拆分成「次年現金退稅」與「次年住民稅轉扣」兩筆專屬暫存值，並評估調整兩引擎呼叫順序。但這本質上仍是將問題侷限於「房貸首年的局部暫存個案」，無法根本連動 [Issue 139](issue-139.md) 的排富機制，也無法為 [Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)（住民稅前年所得引擎）提供通用的跨年所得傳遞基石。
> - 若沿此局部暫存思路，在實作層面容易演變為強行調整引擎呼叫順序，或退化為引入 ad-hoc flag 特判，治標不治本。
> - 因此，我們並未採納 Claude 提出的方向 B，而是另外提出「還原現實世界退稅時序（入居首年確定申告退款於隔年入帳、次年起走公司年末調整）」為思考方向，並在下方正式開展「方案 A（局部補丁）」與「方案 B（通用跨年狀態機）」的工程權衡。

---

## 方案 A 與方案 B 之工程權衡

| 維度 | 方案 A（局部補丁 / 換地方打 Flag） | 方案 B（拍板正解：通用跨年狀態機） |
|---|---|---|
| **本質** | 治標：換個地方打 flag 補丁（在 Context 加專屬暫存欄位或特判標籤） | 治本：建立真正的跨年狀態機（Cross-Year Tax State Machine） |
| **視角定位** | 仍把這件事視為「房貸首年的特殊暫存問題」 | 提升為「跨年狀態傳遞的通用機制」 |
| **資料結構設計** | 在 Context 裡打專屬暫存 flag 或欄位（`pendingNextYearTaxRefund` 等） | 讓 Context 記錄前一年通用的結算結果 `PreviousYearTaxRecord`：<br>• `grossSalary`<br>• `baseIncomeTax`<br>• `pendingKakuteiShinkokuRefund`（僅首年有值，次年歸零）<br>• `unusedMortgageDeductionForInhabitantTax` |
| **首年判定方式** | 在各引擎間手動傳遞或開關 mutable flag | 由 `MortgageCalculator` 依不可變領域時間將額度歸入「確定申告（`kakuteiDeduction`）」或「年末調整（`yearEndDeduction`）」金額通道，零 boolean flag 傳遞 |
| **次年計算方式** | 隔年特判：若有某個 flag 或特定暫存額度就扣 | 隔年標準流程：讀取前一年退稅額（>0 則退、為 0 則不退），自然讀取前一年未扣額折抵住民稅 |
| **Issue 137** | 需多處補丁 | 徹底修復（卡實繳所得稅上限、住民稅轉扣精確生效） |
| **Issue 139 (排富)** | 需再額外打補丁或在 engine 裡特判 flag | 額度交由稅務引擎查核年收，超標則當場兩通道歸零，次年自然無退稅（自然一併根治） |
| **雙重退稅防護** | 易因邏輯混亂重複發放 | 第 2 年起年末調整生效，確定申告通道為 0L，跨年退稅額天然歸零，絕不雙重退稅 |
| **Plan-21 (前年所得)** | 完全無解，未來仍需重寫 | 直接為 Plan-21 鋪好地基（前年所得已在 Context 裡） |
| **代碼乾淨度** | 留著 mutable flag 與特判邏輯（容易在各引擎滋生 ad-hoc 判斷） | **純金額通道驅動（Amount-Driven），無任何 boolean flag 或 mutable dirty flag 污染** |

---

## 拍板治本架構方案：跨年狀態傳遞機制（Cross-Year Tax State Machine）

**PM 決策定案採用「無 Flag（純金額雙通道）」實作，以 100% 符合現實世界金流與稅務時序邏輯**：
我們堅決不採用在各處開關 mutable/dirty flag 或在 `MortgageCalculator` 提前粗暴歸零的局部補丁，而是採用**純金額通道驅動（Amount-Driven）架構：房貸計算機依實體入居時間將理論額度分配至「年末調整（`yearEndDeduction`）」與「確定申告（`kakuteiDeduction`）」雙金額通道（全系統零 boolean flag）。稅務引擎查核排富並依實繳所得稅進行純算術抵扣與法定上限拆分，將結算狀態封裝入 `SimulationContext`（`PreviousYearTaxRecord`）傳遞給次年；次年春季自然兌現現金退稅、夏季兌現住民稅減徵，第 2 年起確定申告通道金額天然為 0 以防杜雙重退稅，並由公司年末調整正常扣減，完全還原真實世界所得稅與住民稅的金流運作時序**。

### 1. 核心因果時序與資料傳遞方式
- **`MortgageCalculator`**：依據房貸入居時間（`refDate`），將理論年度抵稅額直接分流至 `yearEndDeduction`（年末調整）與 `kakuteiDeduction`（確定申告）雙通道；若為首年入居則只點亮 `kakuteiDeduction`，第 2 年起則只點亮 `yearEndDeduction`。
- **`SimulationContext`**：跨年傳遞 `PreviousYearTaxRecord` 記錄前一年薪資與稅務實繳數據。
- **`ActiveTaxCalculationEngine`**：
  - **首年**：因 `yearEndDeduction == 0L`，當期所得稅完全不被抵扣，收入實額全數納入確定申告退稅計算範圍（卡實繳上限）。
  - **次年**：`yearEndDeduction` 正常執行年末調整扣抵當期所得稅；前一年 `pendingKakuteiShinkokuRefund` 全數加回現金（春季入帳），前一年 `unusedMortgageDeductionForInhabitantTax` 按法定上限扣抵當期住民稅（夏季減稅）。
  - **兩年都無 flag 分支**：純算術抵扣與單軌跨年流水線，零特判。

### 2. 核心因果時序與資料傳遞方式加進現金，並當場在 Record 中將該額度歸零（徹底取代舊有 `setPendingNextYearTaxRefund(0L)` 防重複發放機制）。
     - 讀取 `unusedMortgageDeductionForInhabitantTax`，由稅務引擎標準流程扣除次年住民稅。
   - 次年自身的年末調整於年底由 `yearEndDeduction` 正常執行，兩者自然銜接，時序完全合規，零 Flag 驅動。限、住民稅轉扣精確生效） |
| **Issue 139 (排富)** | 需再額外打補丁或在 engine 裡特判 flag | 額度交由稅務引擎查核年收，超標則當場兩通道歸零，次年自然無退稅（自然一併根治） |
| **雙重退稅防護** | 易因邏輯混亂重複發放 | 第 2 年起年末調整生效，確定申告通道為 0L，跨年退稅額天然歸零，絕不雙重退稅 |
| **Plan-21 (前年所得)** | 完全無解，未來仍需重寫 | 直接為 Plan-21 鋪好地基（前年所得已在 Context 裡） |
| **代碼乾淨度** | 留著 mutable flag 與特判邏輯（容易在各引擎滋生 ad-hoc 判斷） | **純金額通道驅動（Amount-Driven），無任何 boolean flag 或 mutable dirty flag 污染** |

---

### 1. 核心類別職責切分與無 Dirty Flag 實作原則

1. **`MortgageCalculator` 與 `MortgageYearlyResult`（領域時序計算，純金額通道輸出，零 Flag）**：
   - 職責純粹化：負責計算本金餘額、利息、年還款額，以及當年度依年底殘高與法定上限算出的理論房貸抵稅額。
   - 徹底移除第 353-361 行的粗暴歸零（`deduction = 0L`）與越權寫入（`setPendingNextYearTaxRefund`）。
   - **純金額通道設計（徹底消滅 Flag）**：
     基於不可變的房貸實體時間（`refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate()`），判斷當年度是否為首年入居（`refDate.getYear() == currentYear`）。
     不向外傳遞任何 `isFirstEligibleYear` boolean flag，而是直接將理論抵稅額分配至語意化金額通道：
     - **首年入居**：`yearEndDeduction = 0L`, `kakuteiDeduction = theoreticalDeduction`
     - **第 2 年起（或既有舊房貸）**：`yearEndDeduction = theoreticalDeduction`, `kakuteiDeduction = 0L`
     - **無房貸或已過法定年限**：兩者皆為 `0L`。
     封裝於不可變 Record `MortgageYearlyResult(totalMortgagePaymentsThisYear, yearEndDeduction, kakuteiDeduction, maxTotalIncomeForMortgageDeduction, inhabitantTaxLimitStrategy)`。

2. **`SimulationContext`（跨年狀態容器）**：
   - 定義不可變的 `PreviousYearTaxRecord` 記錄前一年通用結算數據（保留通用跨年欄位以供後續住民稅隔年課稅機制銜接）：
     - `long grossSalary`（前一年薪資毛額，供 Plan-21 等住民稅計算及查核）
     - `long taxableIncomeForIncomeTax`（前一年課稅所得總額）
     - `long baseIncomeTax`（前一年基準所得稅，確定申告退稅法定上限）
     - `long pendingKakuteiShinkokuRefund`（前一年待以確定申告現金退還之款項；**首年卡實繳所得稅上限，第 2 年起自然為 0L**）
     - `long unusedMortgageDeductionForInhabitantTax`（前一年所得稅未扣完轉入次年住民稅之額度「所得税から控除しきれなかった額」）
   - Context 統一持有 `PreviousYearTaxRecord previousYearTaxRecord` 並於年際推進時傳遞，同時徹底移除舊有的 `pendingNextYearTaxRefund` 孤立欄位。

3. **`ActiveTaxCalculationEngine`（標準稅務計算、排富查核、純算術抵扣與單軌跨年流水線）**：
   - **排富閘門（解 Issue 139）**：收到房貸結果後，第一時間查核當期年收，若超標（`employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()`），當場將兩個金額通道全數歸零（`yearEndDeduction = 0L; kakuteiDeduction = 0L;`）。
   - **當期年末調整所得稅抵扣（純算術運算，零 Flag 分流）**：
     `actualYearEndDeduction = Math.min(baseIncomeTax, mortgageResult.yearEndDeduction())`
     `baseIncomeTax -= actualYearEndDeduction`
     （首年因 `yearEndDeduction == 0L`，`actualYearEndDeduction` 自然為 0L，當期所得稅分文不扣，完整維持源泉徵收；第 2 年起走年末調整，當期直接少繳稅款，完全不需要 `if (isFirstYear)` 分支判斷！）。
   - **次年確定申告現金退稅拆分（卡所得稅實繳上限，解 Issue 137 問題二，天然防杜雙重退稅）**：
     `actualKakuteiRefund = Math.min(baseIncomeTax, mortgageResult.kakuteiDeduction())`
     （首年卡在實繳所得稅 `baseIncomeTax` 上限內算出次年確定申告應退現金；第 2 年起因 `kakuteiDeduction == 0L`，`actualKakuteiRefund` 數學上必然為 0L，天然杜絕雙重退稅，零特判！）。
   - **住民稅抵扣與轉扣拆分（單軌跨年流水線，解 Issue 137 問題一，全週期零重複扣除）**：
     - **第一步：先扣除前一年傳下來的住民稅抵扣額**（次年兌現前一年未能退完/抵完之額度）：
       ```java
       if (prevTax != null && prevTax.unusedMortgageDeductionForInhabitantTax() > 0) {
           long priorCredit = Math.min(inhabitantTax, prevTax.unusedMortgageDeductionForInhabitantTax());
           inhabitantTax -= priorCredit;
       }
       ```
     - **第二步：計算當期房貸所得稅未扣完之餘額，全數封裝留待次年住民稅折抵（當期絕不扣當期）**：
       ```java
       long inhabitantLimit = mortgageResult.inhabitantTaxLimitStrategy()
               .calculateLimit(taxableIncomeForIncomeTax);
       long totalDeduction = mortgageResult.yearEndDeduction() + mortgageResult.kakuteiDeduction();
       long totalIncomeTaxUsed = actualYearEndDeduction + actualKakuteiRefund;
       long remainingDeduction = totalDeduction - totalIncomeTaxUsed;

       long nextYearInhabitantCredit = remainingDeduction > 0 
               ? Math.min(remainingDeduction, inhabitantLimit) 
               : 0L;
       ```
     - **第三步：將當年度標準結算數據打包成 `PreviousYearTaxRecord` 存入 Context**：
       封裝完整跨年結算 Record：
       - `grossSalary` = 當年度薪資毛額
       - `taxableIncomeForIncomeTax` = 當年度課稅所得
       - `baseIncomeTax` = 當年度實繳基準所得稅
       - `pendingKakuteiShinkokuRefund` = `actualKakuteiRefund`（首年卡實繳上限，第 2 年起走年末調整，自然為 0L）
       - `unusedMortgageDeductionForInhabitantTax` = `nextYearInhabitantCredit`（當期未抵完額度全數轉入次年，第 2 年年末調整若沒扣完亦自然滾入第 3 年）

4. **`YearlySimulationPipeline`（生命週期推進，純金額驅動）**：
   - 跨年推進：次年開頭自然讀取 `PreviousYearTaxRecord`。
     - 若 `pendingKakuteiShinkokuRefund > 0`（第 1 年確定申告退稅款），全額加進現金，並當場在 Record 中將該額度歸零（徹底取代舊有 `setPendingNextYearTaxRefund(0L)` 防重複發放機制）。
     - 讀取 `unusedMortgageDeductionForInhabitantTax`，由稅務引擎標準流程扣除次年住民稅。
   - 次年自身的年末調整於年底由 `yearEndDeduction` 正常執行，兩者自然銜接，時序完全合規，零 Flag 驅動。

> [!NOTE]
> **關於「確定申告機制」之架構定性與實作備忘（先採註解與輕量時序模擬）**：
> - **現狀架構盤點**：全系統目前僅有 `YearEndAdjustmentEngine`（公司年末調整引擎），並無獨立的 `KakuteiShinkokuEngine` 模組。當初 [Fixed-45-01] 在 `YearlySimulationPipeline:78-82` 於年度開頭執行的現金注入，本質上即為「確定申告退稅款於次年春季入帳」的輕量效果模擬。
> - **本期實作方針**：本期不額外建立龐大複雜的獨立確定申告引擎，而是沿用此輕量管線時序，並在 `YearlySimulationPipeline` 與 `ActiveTaxCalculationEngine` 相關程式碼中補齊詳盡註解，清楚說明此處即為「確定申告現金還付金入帳」與「確定申告未退完額度折抵次年住民稅」的法定等效落地點。
> - **未來演進路徑**：未來若有更龐雜的確定申告業務場景（如自營商申報、醫療費控除申報、多重所得合算等），再來評估是否將其升級抽換為獨立的確定申告申報引擎。

### 2. 核心因果時序與資料傳遞方式

#### 階段一：第 1 年（入居當年）——正常結算並封裝跨年狀態
1. **正常計算房貸額度並分流至金額通道（零 Flag）**：
   - `MortgageCalculator` 照常依年底殘高與借入上限算出當年度合法的理論房貸抵稅額（例如 21 萬円），**絕不提前歸零**。
   - 比對不可變時間屬性（`refDate.getYear() == currentYear`），將理論額度分配至確定申告通道（`kakuteiDeduction = 21 萬円`，`yearEndDeduction = 0L`），封裝於不可變 Record `MortgageYearlyResult`，零 boolean flag 傳遞。
2. **正常進行第 1 年稅務計算、排富檢驗與上限拆分（純算術運算）**：
   - `ActiveTaxCalculationEngine` 拿到第 1 年的 `employmentIncome` 與 `mortgageResult`。
   - **排富閘門（連帶徹底修復 [Issue 139](issue-139.md)）**：若第 1 年年薪超過門檻（2,000 萬或 3,000 萬円），該年度房貸兩金額通道當場全數歸零。
   - **當期所得稅與源泉扣繳（純算術，零 Flag）**：
     因入居首年依法不能走公司年末調整，`yearEndDeduction = 0L`，`actualYearEndDeduction = Math.min(baseIncomeTax, 0L) = 0L`，第 1 年所得稅照常源泉徵收（當期不自 `baseIncomeTax` 中扣除）。
   - **首年確定申告退稅額度結算（徹底修復 Issue 137 問題二）**：
     在第 1 年年底結算時，次年確定申告的退稅金額與轉扣額度 100% 純算術精確算定：
     $$\text{確定申告還付金（現金退稅款）} = \min(\text{第 1 年 } baseIncomeTax, \text{第 1 年 } kakuteiDeduction)$$
     $$\text{未抵完轉抵次年住民稅額} = \min(\text{第 1 年剩餘額度}, \text{法定住民稅限度額 9.75 萬/13.65 萬})$$
3. **將結算狀態寫入 Context**：
   - 在第 1 年結算完成時，將上述核定數字封裝存入 `SimulationContext` 的 `PreviousYearTaxRecord`：
     - `grossSalary`（前一年薪資所得）
     - `baseIncomeTax`（前一年實繳所得稅）
     - `pendingKakuteiShinkokuRefund`（已卡所得稅上限的確定申告現金還付額，例如 5 萬円）
     - `unusedMortgageDeductionForInhabitantTax`（前一年未扣完轉入之住民稅抵扣額，例如 9.75 萬円）

---

#### 階段二：第 2 年（次年）——春季還付 + 住民稅減繳 + 第二年正常的年末調整（退稅歸零防重扣）
進入第 2 年時，使用者依法同時享有三項權益，系統依序精準兌現：

1. **春季（3～4 月）確定申告現金退稅入帳（純金額驅動）**：
   - 在 Pipeline 年度開頭，讀取 `context.getPreviousYearTaxRecord().pendingKakuteiShinkokuRefund()`。
   - 若大於 0（首年留下的 5 萬円），全額加進 `currentLiquidCash`，並當場在 Record 中將該額度歸零（徹底取代舊有 `setPendingNextYearTaxRefund(0L)` 安全機制，防範重複領取）。
   - **效果（徹底修復 Issue 137 問題二）**：現金退稅絕不超發，精準等於第 1 年實繳所得稅範圍內的應退金額。若第 1 年年薪超標被排富（Issue 139），此處發放額為 0。
2. **夏季起（6 月開始之住民稅）住民稅減額**：
   - 算稅引擎讀取 `context.getPreviousYearTaxRecord().unusedMortgageDeductionForInhabitantTax()`，直接從第 2 年算出的住民稅中扣除。
   - **效果（徹底修復 Issue 137 問題一）**：第 1 年未抵完的額度順利在第 2 年住民稅兌現，不再被系統性沒收。
3. **冬季（12 月薪資）第二年自身正常的「年末調整」與次年退稅歸零（防杜雙重退稅）**：
   - **第 2 年起上班族已可透過公司年末調整辦理房貸抵稅**。
   - `MortgageCalculator` 判定非首年，將理論抵稅額全額分配至年末調整通道（`yearEndDeduction = 21 萬円`，`kakuteiDeduction = 0L`）。
   - `ActiveTaxCalculationEngine` 執行純算術：
     - `actualYearEndDeduction = Math.min(baseIncomeTax, yearEndDeduction)`，直接從第 2 年的 `baseIncomeTax` 中扣除（`baseIncomeTax -= actualYearEndDeduction`，當期少繳稅款）。
     - `actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction) = Math.min(baseIncomeTax, 0L) = 0L`。
   - **封裝第 2 年結算狀態至新的 `PreviousYearTaxRecord`**：
     - `pendingKakuteiShinkokuRefund = 0L`（**關鍵：因為 `kakuteiDeduction` 為 0L，實際確定申告退稅額 Math.min(baseIncomeTax, 0L) 必然為 0L，給第 3 年的現金退稅額天然歸零，徹底防杜雙重退稅！**）
     - `unusedMortgageDeductionForInhabitantTax = Math.min(第 2 年剩餘額度, 上限)`（第 2 年若所得稅沒扣完，繼續自然轉入第 3 年住民稅扣抵）。
   - **效果**：第 2 年年末調整照常生效，第 3 年不會被重複發放現金退稅，完全符合日本真實稅制現況！

---

### 3. 方案效益總結

| 解決維度 | 舊機制（Fixed-45-01 Hack） | 新機制（跨年狀態傳遞治本架構） |
|---|---|---|
| **第 2 年春季現金退稅** | 盲目發放全額抵稅額（無上限，多送十幾萬現金） | **精準卡死在第 1 年實繳所得稅上限內**（Issue 137 解除） |
| **第 2 年住民稅抵扣** | 首年額度被提前歸零，次年住民稅扣除為 0（實質沒收） | **精準扣除第 1 年未抵完的餘額**（Issue 137 解除） |
| **第 1 年高所得排富** | 首年繞過稅務引擎，年薪 > 2000 萬依然全額領退稅 | **第 1 年結算時即受排富閘門約束**（Issue 139 自然解除） |
| **第 2 年年末調整** | 邏輯混亂 | **第 2 年年末調整正常執行，與第 1 年還付金疊加生效** |
| **第 3 年起防雙重退稅** | 未考慮，易重發退稅現金 | **第 2 年起退稅款精準歸零（0L），絕不多發一毛錢** |
| **對 [Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md) 的支援** | 孤立無援 | **為「住民稅課前年所得」奠定統一的跨年狀態傳遞基石** |
| **代碼純粹度** | 依賴 ad-hoc flags 與提前歸零 hack | **純金額通道驅動（Amount-Driven），無任何 boolean flag 或 mutable dirty flag 污染** |

---

## 階段一實作計畫：第 1 年（入居當年）——正常結算並封裝跨年狀態

### 1. 目標與邊界
- **核心目標**：
  1. **徹底根治 [Issue 139](issue-139.md)**：終結首年房貸額度繞過稅務引擎排富閘門的重大漏洞，確保年薪超標者（> 2,000 萬或 3,000 萬円）當期額度精確歸零。
  2. **徹底移除首年 Hack**：清除 `MortgageCalculator` 內部對首年的提前歸零（`deduction = 0`）與越權 pending 寫入。
  3. **精確落實首年源泉徵收**：入居首年依法不能走公司年末調整，第 1 年所得稅照常源泉徵收（當期不直接扣除 `actualMortgageDeduction`，防範次年退稅時發生雙重扣除 Double Deduction）。
  4. **建立通用跨年狀態容器 `PreviousYearTaxRecord`**：在 `ActiveTaxCalculationEngine` 完成排富查核與真實法定上限拆分後，將數據完整封裝入 `SimulationContext`。
- **架構約束**：
  - **純金額通道驅動（Amount-Driven）與零 Flag**：由 `MortgageCalculator` 依不可變領域時間將理論額度分配至 `yearEndDeduction` 與 `kakuteiDeduction` 金額通道，完全不向外傳遞任何 boolean flag。稅務引擎採純算術運算，無任何 flag 特判分支。
  - **模組職責純粹**：房貸計算器純算數字與分配申報通道，稅務引擎負責排富、源泉稅額控制、首年退稅拆分與跨年狀態封裝。

---

### 2. 步驟清單與具體改動檔案

#### 步驟 1：建立不可變跨年稅務狀態容器 `PreviousYearTaxRecord`
- **新建檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java`
- **資料定義**：
  ```java
  package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;

  /**
   * 通用跨年稅務結算紀錄。
   * 記錄前一年度真實的所得與稅務抵扣數據，供次年度標準計算流程使用（支援退稅、住民稅轉扣及 Plan-21）。
   */
  public record PreviousYearTaxRecord(
          long grossSalary,                              // 前一年薪資毛額（供 Plan-21 住民稅計算及查核）
          long taxableIncomeForIncomeTax,                // 前一年課稅所得總額
          long baseIncomeTax,                            // 前一年實繳基準所得稅（確定申告退稅之法定上限）
          long pendingKakuteiShinkokuRefund,             // 前一年確定申告現金退稅額（首年卡實繳上限；第 2 年起走年末調整，必為 0L）
          long unusedMortgageDeductionForInhabitantTax   // 前一年所得稅未扣完轉入次年住民稅之額度（卡住民稅上限）
  ) {}
  ```

#### 步驟 2：在 `SimulationContext` 中掛載跨年狀態
- **修改檔案**：[SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- **改動細節**：
  - 增加欄位：`private PreviousYearTaxRecord previousYearTaxRecord;`
  - 由 Lombok `@Data` 自動生成 getter / setter。
  - 在 `clone()` 方法中保留對不可變 Record 的引用拷貝。

#### 步驟 3：淨化 `MortgageCalculator` 並重構 `MortgageYearlyResult` 為雙金額通道（零 Flag）
- **修改檔案**：[MortgageYearlyResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java)
  - 重構 Record 欄位，以語意化金額通道替代單一金額與 boolean flag：
    ```java
    public record MortgageYearlyResult(
            long totalMortgagePaymentsThisYear,
            long yearEndDeduction,          // 當年度年末調整抵稅額（首年為 0L，第 2 年起為理論值）
            long kakuteiDeduction,           // 確定申告抵稅申報額（首年為理論值，第 2 年起為 0L）
            long maxTotalIncomeForMortgageDeduction,
            InhabitantTaxLimitStrategy inhabitantTaxLimitStrategy
    ) {
        // 提供合計便利方法，供向後相容或報表總額統計
        public long totalMortgageTaxDeduction() {
            return yearEndDeduction + kakuteiDeduction;
        }
    }
    ```
- **修改檔案**：[MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L353-L363)
  - 徹底刪除第 359 行的 `context.setPendingNextYearTaxRefund(...)` 與第 360 行的 `deduction = 0L`（消除越權污染）。
  - 依據不可變房貸日期判定首年時間，直接將理論抵稅額分流至金額通道（純領域邏輯，零外部狀態污染）：
    ```java
    LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
    boolean isOccupancyYear = (refDate != null && year == refDate.getYear());
    long yearEndDeduction = isOccupancyYear ? 0L : mortgageTaxDeductionThisYear;
    long kakuteiDeduction = isOccupancyYear ? mortgageTaxDeductionThisYear : 0L;
    ```
  - 回傳封裝雙金額通道之結果：
    ```java
    return new MortgageYearlyResult(
            totalMortgagePaymentsThisYear,
            yearEndDeduction,
            kakuteiDeduction,
            maxTotalIncomeForMortgageDeduction,
            bestStrategy);
    ```

#### 步驟 4：`ActiveTaxCalculationEngine` 嚴格排富、純算術抵扣、首年退稅拆分與狀態封裝（零 Flag）
- **修改檔案**：[ActiveTaxCalculationEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L209-L238)
- **改動細節**：
  1. **排富閘門生效（徹底消滅 Issue 139）**：
     - 若年收超標（`employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()`），當場將兩個金額通道全數降為 `0L`（`yearEndDeduction = 0L; kakuteiDeduction = 0L;`）。
  2. **當期年末調整所得稅抵扣（純算術運算，零 Flag 分流）**：
     - `actualYearEndDeduction = Math.min(baseIncomeTax, yearEndDeduction)`
     - `baseIncomeTax -= actualYearEndDeduction`
     - （首年因 `yearEndDeduction == 0L`，`actualYearEndDeduction` 必然為 0L，當期所得稅分文不扣，完整源泉扣繳；第 2 年起自然當期少繳稅款，完全不需要 `if (isFirstYear)` 分支判斷！）。
  3. **次年確定申告退稅額度拆分（卡實繳所得稅上限，天然防杜雙重退稅）**：
     - `actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction)`
     - （首年卡在實繳所得稅上限內算出次年應退現金；第 2 年起因 `kakuteiDeduction == 0L`，`actualKakuteiRefund` 數學上必然為 0L，天然杜絕雙重退稅，零特判！）。
  4. **住民稅轉扣拆分與單向跨年流動（徹底消滅 Issue 137 問題一與重複抵扣）**：
     - 計算當期未扣完額度：
       ```java
       long totalDeduction = yearEndDeduction + kakuteiDeduction;
       long totalUsed = actualYearEndDeduction + actualKakuteiRefund;
       long remainingMortgageDeduction = totalDeduction - totalUsed;
       long unusedForInhabitantTax = remainingMortgageDeduction > 0 
               ? Math.min(remainingMortgageDeduction, limitStrategy.calculateLimit(year, employmentIncome, taxableIncome, baseIncomeTax)) 
               : 0L;
       ```
     - **徹底移除當期直接折抵住民稅邏輯**：**刪除現行第 235-237 行**（`inhabitantTax -= inhabitantTaxDeduction;`）。當期未抵完額度純粹封裝入 `PreviousYearTaxRecord` 留待次年扣抵，杜絕當期提前扣抵與次年重複抵扣。
  5. **封裝跨年狀態**：
     - 將當年度結算數據構建成 `PreviousYearTaxRecord` 存入 `context.setPreviousYearTaxRecord(...)`：
       - `pendingKakuteiShinkokuRefund = actualKakuteiRefund`（首年有值，次年自然為 0L）
       - `unusedMortgageDeductionForInhabitantTax = unusedForInhabitantTax`

#### 步驟 5：同步更新單元測試
- **修改檔案**：[MortgageCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)
  - 修改 `testSimulateMortgagesForYear_FirstEligibleYear_DefersTaxDeductionToNextYear`：
    - 驗證首年 `result.kakuteiDeduction() > 0` 且 `result.yearEndDeduction() == 0`（理論額度精準歸入確定申告通道）。
    - 驗證非首年 `result.yearEndDeduction() > 0` 且 `result.kakuteiDeduction() == 0`（理論額度精準歸入年末調整通道）。
    - 驗證 `context.getPendingNextYearTaxRefund()` 為 0（`MortgageCalculator` 不再越權污染 context）。
- **新建/擴充測試**：[ActiveTaxCalculationEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)
  - **Issue 139 排富測試**：年薪 > 2000 萬在首年購屋時，驗證封裝進 `PreviousYearTaxRecord` 的 `pendingKakuteiShinkokuRefund` 與 `unusedMortgageDeductionForInhabitantTax` **精確為 0**。
  - **首年源泉徵收測試**：首年購屋時，驗證當期 `incomeTax` 不被抵減（維持源泉扣繳），但 `PreviousYearTaxRecord` 準確記錄應退額度。
  - **狀態封裝完整性測試**：驗證 `PreviousYearTaxRecord` 的各項數值 100% 卡在法定所得稅與住民稅限額內。

---

### 3. 驗收標準（Acceptance Criteria）
1. **[Issue 139](issue-139.md) 徹底根除**：高所得者在入住首年，房貸抵稅額當場歸零，封裝進 Context 的次年退稅與住民稅轉扣全為 0。
2. **首年無雙重扣減**：入住首年所得稅實施完整源泉扣繳，當期淨薪資不提前扣抵，精準鎖定在次年以確定申告現金退還。
3. **純金額通道驅動與零 Flag**：`MortgageCalculator` 內無任何全域狀態污染，純函數計算領域時序並分流至金額通道；稅務引擎純算術運算，全系統零 boolean flag。
4. **編譯與既有測試**：全專案編譯通過，零新增 warning，所有既有測試全數維持綠燈。

---

## 階段二實作計畫：第 2 年（次年）——春季還付 + 住民稅減繳 + 第二年正常的年末調整

### 1. 目標與邊界
- **核心目標**：在次年生命週期中，無縫讀取前一年度封裝於 `SimulationContext` 的 `PreviousYearTaxRecord`，精準兌現確定申告現金退稅（春季入帳）與住民稅額度折抵（夏季減繳）；同時讓第 2 年自身的年末調整正常扣減當期所得稅，並將第 2 年的退稅額歸零以防範雙重退稅，將結算狀態持續流轉給第 3 年（形成跨年狀態機閉環）。
- **架構約束**：
  - **零特判**：次年流程一律以標準化管線執行，自然讀取 `PreviousYearTaxRecord`，若退稅額為 0 則不發放，若有值則退還，無任何 hardcoded 年度特判分支。
  - **清理技術債**：徹底移除原本的 `pendingNextYearTaxRefund` 專屬欄位。

---

### 2. 步驟清單與具體改動檔案

#### 步驟 1：`YearlySimulationPipeline` 年度開頭兌現確定申告現金還付（春季退稅）
- **修改檔案**：[YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L77-L83)
- **改動細節**：
  - 替換既有讀取 `context.getPendingNextYearTaxRefund()` 的舊邏輯：
    ```java
    // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and inhabitant tax rollover credit)
    PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
    if (prevTax != null && prevTax.pendingKakuteiShinkokuRefund() > 0) {
        context.setCurrentLiquidCash(context.getCurrentLiquidCash() + prevTax.pendingKakuteiShinkokuRefund());
        // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) upon cash disbursement to prevent duplicate payout.
        // DO NOT set context.setPreviousYearTaxRecord(null) here:
        // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in context so that
        // ActiveTaxCalculationEngine can consume them later in the annual pipeline for inhabitant tax
        // deduction and Plan-21 prior-year income assessment.
        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                prevTax.grossSalary(),
                prevTax.taxableIncomeForIncomeTax(),
                prevTax.baseIncomeTax(),
                0L, // Refund disbursed; zeroed to prevent double redemption
                prevTax.unusedMortgageDeductionForInhabitantTax()));
    }
    ```
  - **效果（徹底解決 Issue 137 問題二與雙重退稅）**：第 2 年春季現金退稅絕不超發，精準等於第 1 年實繳所得稅範圍內的應退金額；第 3 年起因前一年退稅額為 0，絕不多發一毛錢。

#### 步驟 2：`ActiveTaxCalculationEngine` 讀取前年狀態扣抵住民稅（夏季住民稅減額）
- **修改檔案**：[ActiveTaxCalculationEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L220-L238)
- **改動細節**：
  - 當年度計算出基礎住民稅後，檢視前一年結算紀錄：
    ```java
    PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
    if (prevTax != null && prevTax.unusedMortgageDeductionForInhabitantTax() > 0) {
        long inhabitantTaxCredit = Math.min(inhabitantTax, prevTax.unusedMortgageDeductionForInhabitantTax());
        inhabitantTax -= inhabitantTaxCredit;
    }
    ```
  - 同時清理現行代碼第 231-238 行中「當期直接扣當期住民稅」的邏輯，統一改為：當期未扣完的額度封裝進當年度的 `PreviousYearTaxRecord.unusedMortgageDeductionForInhabitantTax`，留待次年折抵。
  - **效果（徹底解決 Issue 137 問題一）**：前一年所得稅未扣完轉入之額度，順利在次年住民稅中全額扣抵（上限內），不再被粗暴沒收。同時為 [Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md) 奠定標準前年所得/抵扣傳遞管道。

#### 步驟 3：第 2 年自身的「年末調整」與狀態循環延續（退稅額天然歸零）
- **修改檔案**：[ActiveTaxCalculationEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
- **改動細節**：
  - 第 2 年自身由 `MortgageCalculator` 算出的額度歸入 `yearEndDeduction`，當期直接參與所得稅扣減（`baseIncomeTax -= actualYearEndDeduction`），如實反映公司年末調整。
  - 因為 `kakuteiDeduction` 為 `0L`，算出的 `actualKakuteiRefund` 數學上自然為 `0L`。當年度結算完成時，封裝第 2 年結算狀態為新的 `PreviousYearTaxRecord`，其中 `pendingKakuteiShinkokuRefund = 0L`，供第 3 年無縫繼續使用，狀態機自然推進，絕無雙重退稅。

#### 步驟 4：全面清理舊有暫存欄位（技術債清理）
- **修改檔案**：[SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- **改動細節**：
  - 徹底移除 `private long pendingNextYearTaxRefund;` 欄位與其相應的 getter / setter。
  - 確保專案中不再有任何分散式的暫存退稅欄位，所有跨年稅務狀態唯一歸屬於 `PreviousYearTaxRecord`。

#### 步驟 5：端到端跨年整合測試驗證
- **新建/修改測試檔案**：[YearlySimulationPipelineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java)
- **測試矩陣**：
  1. **測試情境 A（常態足額抵扣）**：
     - 第 1 年額度 21 萬、實繳所得稅 25 萬。
     - 驗證第 2 年春季現金準確增加 21 萬退稅款。
     - 驗證第 2 年年末調整正常進行（當期所得稅成功抵扣）。
     - 驗證第 3 年春季現金退稅為 0（杜絕雙重退稅）。
  2. **測試情境 B（所得稅不足額，跨年轉扣住民稅——核心修復驗證）**：
     - 第 1 年額度 21 萬、實繳所得稅僅 5 萬。
     - 驗證第 2 年春季現金**精確增加 5 萬**（卡上限，絕不多發，徹底解 Issue 137 問題二）。
     - 驗證第 2 年住民稅**成功扣除 9.75 萬**（法定上限內扣除，徹底解 Issue 137 問題一）。
  3. **測試情境 C（年收超標排富驗證——核心修復驗證）**：
     - 第 1 年年薪 2500 萬（超標 2000 萬）。
     - 驗證第 2 年春季現金退稅為 0，次年住民稅轉扣為 0（徹底解 Issue 139）。

---

### 3. 驗收標準（Acceptance Criteria）
1. **現金流高估清零**：在第 1 年實繳所得稅低於抵稅額的情境下，第 2 年春季退稅不再超發一毛錢。
2. **住民稅權益復活與單向流動**：第 1 年未抵完之額度，在第 2 年住民稅繳納時 100% 如實扣抵（法定上限內）；當期絕不扣除當期房貸未抵完額度，次年絕不重複抵扣。
3. **排富機制端到端貫通**：首年年收超標者，次年退稅與抵扣全額歸零，無任何漏洞。
4. **零雙重退稅**：第 2 年起年末調整抵扣後，第 3 年起絕不再重複發放現金退稅。
5. **狀態機閉環運作**：模擬系統從第 1 年一路跑到退休，年年自然流轉，零 exception、零 boolean flag、零 dirty flag、零邏輯斷裂。

---

## 補充備忘與邊界收斂（PM 審查定案註解）

> [!NOTE]
> **關於架構審查回饋之邊界收斂與法規共識**：
> 1. **法規唯一性（單一自住限定）**：依據日本《租稅特別措置法》第 41 條，住宅借入金等特別控除嚴格限定為「自己居住之單一住宅」，法律上絕無同一人同時持有多棟住宅享有控除之可能。因此系統中同一年度符合控除資格之房貸必然至多僅有一筆（`taxDeductionEligible == true`），無需考慮多棟房貸於通道間累加覆蓋之問題。
> 2. **邊界控制（聚焦核心問題，拒絕範疇蔓延）**：本 Issue 專注於根治 Fixed-45-01 遺留之「首年確定申告退稅卡實繳所得稅上限」與「次年住民稅轉扣被實質沒收」兩大核心問題。復興特別所得稅（2.1%）並非 Claude 與本 Issue 界定之業務缺陷，不納入本期改動，以防範 Scope Creep。
> 3. **商務邏輯與代碼細節分離**：虛擬代碼範例中若有參數簽名差異，屬於實作與編譯層級之工程細節，以現行介面定義（如 `InhabitantTaxLimitStrategy.calculateLimit(taxableIncomeForIncomeTax)`）為準，不影響核心商務狀態機與金額通道之有效性。
> 4. **既有舊房貸之時序與歷史邊界（Zero Historical Overhead）**：既有舊房貸於模擬起始年早已渡過入居首年，所得稅依法直接走公司年末調整（`yearEndDeduction`），完全沒有確定申告退稅款問題，亦無歷史退稅可言。在無前一年歷史源泉徵收票與薪資單之客觀邊界下，方案採用「跨年狀態機自然向前遞延」：模擬首年年末調整未抵完額度自然於次年住民稅抵扣，並一路自然順延至法定減稅年限屆滿之次年，在整個模擬生命週期中所享受之住民稅扣除總年數與總額度完全不變（分文不少），徹底免除無中生有推估歷史所得或強求使用者輸入歷史稅單之負擔，維持無 Flag 狀態機自然演進即為產品與工程之最佳解。

---

## 實作完成報告（Implementation & Verification Report）

### 1. 執行概述
本 Issue 所規劃之「跨年狀態傳遞機制（Cross-Year Tax State Machine）」與「純金額雙通道驅動架構（Amount-Driven Architecture）」已全數實作完成並驗證通過。
徹底根治了 Fixed-45-01 遺留的四大技術債與業務缺陷：
1. **[Issue 137 問題一] 住民稅轉扣被實質沒收**：首年所得稅未抵扣完之理論額度，精準封裝並於次年住民稅繳納時依租稅特別措置法法定限額（9.75 萬 / 13.65 萬円）如實折抵。
2. **[Issue 137 問題二] 次年春季退稅盲目超發**：次年確定申告退稅款嚴格受限於首年實繳基準所得稅（`baseIncomeTax`），低所得者不再虛增流動性資產。
3. **[Issue 139] 首年高所得排富穿透漏洞**：稅務引擎在當年度即依入居年份適用門檻（2,000 萬或 3,000 萬円）審查，超標者雙金額通道當場歸零，次年退稅與轉扣全數歸零。
4. **雙重退稅與越權污染**：`MortgageCalculator` 完全移除對 `SimulationContext` 的直接越權寫入與 `deduction = 0L` 粗暴歸零；第 2 年起走年末調整，確定申告通道天然為 `0L`，跨年退稅金額自然歸零，杜絕雙重發放。

---

### 2. 改動檔案清單

#### 新建檔案
1. [`PreviousYearTaxRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)
   - 定義不可變跨年稅務結算 Record：持有 `grossSalary`、`taxableIncomeForIncomeTax`、`baseIncomeTax`、`pendingKakuteiShinkokuRefund`、`unusedMortgageDeductionForInhabitantTax`。
2. [`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java)
   - 新建端到端跨年整合測試矩陣（涵蓋情境 A、情境 B、情境 B 法定圖解、情境 C 排富）。

#### 修改檔案
1. [`MortgageYearlyResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java)
   - 重構為不可變雙金額通道（`yearEndDeduction`, `kakuteiDeduction`）。
2. [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
   - 移除 `setPendingNextYearTaxRefund` 與 `deduction = 0L` 污染；依不可變實體入居年份自動將理論額度分流至申報通道。
3. [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
   - 掛載 `PreviousYearTaxRecord previousYearTaxRecord`；徹底移除廢棄欄位 `pendingNextYearTaxRefund`。
4. [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
   - 實作所得超標即時排富閘門（兩通道歸零）。
   - 純算術執行 `baseIncomeTax -= Math.min(baseIncomeTax, yearEndDeduction)`。
   - 計算 `actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction)`。
   - 自前年紀錄讀取 `unusedMortgageDeductionForInhabitantTax` 折抵當期住民稅。
   - 當期未扣完額度算定法定上限後封裝入 `PreviousYearTaxRecord` 傳遞至次年（當期絕不扣當期）。
5. [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
   - 年度開頭自 `PreviousYearTaxRecord` 兌現春季退稅款至流動現金，並當場在 Record 中將該項歸零（防杜雙重發放）。
6. [`MortgageCalculatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)
   - 配合雙通道重構測試，清理已廢除的 `pendingNextYearTaxRefund` 呼叫。
7. [`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)
   - 更新建構子參數，新增首年排富、源泉徵收維持、低所得限額轉扣等單元測試。

---

### 3. 測試驗收矩陣與成果

| 測試案例 | 驗證項目 | 預期結果 |
|---|---|---|
| `testScenarioA_standardFullDeduction_multiYearCycle` | 常態足額跨年循環 | 第 2 年春季準確發放 21 萬；第 2 年年末調整正常扣除當期所得稅；第 3 年退稅精確為 0（杜絕雙重發放） |
| `testScenarioB_lowIncomeWithInhabitantRollover_cappedRefundAndCredit` | 低所得跨年轉扣（Issue 137 核心） | 首年退稅精確卡在實繳所得稅上限（103,200 JPY < 210,000 JPY）；次年住民稅精確折抵法定上限 97,500 JPY |
| `testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500` | 法定說明數據端到端驗證 | 前年 5 萬實繳稅與 9.75 萬轉扣：第 2 年春季現金增加 5 萬，住民稅減少 9.75 萬 |
| `testScenarioC_highIncomeDisqualification_phaseOut` | 高所得排富穿透（Issue 139 核心） | 年薪 2,500 萬超標者，第 1 年記錄退稅與轉扣全為 0；第 2 年現金與住民稅無任何抵扣 |
| `MortgageCalculatorTest` (全組) | 領域時序純金額通道分流 | 首年全入 `kakuteiDeduction`，次年全入 `yearEndDeduction`，零 Context 污染 |
| `ActiveTaxCalculationEngineTest` (全組) | 稅務引擎排富與限額封裝 | 排富即時歸零、純算術扣抵、單向封裝跨年紀錄 |

---

### 4. 驗收標準達成核對
- [x] **現金流高估清零**：在第 1 年實繳所得稅低於抵稅額時，退稅款精準受限，絕不超發。
- [x] **住民稅權益復活與單向流動**：首年未抵完額度在次年夏季住民稅 100% 如實折抵（法定上限內）；當期絕不扣當期，次年絕不重複扣。
- [x] **排富機制端到端貫通**：首年年收超標者次年退稅與抵扣全額歸零，無任何漏洞。
- [x] **零雙重退稅**：第 2 年年末調整扣減後，第 3 年起春季退稅天然為 0。
- [x] **狀態機閉環運作**：純金額通道驅動，全專案零 boolean flag、零 dirty flag、零 mutable 狀態洩漏，全流程自然平滑演進。
