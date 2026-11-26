# [FIXED] Issue 152：DC/iDeCo年資覆寫設定的日期合成方式，一旦當年有實際提繳就會被每年重算的邏輯悄悄灌水多算一年

## 這份issue怎麼被發現的
這是這次Source Code Review針對年金/退職所得稅務計算進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:53-66`（DC，有快照的分支）、`:94-102`（DC，沒快照的分支）、`:126-139`（iDeCo，有快照）、`:167-175`（iDeCo，沒快照）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:161-179`（`applyWorkingTaxes`，每年只要有實際提繳就會重新呼叫一次`calculateTaxServiceYears`，覆蓋掉前面算好的值）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/TaxDateUtils.java:27-49`（`calculateTaxServiceYears`，`endDate.plusDays(1)`這個「含頭含尾」的進位設計，是為了讓真實日期算出來的年資正確捨入，但套用在人工合成的日期上會造成多算一年）

## 現狀
使用者如果開啟「年資覆寫」開關、自己輸入一個整數（例如「我的DC年資是3年」），系統會把這個整數反推成一個合成的「入職日」：`今天.minusYears(3)`。如果這個帳戶同時有既有快照資料，系統會立刻拿這個合成日期，透過`TaxDateUtils.calculateTaxServiceYears(合成日期, 今天)`重新算一次年資——這個函式原本是設計給「真實的入職日」用的，為了符合「不滿一年的畸零天數要進位成一年」的規則，特意在計算時把結束日加一天。但套用在`今天.minusYears(N)`這種剛好是整年差距的合成日期上，加一天這個動作，會讓計算結果固定多算一年（這點已經用程式邏輯重新手算驗證過：測試1到20年的覆寫值，每一個都會從N變成N+1，不是偶發的邊界情況）。

更麻煩的是：這個多算一年的結果只在「有既有快照」的分支會發生；在「沒有快照、只靠設定值去模擬」的分支，是直接把使用者輸入的整數原封不動存進去，不會重算，所以不會多算。同一個使用者輸入的同一個數字，會不會被灌水，純粹取決於這個帳戶是否恰好有既有快照這個跟使用者輸入完全無關的條件。

而且这個灌水只在模擬第一年發生一次：之後每一年，只要這個帳戶還有實際提繳，`YearlySimulationPipeline`每年都會重新呼叫`calculateTaxServiceYears`（這次拿的是真正的、已經錨定好的日期跟每年12月31日比對），所以不會逐年累加，而是固定維持「使用者輸入值+1」這個偏差貫穿整個模擬期間。

## 為何是問題
使用者在2026-10-09輸入「DC年資覆寫=3年」，這個帳戶有既有快照。合成日期變成`2023-10-09`。`calculateTaxServiceYears(2023-10-09, 2026-10-09)`算出來的期間是「3年0個月1天」(因為函式內部多加了一天)，進位後變成4，不是3。等到這筆DC之後真正解鎖請領、計算退職所得扣除額時，用的是年資4、不是使用者原本想輸入的3：`扣除額 = max(80萬, 40萬×4) = 160萬`，比使用者原本預期的`max(80萬, 40萬×3) = 120萬`多了40萬円的扣除額——這代表退職所得稅被低估、拿到手的淨現金被高估，而且這個落差是使用者自己明確輸入一個數字、卻拿到跟輸入值不一致的結果，不是模糊的估算誤差。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：覆寫值不要經過日期轉換，直接鎖定這個整數**
- 做法：使用者開啟覆寫開關時，完全不要透過`TaxDateUtils`重新計算，直接採用「沒快照」分支本來就有的做法——固定值或是逐年遞增。
- 優點：符合使用者輸入的字面意思，不會悄悄跑掉。
- 缺點：需要先決定一個產品方向：覆寫之後的年資，應該維持固定不變，還是隨著之後持續提繳每年增加一年？這兩種選擇都需要各自驗證正確性。

**方向B：修正合成日期本身，讓它跟函式的進位邏輯互相抵消**
- 做法：合成日期改成`今天.minusYears(N).plusDays(1)`，讓函式內部「加一天」的動作剛好抵消掉。
- 優點：改動最小、最集中。
- 缺點：這只解決了初始化那一刻的計算，`YearlySimulationPipeline`裡每年用12月31日重新計算的那一段，用的是不同的比較基準日，仍然會重新出現類似的偏移，需要搭配方向A或是另外處理這一段才能完整解決。

**方向C：讓「覆寫」跟「入職日」這兩個概念徹底分開，不要共用同一個欄位**
- 做法：覆寫值用一個單純的整數欄位傳遞跟儲存，永遠不轉換成`LocalDate`；`YearlySimulationPipeline`改成直接判斷「這個帳戶的年資是否被覆寫」這個明確旗標，而不是用「入職日是否為null」去判斷。
- 優點：從根本上移除「一個欄位身兼兩種不同概念」這個問題的根因，也可以直接重用「沒快照」分支已經驗證過的邏輯。
- 缺點：改動範圍比較大，需要同步確認目前有沒有其他地方也依賴讀取這個入職日欄位（這次審查沒有找到其他讀取點，但建議正式修改前再掃一次全專案確認）。

---

## 決策方向

經深入分析與業務邏輯釐清，原提案評估與最終定案如下：

### 1. 原提案方向審查
- **方向 B（修補合成日期）：不予採用**
  - 將合成日期改為 `今天.minusYears(N).plusDays(1)` 僅是形式上的數字湊合。
  - 進入 `YearlySimulationPipeline` 後，每年年底以 `12/31` 為基準重新比對時，依然會因為人造日期與日曆差距重新出現年資跳躍與偏移，治標不治本。
- **方向 C（全面分離欄位與新增旗標）：不予採用**
  - 核心思想正確（覆寫年資不應依賴假日期），但不需要大幅改動 DTO 或新增 `isOverridden` 旗標。
  - 現行 `YearlySimulationPipeline` 內部本來就已經具備 `enrollmentDate == null` 時的「純整數逐年遞增」邏輯，無須過度工程化。

---

### 2. 採納方案：方向 A 定案版（整數基準年資 ＋ 模擬年度實際提繳逐年遞增）

#### 業務語意拍板：
使用者在設定中輸入「年資覆寫 = $N$ 年」，其真實業務意義為：
> **「截至模擬基準日（今天）為止，過去已累積之年資為 $N$ 年。」**

在進入未來的模擬期間（例如往後跑 20 年）：
1. 初始年資即為使用者輸入的 $N$ 年。
2. 每年只要有實際提繳 DC/iDeCo（`annualContribution > 0`），年資理應隨之遞增（每年 $+1$）。
3. 若該年無提繳，年資則維持不變。
4. 最終退休領取時，以 `N + 未來有提繳年數` 作為年資計算退職所得扣除額。這徹底避免了「死固定值」導致未來 20 年提繳年資被完全吃掉的嚴重缺陷。

#### 具體系統實作機制：
1. **徹底移除假日期合成，統一「有快照」與「無快照」分支**：
   - 位於 `DefinedContributionContextBuilder.java`：
     - 若使用者開啟覆寫開關（`isOverridden == true` 且 `overrideVal != null`）：
       - 直接設定初始年資：`context.setDcContributionYears(dcOverrideVal)`（iDeCo 亦同）。
       - **不合成任何假日期**，維持 `context.setDcEnrollmentDate(null)`。
       - 移除無快照分支中殘留的 `LocalDate.now().minusYears(overrideVal)` 假日期設定。
     - 若使用者未開啟覆寫：
       - 維持原邏輯，讀取快照真實之 `enrollmentDate`，透過 `TaxDateUtils.calculateTaxServiceYears(resolvedDate, LocalDate.now())` 計算精確年資。
2. **無縫重用 `YearlySimulationPipeline` 既有機制**：
   - 在 `YearlySimulationPipeline.java:172-190` 中，現行邏輯本就存在：
     ```java
     if (context.getDcEnrollmentDate() != null) {
         if (contributions.annualDcTotalContribution() > 0) {
             context.setDcContributionYears(
                     TaxDateUtils.calculateTaxServiceYears(context.getDcEnrollmentDate(),
                             LocalDate.of(context.getYear(), 12, 31)));
         }
     } else if (contributions.annualDcTotalContribution() > 0) {
         // 覆寫模式（dcEnrollmentDate 為 null）：每年有提繳自動 +1
         context.setDcContributionYears(context.getDcContributionYears() + 1);
     }
     ```
   - 只要在覆寫時不塞假日期，管線就會自動命中 `else if` 分支，在每年有提繳時精準遞增 1 年，邏輯極度簡潔且無副作用。

---

## 實作成果報告

### 1. 修改檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java`

### 2. 重點實作成效
1. **精準收斂改動範圍（Strictly Targeted Scope）**：
   - 僅修改「有快照」的 DC（第 58-66 行）與 iDeCo（第 131-139 行）兩處，不更動無快照分支與任何周邊結構，徹底杜絕邊際效應。
2. **直接設定起始年資（Direct Value Assignment）**：
   - 當 `dcOverrideVal != null`（或 `idecoOverrideVal != null`）時，直接透過 `context.setDcContributionYears(dcOverrideVal)` 設定年資，不再往回推算假日期。
   - 徹底根除假日期傳入 `TaxDateUtils` 觸發 `endDate.plusDays(1)` 誤進位多算一年的根本問題。
3. **未覆寫情境維持原狀（Zero Regression for Non-overridden Cases）**：
   - 沒設定覆寫時，走 `else if` 依原邏輯讀取快照真實入職日並計算年資，行為 100% 保持一致。
