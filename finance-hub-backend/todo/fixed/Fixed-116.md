# Fixed 116：Grid Search選中「全額還清」時，回傳給前端的金額是`Long.MAX_VALUE`這個內部代號，不是剩餘本金；「Apply to Sandbox」把這個代號原封不動帶進Sandbox，導致Sandbox模擬持續失敗

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定：修正方向是讓系統顯示正確的房貸剩餘本金金額，不是`Long.MAX_VALUE`這種對使用者毫無意義的數字。

## 範圍界定（先講清楚，避免誤改到不相關的地方）
**這個bug只存在於Grid Search這個引擎的路徑上，跟GA（Genetic Algorithm）完全無關，修正時不要去動GA那邊的程式碼。**

- 會踩到這個bug的情境：（1）使用者在「AI Optimizer」畫面手動選「Grid Search」策略；或（2）選「Auto」，而後端依搜尋空間大小自動選中了Grid Search（`GlobalOptimizerServiceImpl.java:101-109`，門檻是`OptimizationConstants.MAX_GRID_SEARCH_SPACE`，房貸只有一筆、其他優化維度沒有全部打開時很容易落在這個門檻內）。只要滿足其中一種，且這次跑出的建議是「某筆房貸該在某一年全額還清」，就會觸發。
- 不會踩到這個bug的情境：使用者手動選「Genetic Algorithm」，或Auto模式因為搜尋空間過大自動切換去GA（同一段程式碼，`combinations > MAX_GRID_SEARCH_SPACE`時）。GA對「還款金額」的建模方式，從頭到尾都是`Chromosome.mutate()`（`Chromosome.java:147-153`）隨機生成、逐代微調出來的真實金額（50萬~500萬區間起始，見`Chromosome.java:151`），從來不會出現`Long.MAX_VALUE`這個代號，因為GA没有「全額還清」這個特殊概念，它只是把金額當一般基因去試各種數字組合。

## 為何「一個年份」可以對應到「一個確定的金額」（修正可行性的前提）
已確認：Grid Search目前對「全額還清」這件事的搜尋方式，是一維窮舉「哪一年」（`GridSearchEngine.java:116-128`，`gridSingleMortgageRepaymentYear`），沒有機制表達「分成兩次以上還款」；且Grid Search本身只支援單一房貸（既有by-design限制，不在這次修正範圍內）。這代表只要Grid Search選定了「哪一年」，這一年、這一筆房貸的剩餘本金金額，在數學上是唯一確定的一個數字，不會有「這一年可能對應好幾種還款金額」的歧義，修正的目標是把這個唯一確定的數字找出來、放進回應裡，不是要處理任何模糊或需要猜測的情境。

## 嚴重程度
Medium（不是每次都會發生，只在「Grid Search+建議全額還清+使用者點了Apply to Sandbox」三個條件同時成立時才會發生；但一旦發生，Sandbox會持續壞掉，使用者要自己發現並手動刪除異常紀錄才能恢復）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:59-69`（**問題根源**：Grid Search選中「哪一年全額還清」時，`GridSearchEngine`只窮舉年份、不窮舉金額，只把年份存進`gridSingleMortgageRepaymentYear`這個欄位；`ChromosomeMapper`要把這個決定轉換成`SimulationOverrides`物件時，找不到一個「剩餘本金」的數字可以填，於是用`Long.MAX_VALUE`這個代號填進`amount`，靠模擬引擎自己的夾值保護去表達「這一年付掉全部餘額」這個意思）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:406-410`（**真正的數字已經被算出來過、只是沒有被保留下來**：模擬引擎逐月計算時，這裡的`earlyPrincipal = balance`這一行，就是「這一年這個月、這筆房貸的真實剩餘本金」；`GridSearchEngine`每嘗試一個候選年份，都會透過`evaluator.evaluate()`觸發一次完整模擬，這裡都會被算一次，包括最終獲勝的那個候選年份——只是這個數字只存在於這次模擬呼叫的區域變數裡，算完就丟，沒有被傳回、也沒有被存進任何回應物件）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:82-84`（`evaluate()`裡其實拿到了完整的`List<AssetSimulationYearlyResult>`模擬結果，但目前只從裡面取最終身家淨值來算分數，其餘資訊（包括房貸相關的逐年結果）評估完就丟棄，沒有保留給`GridSearchEngine`或`ChromosomeMapper`使用）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java:43-63`（**這裡有一個已經做了、但只解決一半的防護**：`toResponse`方法會另外算出一個乾淨的`displayYear`欄位塞進`mortgageEarlyRepaymentYear`，方法上的註解寫著「so the frontend never sees a raw MAX_VALUE」——但這句話只對`mortgageEarlyRepaymentYear`這一個獨立欄位成立；同一個回應物件裡的`mortgageEarlyRepayments`欄位（第100-101行`mapMortgageEarlyRepayments`，逐筆轉換，完全没有過濾`Long.MAX_VALUE`），一樣帶著原始代號直接送給前端，這個方法上的註解沒有提到這一半，讀程式碼時容易誤以為「這裡已經處理過了、不用再管」，但實際上只處理了展示用的那一個欄位）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java:32,36`（`mortgageEarlyRepaymentYear`——乾淨版，只給畫面顯示用；`mortgageEarlyRepayments`——帶原始代號的完整清單，這個才是「Apply to Sandbox」實際會複製走的資料）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx:71,142`（結果卡片顯示用的是`result.mortgageEarlyRepaymentYear`這個乾淨版欄位，畫面上看起來完全正常；`isFullPayoffSentinel`這個判斷函式只用在「篩選要不要顯示金額明細列表」，不影響Apply的行為）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx:125-127`（**「Apply to Sandbox」實際複製的欄位**：`result.mortgageEarlyRepayments`，帶原始代號的那個，整包原封不動塞進Sandbox的`overrides`狀態，沒有經過任何過濾）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx:445`（Sandbox畫面上，如果偵測到`ev.amount >= Number.MAX_SAFE_INTEGER`，會顯示成唯讀的「Full Payoff (AI)」文字，不能編輯——這是目前唯一在使用者眼前能看到異常訊號的地方，但只是顯示層的權宜處理，並沒有解決「這筆資料實際上不能被正常送出重新模擬」這個根本問題）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java:60`（`MortgageEarlyRepaymentRequest.amount`型別是`Long`，`@Positive`驗證只擋負數跟0，不會擋這種超大數字）

## 現狀
Grid Search判斷「某筆房貸該在某年全額還清」時，後端內部用`Long.MAX_VALUE`（9223372036854775807）代表「這一年付掉全部餘額」，這個代號只在模擬引擎自己逐月計算分數時被正確攔截、夾成真實金額；但要把「最佳解」轉換成要回傳給前端的物件時，這個代號沒有被轉換回真實金額，直接原封不動送出。前端結果卡片因為用的是另一個有被安全處理過的欄位，畫面上完全看不出異常；但「Apply to Sandbox」按鈕複製的是帶原始代號的那個欄位，這個超大數字經過JavaScript浮點數精度失真、再送回後端`Long`型別反序列化失敗，Sandbox的模擬會持續失敗，直到使用者自己發現並手動刪除這筆異常紀錄。

## 具體錯誤情境
使用者只有一筆房貸，在「AI Optimizer」選「Grid Search」（或選「Auto」但搜尋空間小到自動選中Grid Search），優化結果顯示「Mortgage Full Repayment — Year 2035」，卡片上完全没有任何異常。使用者點下「Apply to Sandbox」，畫面切到Sandbox分頁，「Advanced Overrides」裡的還款金額欄位顯示唯讀的「Full Payoff (AI)」文字（因為前端偵測到`Number.MAX_SAFE_INTEGER`，見`simulation-override-panel.tsx:445`）；但只要頁面重新觸發模擬（例如使用者順手調了另一個滑桿），這筆帶著`Long.MAX_VALUE`的還款紀錄會被送回後端，後端反序列化`Long`型別時因數值超出範圍而失敗，Sandbox的模擬持續回錯誤，使用者必須自己發現這筆「Full Payoff (AI)」紀錄是問題根源，手動點垃圾桶圖示刪除它才能恢復。

## 修正方向：把`Long.MAX_VALUE`換成真實剩餘本金金額
目標很明確：不管中間用什麼機制算出來，最終回傳給前端、寫進`mortgageEarlyRepayments`的，必須是這筆房貸在那一年、那個月份的真實剩餘本金，而不是任何代號（不管是`Long.MAX_VALUE`還是別的sentinel數字）。

實作上，如何取得這個真實數字，有兩種可行的機制，這裡列出來給實際動手的人參考，不是要使用者在這兩者之間做產品判斷（純粹是工程手法選擇，兩者最終效果對使用者而言完全相同）：

1. **從已經跑過的模擬裡把這個數字撈出來、往外傳**：Grid Search每嘗試一個候選年份，`AssetSimulationFitnessEvaluator.evaluate()`都會觸發一次完整模擬，`MortgageCalculator`內部在做夾值的那一刻（`MortgageCalculator.java:406-410`）就已經算出了真實金額——包括最終獲勝的那個年份。只要把這個真實金額從模擬結果裡帶出來（例如透過`AssetSimulationYearlyResult`或新增一個欄位），一路傳回`GridSearchEngine`、`ChromosomeMapper`，就不需要另外重算。優點是不會有第二份重複的攤還計算邏輯；缺點是需要新增資料從模擬引擎深處往外傳遞的路徑，牽動的檔案較多。
2. **獨立重新計算一次**：房貸本金隨時間攤還的過程，只跟這筆房貸自己的條件（利率、還款方式、有沒有利率情境覆寫）有關，跟同一個chromosome裡其他基因（退休年齡、NISA目標等等）完全無關。所以也可以在確定「哪一年、哪筆房貸」之後，直接重用現有的房貸攤還邏輯，獨立算一次「不含任何提前還款的情況下，到這一年這個月為止，這筆房貸剩下多少」，不需要跑整個資產模擬引擎。優點是不用更動模擬引擎內部的資料傳遞路徑，改動範圍更集中；缺點是等於在系統裡多維護一份跟`MortgageCalculator`主迴圈邏輯對應的攤還計算，未來如果房貸攤還邏輯調整，這裡要記得同步更新（類似issue-113裡討論過的「兩份邏輯要同步維護」風險，性質相同）。

## 修正後的附帶影響：前端的sentinel偵測邏輯會變成死程式碼
一旦後端統一改成永遠回傳真實金額，前端目前用來偵測「這是不是全額還清代號」的邏輯——`global-optimizer-card.tsx:71`的`isFullPayoffSentinel`、`simulation-override-panel.tsx:445`的`ev.amount >= Number.MAX_SAFE_INTEGER`判斷——就再也不會被觸發（正常情況下不會有任何真實房貸餘額大到等於`Number.MAX_SAFE_INTEGER`）。實作這次修正時，應該一併把這兩處判斷邏輯清掉，不要留著一段永遠不會執行的死程式碼。

---

## 推薦最佳解方案：旁路自省回填與全專案審查（2026-10-01 複審補充）

### 一、全系統審查：是否還有其他 `MAX_VALUE` 滲漏至前端？
經對全專案進行全面檢索（後端 `MAX_VALUE` 與前端 `MAX_SAFE_INTEGER`），確認**本案為全系統唯一一處將哨兵代號傳送給前端的孤立案例**：
- 後端其餘 `MAX_VALUE` 皆為演算法純內部初始化比對（如 `bestOverallScore = -Double.MAX_VALUE`、搜尋空間邊界計算等），從未流入任何 API Response。
- 前端唯二檢查 `MAX_SAFE_INTEGER` 的地方（`global-optimizer-card.tsx:71` 與 `simulation-override-panel.tsx:445`），100% 就是當初為了防禦本案這個特定房貸漏洞而臨時寫的遮羞補丁。
- 本案修復後，全專案將再無任何此類隱患。

### 二、為何不採上述方案 1 或方案 2？
- **方案 2（外層獨立重算一份房貸攤還）有重大隱患**：房貸涉及元利/元金均等、5年規則、利率覆寫情境（Rate Scenarios）、獎金還款月及手續費等多重條件。在外層另寫攤還迴圈極易重蹈 Issue 113 的「雙軌維護」覆轍，只要任何條件不同步，算出的金額就會差幾十至幾百日圓，導致 Apply 到 Sandbox 時無法精確清零房貸。
- **方案 1（管線層層穿透傳遞）改動太重**：若要將數值從 `MortgageCalculator` 一路 return 經由 `MortgageYearlyResult` $\to$ `YearlySimulationPipeline` $\to$ `AssetSimulationYearlyResult` $\to$ `FitnessEvaluator` $\to$ `GridSearchEngine` $\to$ `Chromosome`，會強迫改動 6~7 個核心類別與 DTO，污染了本不需要關心房貸金額的搜尋引擎。

### 三、推薦方案：旁路自省回填（Bypass Introspection Backfill）
**核心思維：搜尋階段繼續享受夾值保護的高效，結算階段由 Context 旁路記錄並精準替換真金白銀。**

1. **後端夾值當下登記於 Context（零侵入、改動 1 行）**：
   - 在 [`MortgageCalculator.java:413-416`](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 執行夾值時：
     ```java
     if (earlyPrincipal > balance) {
         earlyPrincipal = balance;
     }
     ```
     當下夾值後的 `earlyPrincipal` 即為該筆房貸在該年度真實扣除的剩餘本金。
   - 僅需在已存在傳遞鏈中的 `SimulationContext` 增加一個暫存記錄結構（例如 `context.getExecutedEarlyRepayments().put(...)`），扣款當下記錄此金額。
2. **在 `GlobalOptimizerServiceImpl` 結算時精準替換（改動集中於 1 個類別）**：
   - 當 Grid Search 最佳解選出後（如 2035 年），以該解執行最後一次結算模擬，直接從該次執行的 `context` 取出精確扣除的真實本金金額（例如 `24,500,000L`）。
   - 在回傳 `SimulationOverrides` 前，將原本的 `Long.MAX_VALUE` 替換為該真實金額。
   - `GlobalOptimizerMapper` 與現有 API Response DTO 完全不需更動。
3. **前端順手卸妝（清理死代碼）**：
   - 移除 [`global-optimizer-card.tsx:71`](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx) 的 `isFullPayoffSentinel` 判定。
   - 移除 [`simulation-override-panel.tsx:445`](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx) 的 `ev.amount >= Number.MAX_SAFE_INTEGER` 判定及唯讀 "Full Payoff (AI)" 框，恢復為一般金額輸入框。
   - 使用者點擊「Apply to Sandbox」後，Sandbox 清楚顯示真實還款金額（如 ¥24,500,000），可自由微調，重新發起模擬時絕不溢位、完全正常運作。

---

## 實作清單（Checklist）

1. **後端 Context 輕量欄位**：
   - [x] [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)：新增單一 `long executedEarlyRepaymentPrincipal` 基礎欄位（0 物件分配，搜尋迴圈中 0 記憶體負擔）。
2. **計算機即時登錄真實本金**：
   - [x] [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)：在未來月份執行夾值（`earlyPrincipal = balance`）後，即時呼叫 `context.setExecutedEarlyRepaymentPrincipal(earlyPrincipal)` 登錄扣除的本金金額。
3. **優化器結算層替換真實金額**：
   - [x] [`GlobalOptimizerServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)：最佳解選出後，若有結清年份（`payoffYear != null`），以該解執行 1 次結算模擬，從 context 取得真實扣款本金（`actualAmount`）並直接填入單筆房貸還款清單中。
4. **API Mapper 清理哨兵依賴**：
   - [x] [`GlobalOptimizerMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java)：更新 `displayYear` 摘要欄位提取邏輯，直接從提前還款紀錄中取出年份，徹底移除 `o.amount() == Long.MAX_VALUE` 的依賴。
5. **前端清理死代碼與恢復輸入框**：
   - [x] [`global-optimizer-card.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)：移除 `isFullPayoffSentinel` 過濾器，正常展示提前還款真實金額。
   - [x] [`simulation-override-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)：移除 `Number.MAX_SAFE_INTEGER` 判定與唯讀 "Full Payoff (AI)" 框，恢復可編輯之數值輸入框。

---

## 修正結果報告

### 1. 根本問題與解決機制
- **問題回顧**：Grid Search 將「全額還清」以 `Long.MAX_VALUE` 傳入覆寫資料，原先預期僅在後端計算夾值，但該哨兵值未經替換便回傳至前端。前端 JavaScript 浮點數精度損失使其變為 `9223372036854776000`（大於 `Long.MAX_VALUE`），「Apply to Sandbox」再傳回後端時引發 Jackson 反序列化 400 錯誤。
- **解決機制（極簡單一數值回填）**：
  1. 針對 Grid Search「單一房貸、單一結清年份」的本質，在 `SimulationContext` 僅以單一 primitive `long executedEarlyRepaymentPrincipal` 承接，不引入任何 Map 物件，確保在萬次評估的搜尋迴圈中維持 0 物件配置與極致效能。
  2. 最佳解出爐後，僅當有結清年份時，執行 1 次結算模擬取得真實本金（例如 ¥24,500,000），並將該房貸的還款金額精準替換回傳。
  3. 前端同步移除 `Number.MAX_SAFE_INTEGER` 與 `isFullPayoffSentinel` 遮蔽邏輯，Sandbox 欄位回歸正常數值輸入，可供使用者檢視與編輯，且重新發起模擬不再拋錯。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10031  12.65G   4.00    14:20:19  
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
index 5dda8b5..ba0581f 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
@@ -68,11 +68,9 @@ interface OptimizerResultPanelProps {
 }
 
 function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelProps) {
-  const isFullPayoffSentinel = (amount: number) => amount >= Number.MAX_SAFE_INTEGER;
   const validMortgageRepayments = result.mortgageEarlyRepayments 
     ? Object.entries(result.mortgageEarlyRepayments)
-        .map(([mortgageId, events]) => [mortgageId, events.filter(e => !isFullPayoffSentinel(e.amount))] as const)
-        .filter(([_, events]) => events.length > 0)
+        .filter(([_, events]) => events && events.length > 0)
     : [];
 
   const allOptimized = config.optimizeRetirementAge && config.optimizePensionAge && config.optimizeIdecoAge && config.optimizeNisa && config.optimizeMortgage;
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx b/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
index 346539d..9466d7a 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
@@ -442,19 +442,13 @@ export function SimulationOverridePanel({
                       </div>
                       <div className="space-y-1 flex-1">
                         <Label className="text-[10px] text-muted-foreground">Amount (¥)</Label>
-                        {ev.amount >= Number.MAX_SAFE_INTEGER ? (
-                          <div className="flex h-7 w-full items-center justify-start rounded-md border border-input bg-muted px-3 py-1 text-xs text-muted-foreground font-medium shadow-sm">
-                            Full Payoff (AI)
-                          </div>
-                        ) : (
-                          <Input 
-                            type="number" 
-                            step="100000" 
-                            className="h-7 text-xs font-medium text-green-600 dark:text-green-400" 
-                            value={ev.amount || ''}
-                            onChange={(e) => handleUpdateRepayment(index, 'amount', e.target.value ? Number(e.target.value) : 0)}
-                          />
-                        )}
+                        <Input 
+                          type="number" 
+                          step="100000" 
+                          className="h-7 text-xs font-medium text-green-600 dark:text-green-400" 
+                          value={ev.amount || ''}
+                          onChange={(e) => handleUpdateRepayment(index, 'amount', e.target.value ? Number(e.target.value) : 0)}
+                        />
                       </div>
                       <div className="pt-4">
                         <Button
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10032  12.65G   3.92    14:20:40  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10033  12.64G   3.87    14:20:51  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index 9f10a153..a143dcd6 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -413,6 +413,9 @@ public class MortgageCalculator {
                     if (earlyPrincipal > balance) {
                         earlyPrincipal = balance;
                     }
+                    if (earlyPrincipal > 0) {
+                        context.setExecutedEarlyRepaymentPrincipal(earlyPrincipal);
+                    }
                     balance -= earlyPrincipal;
                     annualPayment += earlyPrincipal + earlyFee;
                 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
index 43a201d8..1475aa3c 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
@@ -114,6 +114,9 @@ public class SimulationContext implements Cloneable {
     private long[] mortgageCurrentExpectedPayments;
     private int[] mortgageMonthsSinceStart;
 
+    // Records the single early repayment principal executed during simulation
+    private long executedEarlyRepaymentPrincipal;
+
     @Override
     public SimulationContext clone() throws CloneNotSupportedException {
         SimulationContext copy = (SimulationContext) super.clone();
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
index 253026f1..c29329fc 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
@@ -94,7 +94,7 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
                 if (gridCombinations > OptimizationConstants.MAX_GRID_SEARCH_SPACE) {
                     throw new OptimizationNotSupportedException(
                             "Search space (" + gridCombinations + ") exceeds maximum limit ("
-                                    + OptimizationConstants.MAX_GRID_SEARCH_SPACE + ") for FORCE_GRID strategy.");
+                            + OptimizationConstants.MAX_GRID_SEARCH_SPACE + ") for FORCE_GRID strategy.");
                 }
                 engine = new GridSearchEngine(fitnessEvaluator, baseData);
                 break;
@@ -113,6 +113,26 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
 
         // 5. Map back to SimulationOverrides
         SimulationOverrides optimizedOverrides = chromosomeMapper.toOverrides(bestSolution);
+
+        Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> finalMortgageRepayments
+                = optimizedOverrides.mortgageEarlyRepayments();
+
+        Integer payoffYear = bestSolution.getGridSingleMortgageRepaymentYear();
+        if (payoffYear != null && baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
+            SimulationData finalRunData;
+            try {
+                finalRunData = baseData.cloneContext();
+            } catch (CloneNotSupportedException e) {
+                throw new IllegalStateException("Failed to clone SimulationData for final optimizer resolution", e);
+            }
+            assetSimulationEngine.runSimulationWithData(finalRunData, targetAge, optimizedOverrides);
+            long actualAmount = finalRunData.getBaseContext().getExecutedEarlyRepaymentPrincipal();
+
+            Long mortgageId = baseData.getAllMortgages().get(0).getId();
+            finalMortgageRepayments = Map.of(
+                    mortgageId, List.of(new SimulationOverrides.MortgageEarlyRepaymentOverride(payoffYear, actualAmount)));
+        }
+
         SimulationOverrides baseOverrides = command != null ? command.baseOverrides() : null;
 
         var userProfile = baseData.getBaseContext().getProfile();
@@ -160,7 +180,7 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
                 optimizedOverrides.targetRetirementAgeOverride(),
                 optimizedOverrides.pensionStartAgeOverride(),
                 optimizedOverrides.idecoStartAgeOverride(),
-                optimizedOverrides.mortgageEarlyRepayments(),
+                finalMortgageRepayments,
                 baseOverrides != null ? baseOverrides.fundOverrides() : null,
                 baseOverrides != null ? baseOverrides.mortgageRateScenarios() : null,
                 appliedProfileBaselineDetails);
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
index c0c2af48..2ce847c3 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
@@ -56,7 +56,6 @@ public interface GlobalOptimizerMapper {
                     .filter(java.util.Objects::nonNull)
                     .flatMap(l -> l.stream())
                     .filter(java.util.Objects::nonNull)
-                    .filter(o -> o.amount() != null && o.amount() == Long.MAX_VALUE)
                     .map(o -> o.year())
                     .findFirst()
                     .orElse(null);
diff --git a/finance-hub-backend/todo/pending fix/issue-116.md b/finance-hub-backend/todo/pending fix/issue-116.md
index 12ee7aec..388fe3ec 100644
--- a/finance-hub-backend/todo/pending fix/issue-116.md
+++ b/finance-hub-backend/todo/pending fix/issue-116.md
@@ -42,3 +42,65 @@ Grid Search判斷「某筆房貸該在某年全額還清」時，後端內部用
 
 ## 修正後的附帶影響：前端的sentinel偵測邏輯會變成死程式碼
 一旦後端統一改成永遠回傳真實金額，前端目前用來偵測「這是不是全額還清代號」的邏輯——`global-optimizer-card.tsx:71`的`isFullPayoffSentinel`、`simulation-override-panel.tsx:445`的`ev.amount >= Number.MAX_SAFE_INTEGER`判斷——就再也不會被觸發（正常情況下不會有任何真實房貸餘額大到等於`Number.MAX_SAFE_INTEGER`）。實作這次修正時，應該一併把這兩處判斷邏輯清掉，不要留著一段永遠不會執行的死程式碼。
+
+---
+
+## 推薦最佳解方案：旁路自省回填與全專案審查（2026-10-01 複審補充）
+
+### 一、全系統審查：是否還有其他 `MAX_VALUE` 滲漏至前端？
+經對全專案進行全面檢索（後端 `MAX_VALUE` 與前端 `MAX_SAFE_INTEGER`），確認**本案為全系統唯一一處將哨兵代號傳送給前端的孤立案例**：
+- 後端其餘 `MAX_VALUE` 皆為演算法純內部初始化比對（如 `bestOverallScore = -Double.MAX_VALUE`、搜尋空間邊界計算等），從未流入任何 API Response。
+- 前端唯二檢查 `MAX_SAFE_INTEGER` 的地方（`global-optimizer-card.tsx:71` 與 `simulation-override-panel.tsx:445`），100% 就是當初為了防禦本案這個特定房貸漏洞而臨時寫的遮羞補丁。
+- 本案修復後，全專案將再無任何此類隱患。
+
+### 二、為何不採上述方案 1 或方案 2？
+- **方案 2（外層獨立重算一份房貸攤還）有重大隱患**：房貸涉及元利/元金均等、5年規則、利率覆寫情境（Rate Scenarios）、獎金還款月及手續費等多重條件。在外層另寫攤還迴圈極易重蹈 Issue 113 的「雙軌維護」覆轍，只要任何條件不同步，算出的金額就會差幾十至幾百日圓，導致 Apply 到 Sandbox 時無法精確清零房貸。
+- **方案 1（管線層層穿透傳遞）改動太重**：若要將數值從 `MortgageCalculator` 一路 return 經由 `MortgageYearlyResult` $\to$ `YearlySimulationPipeline` $\to$ `AssetSimulationYearlyResult` $\to$ `FitnessEvaluator` $\to$ `GridSearchEngine` $\to$ `Chromosome`，會強迫改動 6~7 個核心類別與 DTO，污染了本不需要關心房貸金額的搜尋引擎。
+
+### 三、推薦方案：旁路自省回填（Bypass Introspection Backfill）
+**核心思維：搜尋階段繼續享受夾值保護的高效，結算階段由 Context 旁路記錄並精準替換真金白銀。**
+
+1. **後端夾值當下登記於 Context（零侵入、改動 1 行）**：
+   - 在 [`MortgageCalculator.java:413-416`](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 執行夾值時：
+     ```java
+     if (earlyPrincipal > balance) {
+         earlyPrincipal = balance;
+     }
+     ```
+     當下夾值後的 `earlyPrincipal` 即為該筆房貸在該年度真實扣除的剩餘本金。
+   - 僅需在已存在傳遞鏈中的 `SimulationContext` 增加一個暫存記錄結構（例如 `context.getExecutedEarlyRepayments().put(...)`），扣款當下記錄此金額。
+2. **在 `GlobalOptimizerServiceImpl` 結算時精準替換（改動集中於 1 個類別）**：
+   - 當 Grid Search 最佳解選出後（如 2035 年），以該解執行最後一次結算模擬，直接從該次執行的 `context` 取出精確扣除的真實本金金額（例如 `24,500,000L`）。
+   - 在回傳 `SimulationOverrides` 前，將原本的 `Long.MAX_VALUE` 替換為該真實金額。
+   - `GlobalOptimizerMapper` 與現有 API Response DTO 完全不需更動。
+3. **前端順手卸妝（清理死代碼）**：
+   - 移除 [`global-optimizer-card.tsx:71`](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx) 的 `isFullPayoffSentinel` 判定。
+   - 移除 [`simulation-override-panel.tsx:445`](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx) 的 `ev.amount >= Number.MAX_SAFE_INTEGER` 判定及唯讀 "Full Payoff (AI)" 框，恢復為一般金額輸入框。
+   - 使用者點擊「Apply to Sandbox」後，Sandbox 清楚顯示真實還款金額（如 ¥24,500,000），可自由微調，重新發起模擬時絕不溢位、完全正常運作。
+
+---
+
+## 實作清單（Checklist）
+
+1. **後端 Context 輕量欄位**：
+   - [x] [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)：新增單一 `long executedEarlyRepaymentPrincipal` 基礎欄位（0 物件分配，搜尋迴圈中 0 記憶體負擔）。
+2. **計算機即時登錄真實本金**：
+   - [x] [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)：在未來月份執行夾值（`earlyPrincipal = balance`）後，即時呼叫 `context.setExecutedEarlyRepaymentPrincipal(earlyPrincipal)` 登錄扣除的本金金額。
+3. **優化器結算層替換真實金額**：
+   - [x] [`GlobalOptimizerServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)：最佳解選出後，若有結清年份（`payoffYear != null`），以該解執行 1 次結算模擬，從 context 取得真實扣款本金（`actualAmount`）並直接填入單筆房貸還款清單中。
+4. **API Mapper 清理哨兵依賴**：
+   - [x] [`GlobalOptimizerMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java)：更新 `displayYear` 摘要欄位提取邏輯，直接從提前還款紀錄中取出年份，徹底移除 `o.amount() == Long.MAX_VALUE` 的依賴。
+5. **前端清理死代碼與恢復輸入框**：
+   - [x] [`global-optimizer-card.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)：移除 `isFullPayoffSentinel` 過濾器，正常展示提前還款真實金額。
+   - [x] [`simulation-override-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)：移除 `Number.MAX_SAFE_INTEGER` 判定與唯讀 "Full Payoff (AI)" 框，恢復可編輯之數值輸入框。
+
+---
+
+## 修正結果報告
+
+### 1. 根本問題與解決機制
+- **問題回顧**：Grid Search 將「全額還清」以 `Long.MAX_VALUE` 傳入覆寫資料，原先預期僅在後端計算夾值，但該哨兵值未經替換便回傳至前端。前端 JavaScript 浮點數精度損失使其變為 `9223372036854776000`（大於 `Long.MAX_VALUE`），「Apply to Sandbox」再傳回後端時引發 Jackson 反序列化 400 錯誤。
+- **解決機制（極簡單一數值回填）**：
+  1. 針對 Grid Search「單一房貸、單一結清年份」的本質，在 `SimulationContext` 僅以單一 primitive `long executedEarlyRepaymentPrincipal` 承接，不引入任何 Map 物件，確保在萬次評估的搜尋迴圈中維持 0 物件配置與極致效能。
+  2. 最佳解出爐後，僅當有結清年份時，執行 1 次結算模擬取得真實本金（例如 ¥24,500,000），並將該房貸的還款金額精準替換回傳。
+  3. 前端同步移除 `Number.MAX_SAFE_INTEGER` 與 `isFullPayoffSentinel` 遮蔽邏輯，Sandbox 欄位回歸正常數值輸入，可供使用者檢視與編輯，且重新發起模擬不再拋錯。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10034  12.66G   3.87    14:20:54  