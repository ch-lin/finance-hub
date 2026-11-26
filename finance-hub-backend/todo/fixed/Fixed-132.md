# [Fixed] Feature Requirement 132：全面廢除「補算到現在」機制，模擬起點改成「使用者最後一次上傳快照的那個時間點」本身，不再嘗試推算到今天

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定一個比issue-104、issue-105原本討論範圍更根本的原則：**系統不做任何時間推算，一律把使用者最後一次上傳的那筆資料，當作模擬要開始的那個時間點本身——不是「今天」**。這個原則適用於所有資產類別，沒有例外，包含房貸（issue-104原本只打算修正房貸補算「用錯利率」這個子問題，現在改成連補算這個動作本身都要移除；issue-105原本把資產分成「可補算（NISA/iDeCo）」跟「不能補算（銀行現金/公司股票/年金/DC企業型）」兩組，現在六種資產全部不補算）。

## 判斷依據（使用者的推論過程）
使用者這個決定，是從issue-105原本「NISA/iDeCo可以補算、因為定期定額金額系統本來就有記錄」這個論點，一路往回推翻掉的。推翻的理由分五層：

**第一層：責任歸屬。** 系統用公式去推算一段使用者沒有告訴它的事實（例如「使用者最後上傳的快照到今天這段期間，到底發生了什麼」），算錯了，責任在開發者身上，不是使用者的。這跟「AI本來就會犯錯，使用者能接受」是完全不同的風險等級——系統用確定性的公式去斷言一件沒有依據的事，一旦出錯沒有任何可以推卸的理由。使用者的原話：「因此前提條件很清楚。使用者upload什麼就是現在的事實。」「如果是AI的話還可以一句話AI會出錯。系統的話，系統會出錯，那誰敢用。」

**第二層：找不到一個可靠的「真正時間點」可以當補算起點。** 即使想做補算，也沒有辦法確定「使用者實際上傳的這筆資料對應到哪個真實時間點」——檔案內文沒有時間欄位的資料，沒辦法只靠檔案時間戳記判斷（使用者可能分好幾天下載不同東西，也可能很晚才匯入早就下載好的舊資料）。這代表就算承認「補算本身在數學上辦得到」，也沒有一個站得住腳的輸入可以餵給這個補算公式。

**第三層：就算時間點問題解決了，金額本身也對不上，要對上就是在查帳。** 使用者指出兩個具體情境，說明「補算」會不可避免地演變成會計查核工作：
- 銀行扣款去證券商的金額，不保證等於實際買進NISA的金額（可能有手續費、匯率轉換、分批買進等差異），系統沒有逐筆交易明細，無法確認「這筆從銀行扣出的錢」跟「NISA裡增加的這筆錢」是不是同一筆、金額是否一致。
- 使用者可能因為某個銀行帳戶餘額不足以支付房貸，臨時從另一個銀行帳戶（甚至是海外帳戶）調現金過去。這種情況下，要正確補算，必須同時追蹤「A帳戶減少多少」、「B帳戶增加多少」、匯率轉換（如果跨國）、以及這筆資金移動跟房貸扣款之間的時間關係——這已經不是「補算」，是在做跨帳戶資金流向的全盤稽核。
使用者的原話：「怎麼補算基本上都是不合理的」「那一連串要補算準的話，我去國稅局工作吧」。

**第四層：「對帳」不是這個工具的目的。** 使用者明確說「我管你銀行跟iDeCo對不起來幹嘛」——這個工具存在的目的是評估「會不會現金破產」，不是會計對帳軟體，不需要讓每個帳戶的時間點互相校準到分毫不差。只要在使用者按下模擬按鈕的那一刻，誠實地用他當下提供的資料去推算未來，就已經達成這個工具真正的目的。使用者的原話：「一個現金破產模擬變成在查現金流流動……這是沒事找事做嗎？整個系統不幹了把Amazon匯出然後丟給NotebookLM分析還比較快。」

**第五層（在這次review過程中額外被發現、進一步印證這個決定的技術事實）：現有的房貸補算機制`fastForwardMortgagesToPresent`，補算時只會減少房貸本金，完全沒有同步減少銀行現金——這代表這個機制本身已經在產生「憑空印鈔」的淨資產虛增，不只是issue-104原本描述的「用錯利率」這一個子問題。這個發現不是推翻這次決定的理由，而是進一步確認了「補算機制」這整個類別的方法論本身就站不住腳，不只房貸、不只這一個實作細節有問題。

## 相關檔案

### 房貸補算機制：整個移除
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:133-289`（`fastForwardMortgagesToPresent`整個方法，連同內部用到的`newRate`/`earlyPrincipal`判斷邏輯——這整個方法要刪除，不是修正方法內部的某個判斷）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:112`（唯一的呼叫端：`mortgageSimulationService.fastForwardMortgagesToPresent(context, allMortgages, nowMonth, overrides);`，這一行要刪除）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java:187,239`（這兩個測試是issue-104修正時新增的，專門測試`fastForwardMortgagesToPresent`的行為，方法移除後這兩個測試要跟著刪除，不是改成測試別的東西）
- **已確認移除後不會留下遺留問題**：`fastForwardMortgagesToPresent`裡會累加的`monthsSinceStart[i]++`（`MortgageCalculator.java:164`），只影響補算期間的計數；這個計數器的初始值是在`initializeMortgages`（`MortgageCalculator.java:61-69`）裡獨立根據`balanceRecordDate`跟`startDate`的日期差直接算出來的，不依賴補算動作本身，所以移除補算不會讓這個計數器的初始值跟著跑偏。`simulateMortgagesForYear`（未來模擬主迴圈）裡自己的`monthsSinceStart[i]++`（`MortgageCalculator.java:331`，只在`isFutureMonth`時累加）要保留，跟補算無關，不要連這個也一併刪掉。

### NISA、iDeCo補算：原本issue-105提案的補算邏輯，不要開發
`issue-105`裡原本規劃要新增的補算邏輯（在`NisaContextBuilder`跟`DefinedContributionContextBuilder`裡新增類似房貸補算的邏輯）**不要實作**，`issue-105`整份文件改成只保留「資產資料新鮮度提示」這一半的方向（見下方）。

### 銀行現金、公司股票、年金、DC企業型：維持現狀（這幾類原本就沒有補算機制，不受影響）
這幾類資產本來就沒有補算邏輯，不需要任何改動：
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java:184-199`（銀行現金、公司股票起始餘額）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/PensionContextBuilder.java:42-64`（年金）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:46-119`（DC企業型那一段）

## 想要的行為
1. 所有六種資產類別（房貸、銀行現金、NISA、iDeCo、公司股票、年金、DC企業型），模擬起點一律直接採用「使用者最後一次上傳的那筆快照」本身的數字，不做任何時間推算，不嘗試補到「今天」。
2. 畫面上要讓使用者清楚看到每一筆資產快照的日期（新鮮度提示），讓使用者自己判斷要不要先重新匯入最新資料再跑模擬——這一半的需求維持[issue-105](issue-105.md)原文裡「資產資料新鮮度提示」這部分不變，只是把同一份文件裡「NISA/iDeCo補算」那一半的方向整個撤銷。
3. 房貸的模擬起點說明文案（如果有的話）、或任何畫面上跟「補算到今天」相關的提示文字，都要跟著移除或改寫，不要讓使用者看到跟現在行為不符的舊文案。

## 跟issue-104、issue-105的關係（這兩份文件需要同步處理，不是獨立存在）
- **issue-104**（房貸補算誤用Sandbox假設利率）：Gemini已經依照issue-104原本的方向修正了`fastForwardMortgagesToPresent`內部的利率來源判斷（改用`m.getInterestRate()`而不是`overrides`），這個修正本身的邏輯是對的，但因為這次決定要把整個補算機制移除，issue-104描述的那個子問題會隨著整個方法被刪除而自然消失，不需要單獨保留這個修正。issue-104應該標記為「因為feature-requirement-132，整個問題已經不存在，不需要單獨驗證」。
- **issue-105**（除房貸外的資產補算）：原本的NISA/iDeCo補算提案已被這次決定推翻（見上方「判斷依據」），issue-105應該改寫成只保留「資產資料新鮮度提示」這一個方向，移除所有跟NISA/iDeCo補算相關的技術細節跟修正方向段落。

---

## 白話總結與具體實作範圍（TL;DR Scope）

### 一、用白話說：132 到底在做什麼？
**核心一句話：把系統自作聰明「幫使用者補算過去幾個月」的機制徹底砍掉，使用者上傳什麼就是什麼。**

- **過去的荒謬做法（補算機制）**：
  如果使用者上次上傳房貸是 3 個月前，系統在跑模擬時，會自作聰明執行 `fastForwardMortgagesToPresent`，擅自幫使用者扣掉這 3 個月的房貸本金。
  - **嚴重的 Bug**：房貸本金被扣掉了，但銀行帳戶的現金**完全沒有扣**！這等於系統在模擬一開始就「憑空印鈔」送給使用者幾十萬日圓的淨資產。
  - **單邊時間旅行**：其他資產（銀行現金、股票等）都沒補算，只有房貸單獨跳躍到今天，時間線徹底錯亂。
- **132 拍板的原則**：
  **「上傳什麼，就是什麼。」**
  模擬引擎一律直接以「使用者最新一次上傳的快照」為起點，不猜測、不推算、不對帳。使用者如果想要最新時間點的模擬，應該是使用者自己去匯入最新資料，而不是系統用通靈公式去猜。

---

### 二、具體異動範圍（Scope 清單）

132 其實是一個**「主要以刪除廢代碼為主的清理任務」**，範圍非常乾淨且孤立：

#### 1. 後端核心引擎（刪除廢代碼）
- [x] **刪除 [MortgageCalculator.java:133-289](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L133-L289)**：
  - 將整個 `fastForwardMortgagesToPresent(...)` 方法整坨刪除（約 150 行）。
  - 這直接解決了「補算造成憑空印鈔」的邏輯缺陷。
- [x] **刪除 [AssetSimulationEngineImpl.java:112](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java#L112)**：
  - 刪除唯一呼叫該方法的一行程式碼：
    `mortgageSimulationService.fastForwardMortgagesToPresent(context, allMortgages, nowMonth, overrides);`。
- [x] **清理測試 [MortgageCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)**：
  - 刪除專門測試 `fastForwardMortgagesToPresent` 的 2 個測試案例（第 187 行與 239 行）。

#### 2. 其他資產補算（不實作 / 維持現狀）
- [x] **NISA / iDeCo**：原先 Issue 105 提議要為 NISA/iDeCo 加寫的補算邏輯，**完全不開發**（確認維持現狀，無須任何代碼更動）。
- [x] **銀行現金 / 股票 / 年金 / DC企業型**：原本就沒有補算邏輯，維持現狀不做任何改動。

#### 3. 前端 UI 與文案修改（精確定位）
- [x] **更新提示文案 [mortgage-fast-forward-notice.tsx:35-37](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx#L35-L37)**：
  - 已改為：
    > *"The simulation starts directly from this snapshot date without extrapolating or fast-forwarding to today. For maximum accuracy, consider importing your latest mortgage statement."*
  - 移除了「自動補算到當前月份」等不實描述，回歸單純的快照新鮮度提醒。
- [x] **呼叫端與 Hook 確認無副作用**：
  - Hook：[`use-mortgage-fast-forward-notice.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-mortgage-fast-forward-notice.ts)（負責計算快照距離今天過了幾個月，邏輯維持不變）
  - 頁面：[`simulation/page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx#L47)（渲染該 Notice 卡片）

---

### 三、先做 132 對後續「Feature Requirement 124」的巨大好處
1. **避免改到即將被刪除的死碼**：
   124 原文規劃要修改的兩處呼叫點中，第 1 處（`MortgageCalculator.java:214`）正好位在即將被 132 刪除的 `fastForwardMortgagesToPresent` 裡面！如果先做 132 把它砍掉，就不用白費力氣去改它。
2. **讓 124 的呼叫點精簡為 1 處**：
   拔掉補算後，124 在 `MortgageCalculator` 只需要專注處理未來模擬主迴圈（`simulateMortgagesForYear`）的假設還款，程式碼負擔減半。
3. **連帶結案 Issue 104**：
   Issue 104（房貸補算誤用假設利率）隨著補算方法被整段刪除而自然解決，不需額外維護。

---

### 四、實作完成報告（Feature Requirement 132 Execution Report）

#### 1. 核心變更總結
全面廢除房貸在資產模擬啟動時的「補算到當前月份（fast-forward to present）」邏輯。
* **變更前**：若房貸快照是 6 個月前，系統會在進入正式模擬年迴圈前，強行跑 6 個月的分期付款試算，本金因而減少，但這 6 個月扣繳的現金卻完全沒有從銀行帳戶扣除，造成嚴重的「憑空印鈔（資產虛增）」邏輯缺陷，且衍生了 Issue 104（誤用假設利率）等問題。
* **變更後**：資產模擬一律純粹以使用者最後一次匯入/上傳的資產快照時間點為原點，不再進行任何向未來的補算推敲。

#### 2. 改動檔案清單與變更細節
1. **[MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)**
   - 完整刪除 `fastForwardMortgagesToPresent(...)` 方法（約 150 行）。
   - 移除了未使用的 `RepaymentMethod` import。
2. **[AssetSimulationEngineImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)**
   - 刪除呼叫 `fastForwardMortgagesToPresent` 的程式碼行（原第 112 行）。
   - 移除了未使用的 `MortgageSimulationService` 注入欄位與 constructor 參數，精簡引擎依賴。
3. **測試程式碼更新**
   - **[AssetSimulationEngineImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)**：建構子引數從 7 個更新為 6 個。
   - **[MortgageCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)**：移除針對快轉邏輯的 2 個測試（`testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate` 與 `testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides`）。
4. **前端提示元件**
   - **[mortgage-fast-forward-notice.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx)**：
     - 將原先「系統會自動補算到當前月份」文案修改為：「模擬直接從該快照時間點起算，不會向今日推算補算。為求最高精準度，建議匯入最新房貸對帳單。」

#### 3. 伴隨效益與後續連鎖解耦
* **Issue 104 自動結案**：原先 Issue 104 討論房貸補算誤用假設利率的問題，因為該補算方法已全數移除，問題自然不復存在。
* **Issue 105 範圍精簡**：NISA / iDeCo 放棄實作補算，不增添額外邏輯。
* **Feature Requirement 124（GA 支援 REDUCE_PAYMENT）大幅精簡**：
  原 FR-124 規劃要在 `MortgageCalculator` 修改兩處（第 214 行快轉處與第 376 行模擬處），因第 214 行已隨 132 被刪除，FR-124 實作時只需修改 `simulateMortgagesForYear` 一處，複雜度與測試成本減半。

## Diff
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10014  12.62G   4.51    20:27:37  
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx b/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx
index 377d6c2..ddc7f1b 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx
@@ -33,7 +33,7 @@ export function MortgageFastForwardNotice() {
             {outdatedMortgages.map((m) => (
               <p key={m.id} className="leading-relaxed">
                 The latest snapshot for <strong>{m.loanName}</strong> is from <strong>{m.balanceRecordDate}</strong> (approx. {m.monthsElapsed} month{m.monthsElapsed > 1 ? 's' : ''} ago). 
-                The simulation automatically fast-forwards the balance to the current month using the contractual interest rate ({(m.interestRate * 100).toFixed(2)}%), rather than hypothetical scenario overrides. For maximum accuracy, consider importing your latest mortgage statement.
+                The simulation starts directly from this snapshot date without extrapolating or fast-forwarding to today. For maximum accuracy, consider importing your latest mortgage statement.
               </p>
             ))}
           </div>
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10015  12.59G   4.49    20:27:41  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10016  12.54G   4.60    20:27:49  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
index 7b238d09..db699c7b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
@@ -35,7 +35,6 @@ import ch.lin.finance.hub.backend.api.app.repository.user.CareerEventRepository;
 import ch.lin.finance.hub.backend.api.app.repository.user.LifeEventRepository;
 import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.FundOverrideApplier;
-import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SimulationContextFactory;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
@@ -60,7 +59,6 @@ public class AssetSimulationEngineImpl implements AssetSimulationEngine {
 
     private final FundOverrideApplier fundOverrideApplier;
     private final YearlySimulationPipeline yearlySimulationPipeline;
-    private final MortgageCalculator mortgageSimulationService;
 
     @Override
     @Transactional(readOnly = true)
@@ -109,8 +107,6 @@ public class AssetSimulationEngineImpl implements AssetSimulationEngine {
             List<MortgageRecord> allMortgages,
             YearMonth nowMonth) {
 
-        mortgageSimulationService.fastForwardMortgagesToPresent(context, allMortgages, nowMonth, overrides);
-
         int dcUnlockAge = SimulationParameterResolver.resolveDcUnlockAge(context.getConfig());
         int idecoUnlockAge = SimulationParameterResolver.resolveIdecoUnlockAge(context.getConfig());
         Integer idecoStartOverride = overrides != null ? overrides.idecoStartAgeOverride() : null;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
index bf599566..38bc02a7 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java
@@ -40,7 +40,6 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.Mortgag
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
-import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
 
 @Component
 public class MortgageCalculator {
@@ -130,164 +129,6 @@ public class MortgageCalculator {
         return 0L;
     }
 
-    public void fastForwardMortgagesToPresent(SimulationContext context, List<MortgageRecord> allMortgages,
-            YearMonth nowMonth, SimulationOverrides overrides) {
-        long[] mortgageBalances = context.getMortgageBalances();
-        long[] mortgageMonthlyPayments = context.getMortgageMonthlyPayments();
-        double[] currentRates = context.getMortgageCurrentRates();
-        long[] expectedPayments = context.getMortgageCurrentExpectedPayments();
-        int[] monthsSinceStart = context.getMortgageMonthsSinceStart();
-
-        for (int i = 0; i < allMortgages.size(); i++) {
-            MortgageRecord m = allMortgages.get(i);
-            LocalDate recordDate = m.getBalanceRecordDate();
-            if (recordDate == null) {
-                recordDate = m.getStartDate();
-            }
-            if (recordDate != null) {
-                YearMonth recYm = YearMonth.from(recordDate);
-                int monthsElapsed = (nowMonth.getYear() - recYm.getYear()) * 12
-                        + (nowMonth.getMonthValue() - recYm.getMonthValue());
-                long balance = mortgageBalances[i];
-                if (monthsElapsed > 0 && balance > 0) {
-                    for (int mth = 0; mth < monthsElapsed; mth++) {
-                        YearMonth currentYm = recYm.plusMonths(mth);
-
-                        if (currentYm.isBefore(YearMonth.from(m.getStartDate()))) {
-                            continue;
-                        }
-
-                        if (currentYm.equals(YearMonth.from(m.getStartDate())) && balance == 0) {
-                            balance = m.getCurrentBalance();
-                        }
-
-                        monthsSinceStart[i]++;
-
-                        // Fast-forward represents the past (between balanceRecordDate and nowMonth).
-                        // Strictly use the real contractual rate from MortgageRecord; never use Sandbox scenario overrides.
-                        double newRate = m.getInterestRate();
-                        boolean rateChangedThisMonth = false;
-                        if (newRate != currentRates[i]) {
-                            currentRates[i] = newRate;
-                            rateChangedThisMonth = true;
-                        }
-
-                        if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
-                            boolean hasFiveYearRule = Boolean.TRUE.equals(m.getHasFiveYearRule());
-                            if (hasFiveYearRule) {
-                                if (monthsSinceStart[i] % 60 == 0) {
-                                    int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
-                                            + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
-                                    int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                            - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
-                                    remainingMonths = Math.max(1, remainingMonths);
-                                    if (remainingMonths > 0) {
-                                        long theoreticalNewPayment = MortgageCalculatorUtil
-                                                .calculateFixedPaymentOrPrincipal(
-                                                        balance, currentRates[i], remainingMonths,
-                                                        m.getRepaymentMethod(),
-                                                        m.getBonusPayments(),
-                                                        currentYm.getMonthValue());
-                                        long maxAllowedPayment = (long) (expectedPayments[i] * 1.25);
-                                        long newPayment = Math.min(theoreticalNewPayment, maxAllowedPayment);
-                                        expectedPayments[i] = newPayment;
-                                        mortgageMonthlyPayments[i] = newPayment;
-                                    }
-                                }
-                            } else {
-                                if (rateChangedThisMonth) {
-                                    int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
-                                            + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
-                                    int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                            - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
-                                    remainingMonths = Math.max(1, remainingMonths);
-                                    if (remainingMonths > 0) {
-                                        long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
-                                                balance, currentRates[i], remainingMonths, m.getRepaymentMethod(),
-                                                m.getBonusPayments(),
-                                                currentYm.getMonthValue());
-                                        expectedPayments[i] = newPayment;
-                                        mortgageMonthlyPayments[i] = newPayment;
-                                    }
-                                }
-                            }
-                        }
-
-                        // Fast-forward represents the past: only real historical early repayments apply.
-                        // Never apply Sandbox hypothetical early repayments to past months.
-                        long earlyPrincipal = 0L;
-                        if (m.getEarlyRepayments() != null) {
-                            for (var er : m.getEarlyRepayments()) {
-                                if (YearMonth.from(er.getRepaymentDate()).equals(currentYm)) {
-                                    earlyPrincipal += er.getAmount();
-                                    if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_PAYMENT) {
-                                        int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
-                                                + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
-                                        int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
-                                                - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m,
-                                                        currentYm);
-                                        remainingMonths = Math.max(1, remainingMonths);
-                                        if (remainingMonths > 0) {
-                                            mortgageMonthlyPayments[i] = MortgageCalculatorUtil
-                                                    .calculateFixedPaymentOrPrincipal(
-                                                            balance - earlyPrincipal, currentRates[i], remainingMonths,
-                                                            m.getRepaymentMethod(),
-                                                            m.getBonusPayments(),
-                                                            currentYm.getMonthValue());
-                                            if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
-                                                expectedPayments[i] = mortgageMonthlyPayments[i];
-                                            }
-                                        }
-                                    }
-                                }
-                            }
-                        }
-                        balance -= earlyPrincipal;
-                        if (balance <= 0) {
-                            balance = 0;
-                            break;
-                        }
-
-                        double monthlyRate = currentRates[i] / 12.0;
-                        int currentMthValue = currentYm.getMonthValue();
-                        long payment = mortgageMonthlyPayments[i];
-
-                        long currentMonthBonus = 0;
-                        if (m.getBonusPayments() != null) {
-                            for (var bp : m.getBonusPayments()) {
-                                if (bp.getMonth() != null && bp.getMonth() == currentMthValue) {
-                                    Long amount = bp.getAmount();
-                                    currentMonthBonus += amount != null ? amount : 0L;
-                                }
-                            }
-                        }
-                        payment += currentMonthBonus;
-
-                        long interest = (long) (balance * monthlyRate);
-                        long principal;
-
-                        if (m.getRepaymentMethod() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod.EQUAL_PRINCIPAL) {
-                            principal = mortgageMonthlyPayments[i];
-                            principal += currentMonthBonus;
-                        } else {
-                            principal = payment - interest;
-                        }
-
-                        if (principal > balance) {
-                            principal = balance;
-                        }
-                        balance -= principal;
-                        if (balance <= 0) {
-                            balance = 0;
-                            break;
-                        }
-                    }
-                    mortgageBalances[i] = balance;
-                }
-            }
-        }
-    }
-
     public MortgageYearlyResult simulateMortgagesForYear(SimulationContext context, List<MortgageRecord> allMortgages,
             YearMonth nowMonth, SimulationOverrides overrides) {
         long[] mortgageBalances = context.getMortgageBalances();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index 66aeaeda..196b2658 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -240,7 +240,7 @@ class AssetSimulationEngineImplTest {
 
         engine = new AssetSimulationEngineImpl(
                 factory, careerEventRepository, lifeEventRepository, mortgageRecordRepository,
-                fundOverrideApplier, pipeline, mortgageSimulationService);
+                fundOverrideApplier, pipeline);
 
         AllConfigsData configs = new AllConfigsData();
         configs.setEnabledConfigName("default");
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
index af069bac..9e1a5fb1 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java
@@ -139,107 +139,4 @@ class MortgageCalculatorTest {
         // In the second year (2027 > 2026), the deduction for THIS year should be > 0
         assertEquals(true, result.mortgageTaxDeductionThisYear() > 0);
     }
-
-    @Test
-    void testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate() {
-        // Arrange
-        SimulationContext context = new SimulationContext();
-        MortgageRecord m = new MortgageRecord(
-                "Variable Loan",
-                10000000L,
-                10000000L,
-                LocalDate.of(2025, 1, 1),
-                LocalDate.of(2055, 1, 1),
-                LocalDate.of(2026, 1, 1), // balanceRecordDate: Jan 2026
-                360,
-                0.005, // Contractual rate 0.5%
-                InterestRateType.VARIABLE,
-                RepaymentMethod.EQUAL_PAYMENT,
-                30000L,
-                Collections.emptyList(),
-                LocalDate.of(2025, 2, 1),
-                null,
-                null,
-                false, // hasFiveYearRule = false (would trigger recalculation if rate changed)
-                2039,
-                0.007,
-                0L,
-                true);
-        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
-
-        context.setMortgageBalances(new long[]{10000000L});
-        context.setMortgageMonthlyPayments(new long[]{30000L});
-        context.setMortgageCurrentRates(new double[]{0.005});
-        context.setMortgageCurrentExpectedPayments(new long[]{30000L});
-        context.setMortgageMonthsSinceStart(new int[]{12});
-
-        // Overrides setting an aggressive 5.0% rate for 2026
-        SimulationOverrides.MortgageRateScenarioOverride rateOverride
-                = new SimulationOverrides.MortgageRateScenarioOverride(2026, 0.05);
-        SimulationOverrides overrides = new SimulationOverrides(
-                null, null, null, null, null, null, null,
-                java.util.Map.of(1L, List.of(rateOverride)),
-                null);
-
-        YearMonth nowMonth = YearMonth.of(2026, 6); // Fast forward 5 months: Jan, Feb, Mar, Apr, May
-
-        // Act
-        calculator.fastForwardMortgagesToPresent(context, List.of(m), nowMonth, overrides);
-
-        // Assert: rate must strictly remain 0.005 (contractual rate), NOT overridden to 0.05
-        assertEquals(0.005, context.getMortgageCurrentRates()[0], 1e-6);
-        // Monthly payment should remain 30,000 (not recalculated to a higher amount because rate didn't change)
-        assertEquals(30000L, context.getMortgageMonthlyPayments()[0]);
-    }
-
-    @Test
-    void testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides() {
-        // Arrange
-        SimulationContext context = new SimulationContext();
-        MortgageRecord m = new MortgageRecord(
-                "Variable Loan",
-                10000000L,
-                10000000L,
-                LocalDate.of(2025, 1, 1),
-                LocalDate.of(2055, 1, 1),
-                LocalDate.of(2026, 1, 1), // balanceRecordDate: Jan 2026
-                360,
-                0.005,
-                InterestRateType.VARIABLE,
-                RepaymentMethod.EQUAL_PAYMENT,
-                30000L,
-                Collections.emptyList(),
-                LocalDate.of(2025, 2, 1),
-                null,
-                null,
-                true,
-                2039,
-                0.007,
-                0L,
-                true);
-        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
-
-        context.setMortgageBalances(new long[]{10000000L});
-        context.setMortgageMonthlyPayments(new long[]{30000L});
-        context.setMortgageCurrentRates(new double[]{0.005});
-        context.setMortgageCurrentExpectedPayments(new long[]{30000L});
-        context.setMortgageMonthsSinceStart(new int[]{12});
-
-        // Overrides with a hypothetical early repayment in 2026 of 2,000,000
-        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride
-                = new SimulationOverrides.MortgageEarlyRepaymentOverride(2026, 2000000L);
-        SimulationOverrides overrides = new SimulationOverrides(
-                null, null, null, null, null,
-                java.util.Map.of(1L, List.of(erOverride)),
-                null, null, null);
-
-        YearMonth nowMonth = YearMonth.of(2026, 6);
-
-        // Act
-        calculator.fastForwardMortgagesToPresent(context, List.of(m), nowMonth, overrides);
-
-        // Assert: balance should amortize normally (~9.87m). If hypothetical early repayment had leaked in, balance < 8m
-        long balance = context.getMortgageBalances()[0];
-        assertEquals(true, balance > 9500000L, "Balance should not be deducted by hypothetical override");
-    }
 }
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-132.md b/finance-hub-backend/todo/pending fix/feature-requirement-132.md
index 495dda00..e3ee01bb 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-132.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-132.md
@@ -44,3 +44,87 @@
 ## 跟issue-104、issue-105的關係（這兩份文件需要同步處理，不是獨立存在）
 - **issue-104**（房貸補算誤用Sandbox假設利率）：Gemini已經依照issue-104原本的方向修正了`fastForwardMortgagesToPresent`內部的利率來源判斷（改用`m.getInterestRate()`而不是`overrides`），這個修正本身的邏輯是對的，但因為這次決定要把整個補算機制移除，issue-104描述的那個子問題會隨著整個方法被刪除而自然消失，不需要單獨保留這個修正。issue-104應該標記為「因為feature-requirement-132，整個問題已經不存在，不需要單獨驗證」。
 - **issue-105**（除房貸外的資產補算）：原本的NISA/iDeCo補算提案已被這次決定推翻（見上方「判斷依據」），issue-105應該改寫成只保留「資產資料新鮮度提示」這一個方向，移除所有跟NISA/iDeCo補算相關的技術細節跟修正方向段落。
+
+---
+
+## 白話總結與具體實作範圍（TL;DR Scope）
+
+### 一、用白話說：132 到底在做什麼？
+**核心一句話：把系統自作聰明「幫使用者補算過去幾個月」的機制徹底砍掉，使用者上傳什麼就是什麼。**
+
+- **過去的荒謬做法（補算機制）**：
+  如果使用者上次上傳房貸是 3 個月前，系統在跑模擬時，會自作聰明執行 `fastForwardMortgagesToPresent`，擅自幫使用者扣掉這 3 個月的房貸本金。
+  - **嚴重的 Bug**：房貸本金被扣掉了，但銀行帳戶的現金**完全沒有扣**！這等於系統在模擬一開始就「憑空印鈔」送給使用者幾十萬日圓的淨資產。
+  - **單邊時間旅行**：其他資產（銀行現金、股票等）都沒補算，只有房貸單獨跳躍到今天，時間線徹底錯亂。
+- **132 拍板的原則**：
+  **「上傳什麼，就是什麼。」**
+  模擬引擎一律直接以「使用者最新一次上傳的快照」為起點，不猜測、不推算、不對帳。使用者如果想要最新時間點的模擬，應該是使用者自己去匯入最新資料，而不是系統用通靈公式去猜。
+
+---
+
+### 二、具體異動範圍（Scope 清單）
+
+132 其實是一個**「主要以刪除廢代碼為主的清理任務」**，範圍非常乾淨且孤立：
+
+#### 1. 後端核心引擎（刪除廢代碼）
+- [x] **刪除 [MortgageCalculator.java:133-289](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L133-L289)**：
+  - 將整個 `fastForwardMortgagesToPresent(...)` 方法整坨刪除（約 150 行）。
+  - 這直接解決了「補算造成憑空印鈔」的邏輯缺陷。
+- [x] **刪除 [AssetSimulationEngineImpl.java:112](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java#L112)**：
+  - 刪除唯一呼叫該方法的一行程式碼：
+    `mortgageSimulationService.fastForwardMortgagesToPresent(context, allMortgages, nowMonth, overrides);`。
+- [x] **清理測試 [MortgageCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)**：
+  - 刪除專門測試 `fastForwardMortgagesToPresent` 的 2 個測試案例（第 187 行與 239 行）。
+
+#### 2. 其他資產補算（不實作 / 維持現狀）
+- [x] **NISA / iDeCo**：原先 Issue 105 提議要為 NISA/iDeCo 加寫的補算邏輯，**完全不開發**（確認維持現狀，無須任何代碼更動）。
+- [x] **銀行現金 / 股票 / 年金 / DC企業型**：原本就沒有補算邏輯，維持現狀不做任何改動。
+
+#### 3. 前端 UI 與文案修改（精確定位）
+- [x] **更新提示文案 [mortgage-fast-forward-notice.tsx:35-37](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx#L35-L37)**：
+  - 已改為：
+    > *"The simulation starts directly from this snapshot date without extrapolating or fast-forwarding to today. For maximum accuracy, consider importing your latest mortgage statement."*
+  - 移除了「自動補算到當前月份」等不實描述，回歸單純的快照新鮮度提醒。
+- [x] **呼叫端與 Hook 確認無副作用**：
+  - Hook：[`use-mortgage-fast-forward-notice.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-mortgage-fast-forward-notice.ts)（負責計算快照距離今天過了幾個月，邏輯維持不變）
+  - 頁面：[`simulation/page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx#L47)（渲染該 Notice 卡片）
+
+---
+
+### 三、先做 132 對後續「Feature Requirement 124」的巨大好處
+1. **避免改到即將被刪除的死碼**：
+   124 原文規劃要修改的兩處呼叫點中，第 1 處（`MortgageCalculator.java:214`）正好位在即將被 132 刪除的 `fastForwardMortgagesToPresent` 裡面！如果先做 132 把它砍掉，就不用白費力氣去改它。
+2. **讓 124 的呼叫點精簡為 1 處**：
+   拔掉補算後，124 在 `MortgageCalculator` 只需要專注處理未來模擬主迴圈（`simulateMortgagesForYear`）的假設還款，程式碼負擔減半。
+3. **連帶結案 Issue 104**：
+   Issue 104（房貸補算誤用假設利率）隨著補算方法被整段刪除而自然解決，不需額外維護。
+
+---
+
+### 四、實作完成報告（Feature Requirement 132 Execution Report）
+
+#### 1. 核心變更總結
+全面廢除房貸在資產模擬啟動時的「補算到當前月份（fast-forward to present）」邏輯。
+* **變更前**：若房貸快照是 6 個月前，系統會在進入正式模擬年迴圈前，強行跑 6 個月的分期付款試算，本金因而減少，但這 6 個月扣繳的現金卻完全沒有從銀行帳戶扣除，造成嚴重的「憑空印鈔（資產虛增）」邏輯缺陷，且衍生了 Issue 104（誤用假設利率）等問題。
+* **變更後**：資產模擬一律純粹以使用者最後一次匯入/上傳的資產快照時間點為原點，不再進行任何向未來的補算推敲。
+
+#### 2. 改動檔案清單與變更細節
+1. **[MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)**
+   - 完整刪除 `fastForwardMortgagesToPresent(...)` 方法（約 150 行）。
+   - 移除了未使用的 `RepaymentMethod` import。
+2. **[AssetSimulationEngineImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)**
+   - 刪除呼叫 `fastForwardMortgagesToPresent` 的程式碼行（原第 112 行）。
+   - 移除了未使用的 `MortgageSimulationService` 注入欄位與 constructor 參數，精簡引擎依賴。
+3. **測試程式碼更新**
+   - **[AssetSimulationEngineImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)**：建構子引數從 7 個更新為 6 個。
+   - **[MortgageCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculatorTest.java)**：移除針對快轉邏輯的 2 個測試（`testFastForwardMortgagesToPresent_IgnoresRateScenarioOverrides_UsesContractualRate` 與 `testFastForwardMortgagesToPresent_IgnoresEarlyRepaymentOverrides`）。
+4. **前端提示元件**
+   - **[mortgage-fast-forward-notice.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/mortgage-fast-forward-notice.tsx)**：
+     - 將原先「系統會自動補算到當前月份」文案修改為：「模擬直接從該快照時間點起算，不會向今日推算補算。為求最高精準度，建議匯入最新房貸對帳單。」
+
+#### 3. 伴隨效益與後續連鎖解耦
+* **Issue 104 自動結案**：原先 Issue 104 討論房貸補算誤用假設利率的問題，因為該補算方法已全數移除，問題自然不復存在。
+* **Issue 105 範圍精簡**：NISA / iDeCo 放棄實作補算，不增添額外邏輯。
+* **Feature Requirement 124（GA 支援 REDUCE_PAYMENT）大幅精簡**：
+  原 FR-124 規劃要在 `MortgageCalculator` 修改兩處（第 214 行快轉處與第 376 行模擬處），因第 214 行已隨 132 被刪除，FR-124 實作時只需修改 `simulateMortgagesForYear` 一處，複雜度與測試成本減半。
+
diff --git a/finance-hub-backend/todo/pending fix/issue-105.md b/finance-hub-backend/todo/pending fix/issue-105.md
index 675b0934..30ebc26f 100644
--- a/finance-hub-backend/todo/pending fix/issue-105.md
+++ b/finance-hub-backend/todo/pending fix/issue-105.md
@@ -1,4 +1,4 @@
-# Issue 105：除了房貸以外，銀行現金、NISA、DC/iDeCo、公司股票、年金的模擬起點，一律直接採信最新一次匯入的數字，完全沒有考慮這筆資料實際上是多久以前的
+# [Fixed] Issue 105：除了房貸以外，銀行現金、NISA、DC/iDeCo、公司股票、年金的模擬起點，一律直接採信最新一次匯入的數字，完全沒有考慮這筆資料實際上是多久以前的
 
 ## 這份issue的方向已經被更根本的決定取代
 使用者原本在這份issue裡決定「NISA、iDeCo可以補算到今天，其他資產維持現狀不補算、只顯示新鮮度提示」，後來經過與Gemini的深入討論（討論過程記錄在下方「歷史討論記錄」區塊，保留供回溯參考），這個「補算」方向被使用者自己徹底推翻，改成一個更根本的原則：**系統不做任何時間推算，一律把使用者最後一次上傳的那筆快照，當作模擬要開始的那個時間點本身**。這個原則不只適用於這份issue原本討論的六種資產，連房貸現有運作中的補算機制也要一併移除。最終定案方向與完整理由，記錄在[feature-requirement-132](feature-requirement-132.md)，請以該文件為準。
@@ -39,4 +39,4 @@ Gemini查證後確認，現有房貸補算機制`fastForwardMortgagesToPresent`
 2. **連補算起點本身都無法可靠判斷**：檔案內文沒有時間欄位的資料，沒辦法只靠檔案時間戳記判斷真實時間點；使用者可能分好幾天下載不同資料，難以確定該用哪一個當基準。
 3. **這個工具不是對帳軟體**：不需要讓銀行跟NISA/iDeCo的時間點互相校準，只需要在使用者按下模擬那一刻，誠實地用他當下提供的資料去推算未來。
 
-這三點理由，不只推翻了NISA/iDeCo補算，也連帶推翻了現有房貸補算機制本身的正當性，才有了feature-requirement-132「全面廢除補算機制」這個最終範圍更大的決定。
+這三點理由，不只推翻了NISA/iDeCo補算，也連帶推翻了現有房貸補算機制本身的正當性，才有了「Feature Requirement 132：全面廢除「補算到現在」機制，模擬起點改成「使用者最後一次上傳快照的那個時間點」本身，不再嘗試推算到今天」「Fixed-132」這個最終範圍更大的決定。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10017  12.55G   4.60    20:27:52  