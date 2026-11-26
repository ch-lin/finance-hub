# [FIXED] Issue 150：GA/Grid Search最佳化引擎可能自己產生模擬引擎本身會直接拒絕的年齡組合，導致整個最佳化請求直接崩潰

## 這份issue怎麼被發現的
這是這次Source Code Review針對GA/Grid Search最佳化引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:116-136`（模擬引擎本身的防呆：`targetRetirementAge < currentAge`或`pensionStartAge < targetRetirementAge`會直接丟出`IllegalArgumentException`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:81-83`（呼叫模擬引擎時完全沒有包任何`try/catch`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java:98,104`、`.../core/GridSearchEngine.java:136`（兩個引擎呼叫`evaluator.evaluate(...)`的地方都沒有任何例外處理）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:128-137`（每個基因欄位的突變/交配各自獨立進行，沒有跨欄位的聯合限制）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java:83-104`（巢狀迴圈，窮舉所有退休年齡×年金起領年齡的組合，完全沒有排除不合法組合）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java:31-36`（`MIN_RETIREMENT_AGE=50`、`MAX_RETIREMENT_AGE=80`、`MIN_PENSION_AGE=60`、`MAX_PENSION_AGE=75`，這些是跟使用者實際年齡完全無關的固定常數）

## 現狀
模擬引擎本身的防呆機制會對「退休年齡小於目前年齡」或「年金起領年齡小於退休年齡」這兩種不合法組合丟出例外——但從`AssetSimulationFitnessEvaluator`一路往上到GA引擎、Grid Search引擎、到最外層的`GlobalOptimizerServiceImpl`，整條呼叫鏈完全沒有任何地方攔截這個例外。這代表最佳化引擎自己在搜尋空間裡產生的某一個候選組合，只要不合法，就會讓整個最佳化請求直接中斷失敗，不是「這個候選分數比較低、繼續搜尋下一個」，是整個搜尋過程直接崩潰。

這份issue合併記錄兩種各自獨立就足以觸發的情境：
1. **退休年齡下限沒有考慮使用者實際年齡**：`MIN_RETIREMENT_AGE`固定是50，不會根據使用者目前的實際年齡調整。如果使用者實際年齡超過50（這個工具的核心客群本來就常見在50歲以上），只要勾選「最佳化退休年齡」，Grid Search從50歲開始試算的第一個候選值，就已經違反「退休年齡不能小於目前年齡」這個規則，立刻崩潰。
2. **退休年齡跟年金起領年齡完全獨立搜尋，沒有聯合限制**：Grid Search用巢狀迴圈窮舉退休年齡(50-80)跟年金起領年齡(60-75)的所有組合，但模擬引擎要求「年金起領年齡不能早於退休年齡」。因為退休年齡上限(80)大於年金起領年齡上限(75)，只要同時勾選「最佳化退休年齡」+「最佳化年金起領年齡」這個相當自然的組合，窮舉過程必然會在前幾輪就撞到一組不合法組合（例如退休年齡61、年金起領年齡60），100%會崩潰，不是機率性的。這個組合剛好命中Grid Search的搜尋空間門檻（31×16=496≤2000），預設AUTO策略下會直接走Grid Search，而不是走比較不會崩潰的GA。

## 為何是問題
使用者只是想問「我幾歲退休比較好」這種最自然的最佳化請求，結果因為自己的實際年齡剛好超過50歲，整個請求直接回傳錯誤，而不是給出一個合理的建議。同時勾選「退休年齡」+「年金起領年齡」一起最佳化，也是相當常見的使用情境，但目前的實作保證會讓這個組合崩潰。這不是「最佳化引擎給出比較差的答案」，是「最佳化引擎完全無法運作」，而且觸發門檻很低，不需要刻意湊出邊緣情境。

現有測試`GlobalOptimizerServiceTest.testOptimize_RunsGA_WhenHighDimensional`剛好用的是`new OptimizationConfig(true, true, true, true, false)`這組設定(退休年齡+年金起領年齡一起打開)，但因為測試裡的`fitnessEvaluator`是mock對象，不會真正呼叫到模擬引擎的防呆邏輯，所以這個測試完全沒有抓到這個問題。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：在評估層攔截例外，給不合法候選一個很差的分數**
- 做法：在`AssetSimulationFitnessEvaluator.evaluate()`裡包一層`try/catch`，攔到`IllegalArgumentException`時回傳一個很低的分數（跟現有的破產懲罰分數同樣的處理模式）。
- 優點：改動範圍小，直接重用現有的「懲罰分數」模式。
- 缺點：攔截範圍廣泛的`IllegalArgumentException`，可能把真正的呼叫端錯誤也一併吞掉、誤判成只是候選不合法；Grid Search可能會呼叫這個方法數千次，每次都包一層例外處理，有一定的效能負擔。

**方向B：讓基因產生/突變的那一刻就先夾在合法範圍內**
- 做法：讓`Chromosome.mutate()`/`crossover()`以及Grid Search的迴圈邊界，知道使用者目前的實際年齡，退休年齡下限直接取`max(MIN_RETIREMENT_AGE, 目前年齡)`；同時讓年金起領年齡的下限直接跟著退休年齡的候選值連動（≥退休年齡），而不是兩個完全獨立的範圍。
- 優點：從根本上避免產生不合法組合，不會浪費運算資源去跑一個注定失敗的候選。
- 缺點：改動範圍比較大；Grid Search的年金起領年齡內層迴圈邊界會變成依賴外層退休年齡的動態值，會讓現有的「預估搜尋空間組合數」計算邏輯（跟[Fixed-86](../fixed/Fixed-86.md)已經處理過的off-by-one問題同一類風險）需要同步調整。

**方向C：在呼叫模擬引擎之前先做一次便宜的預先過濾**
- 做法：在兩個引擎呼叫`evaluate`之前，先檢查「年金起領年齡是否小於退休年齡」（以及「退休年齡是否小於目前年齡」），不合法就直接給最差分數，完全不去跑那次昂貴的多年模擬。
- 優點：比方向A更省資源，不會真的啟動一次模擬運算才發現不合法。
- 缺點：這個業務規則原本只定義在模擬引擎（`AssetSimulationEngineImpl`）這一處，方向C會讓這個規則在兩個最佳化引擎裡各複製一份，之後如果規則本身有調整，有兩個以上的地方需要同步修改。

---

## 決策方向

經深入分析與架構評估，原提案評估與最終定案如下：

### 1. 原提案方向審查與否決原因
- **方向 A（僅在評估層包 `try-catch` 補洞）：不予採用**
  - **缺陷**：這是在「放任 AI 產生違規組合踩地雷，踩爆了再去幫它收屍打零分」。Grid Search 或 GA 可能產生數百組明顯違規的組合（例如 55 歲的人試算 50 歲退休），白白浪費大量的 CPU 模擬時間去跑注定失敗的運算。此外，寬鬆捕捉 `IllegalArgumentException` 容易遮蔽底層其他真實的程式錯誤。
- **方向 B（Claude 原始描述的過度複雜化版本）：予以修正重塑**
  - Claude 原文將動態邊界描述得過於沈重（過度擔憂組合數公式與巢狀迴圈複雜度），但實際上**「上限既然能卡死，下限本來就該動態卡好（Clamp / 夾值）」**。直接從源頭約束範圍，既不浪費運算次數，又最符合業務邏輯。
- **方向 C（每次模擬前寫 if 預先過濾）：不予採用**
  - 治標不治本，仍會產生無效組合後再拋棄。

---

### 2. 採納方案：方向 D（源頭動態卡下限 ＋ 評估層兜底安全網）

#### 核心原則：
> **「上限既然能卡，下限就動態卡好；只在合法範圍內跑，不浪費任何一次無效搜尋。」**

#### 具體系統實作規劃：
1. **年金與退休年齡下限定義準則（核心防線）**：
   - **退休年齡下限**：`Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge)`（確保不小於法定 50 歲且不小於當前年齡）。
   - **年金起領年齡下限**：`Math.max(OptimizationConstants.MIN_PENSION_AGE, candidateRetirementAge)`（**雙重防線**：確保不小於法定 60 歲 `MIN_PENSION_AGE`，且不早於退休年齡，兩道鎖取最大值，絕不遺漏任何一方）。

2. **種子建構卡下限（`GlobalOptimizerServiceImpl.java:buildSeedChromosome`）**：
   - 退休年齡下限卡在現齡與預設值：
     `seed.setRetirementAge(Math.max(Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge), seed.getRetirementAge()))`
   - 年金年齡下限同時卡在法定 60 歲與退休年齡：
     `seed.setPensionStartAge(Math.max(Math.max(OptimizationConstants.MIN_PENSION_AGE, seed.getRetirementAge()), seed.getPensionStartAge()))`

3. **網格搜尋動態卡下限（`GridSearchEngine.java`）**：
   - 退休年齡迴圈下限：`int minRetire = Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge)`
   - 年金年齡迴圈下限：`int minPension = Math.max(OptimizationConstants.MIN_PENSION_AGE, current.getRetirementAge())`
   - 從源頭保證產出的每一個候選解皆 100% 合法，完全消除時光倒流與時序錯亂問題。

4. **遺傳演算法突變動態卡下限（`Chromosome.java:mutate`）**：
   - 退休年齡突變範圍：下限綁定 `Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge)`
   - 年金年齡突變範圍：下限綁定 `Math.max(OptimizationConstants.MIN_PENSION_AGE, retirementAge)`

5. **搜尋空間安全預估對齊（`GlobalOptimizerServiceImpl.java:calculateSearchSpace`）**：
   - 退休年齡搜尋選項數改為 `Math.max(1, OptimizationConstants.MAX_RETIREMENT_AGE - minRetire + 1)`，真實反映有效搜尋組合數。

6. **評估層兜底安全網（`AssetSimulationFitnessEvaluator.java`）**：
   - 作為最後防線，若候選方案有邊界未對齊，自動 clamp 夾值修正（`targetRetirementAge = Math.max(currentAge, ...)`、`pensionStartAge = Math.max(Math.max(MIN_PENSION_AGE, targetRetirementAge), ...)`），徹底杜絕任何未處理之 `IllegalArgumentException` 導致 HTTP 請求崩潰。

---

## 修正執行報告

### 修正檔案清單
1. [`GridSearchEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)：
   - 新增儲存 `currentAge`。
   - 退休年齡迴圈下限動態卡住 `Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge)`。
   - 年金起領年齡迴圈下限動態卡住 `Math.max(OptimizationConstants.MIN_PENSION_AGE, candidateRetire)`。
2. [`Chromosome.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)：
   - `mutate` 方法中退休年齡下限綁定 `Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge)`。
   - `mutate` 方法中年金起領年齡下限綁定 `Math.max(OptimizationConstants.MIN_PENSION_AGE, candidateRetire)`。
   - `crossover` 方法中交配產出之子代染色體強制檢查並夾值 `child.pensionStartAge >= Math.max(MIN_PENSION_AGE, child.retirementAge)`。
3. [`GlobalOptimizerServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)：
   - `buildSeedChromosome` 種子產生時，退休年齡卡下限至現齡，年金起領年齡卡下限至 `Math.max(MIN_PENSION_AGE, seed.getRetirementAge())`。
   - `calculateSearchSpace` 依據使用者實際年齡計算動態退休年齡維度空間數。
4. [`AssetSimulationFitnessEvaluator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java)：
   - 於 `evaluate` 呼叫模擬引擎之前，進行雙重防線兜底 clamp 夾值防護，杜絕任何未處理之 `IllegalArgumentException` 拋出。
5. **單元測試補充與強化**：
   - [`ChromosomeTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/ChromosomeTest.java)：新增突變現齡下限、年金雙重下限、及交配連動防護測試。
   - [`GridSearchEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java)：新增網格搜尋下限連動測試。
   - [`AssetSimulationFitnessEvaluatorTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluatorTest.java)：新增評估層兜底 clamp 防護專屬單元測試。



