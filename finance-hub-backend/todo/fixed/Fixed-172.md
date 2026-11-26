# [FIXED] 172：使用者在模擬起算當年就退休時，當年已經實際入帳的薪資淨額會被從退休後的年金淨收入裡再扣一次，導致退休當年現金憑空蒸發一大筆，可能觸發不必要的資產賤賣

## 這份issue怎麼被發現的
這是這次Source Code Review審查模擬引擎主迴圈時發現的問題。

## 背景：這段邏輯原本是為了解決什麼問題
[Fixed-65（第一年現金流雙重計算修復計畫）](file://finance-hub/finance-hub-backend/todo/fixed/Fixed-65%20第一年現金流雙重計算修復計畫.md)記錄了這段邏輯的由來：模擬起算當年（過渡年），系統會先算出「今年全年預估稅後淨利」（`fullYearNetIncome`），但銀行餘額（模擬起點的流動現金）本身已經包含「今年至今已經存下來的錢」，如果把全年淨利整筆加進銀行餘額，等於同一筆錢被算了兩次。解法是用「今年全年預估淨利」減去「今年至今已經真實入帳的淨利」，只把「今年剩下月份」這一段的淨現金流加進銀行餘額——這個設計本身是對的，而且前提成立的條件是：全年預估淨利跟今年至今的真實入帳淨利，兩者算的是「同一條收入來源」（都是薪水）。

## 相關檔案
- [YearlySimulationPipeline.java:73](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`context.setRetired(age >= targetRetirementAge)`，退休旗標在每一年、包含模擬第一年，都是用「這一整年算不算退休」這個二元判斷去設定，沒有「這一年裡有一段時間是在職、有一段時間已退休」的概念）
- 同檔案`:156`（`applyWorkingTaxes`方法的進入條件`if (!context.isRetired() && effectiveAnnualSalary > 0)`——只要這一整年被標記成「已退休」，這個方法整個被跳過，包括裡面唯一會呼叫`taxEngine.calculate(...)`、把房貸抵稅額`mortgageResult`一併算進去的那一段）
- 同檔案`:209`（`applyRetireeTaxes`方法的進入條件`if (context.isRetired())`——退休當年只會算年金稅，用的計算方式完全不知道「今年還有一段時間是在職領薪水」這件事）
- 同檔案`:313-321`（`applyCashflowAndSettlement`，`fullYearNetIncome = state.getNetIncome()`、`ytdNetIncome = computeYtdNetIncomeBaseline(context)`、`remainingNetIncome = fullYearNetIncome - ytdNetIncome`）
- 同檔案`:370-377`（`computeYtdNetIncomeBaseline`，用`context.getYtdGrossIncome()`扣掉今年至今已經繳的所得稅/住民稅/健保/年金/雇用保險，算出「今年至今已經實際拿到手的淨薪水」）
- [IncomeExpenseCalculator.java:66-68](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)（`calculateSalaryForYear`，只要`context.isRetired()`為true，不管傳進來的`ytdSalary`是多少，直接回傳0——代表退休當年，系統完全不會把「今年已經領到的薪水」算進今年的全年所得裡）
- [SalaryContextBuilder.java:110-136,198](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)（`ytdGrossIncome`等欄位的來源：逐筆讀取今年已經入帳的薪資單，加總成「今年至今的真實已領薪資」，這些錢在現實中已經真的轉進使用者的銀行帳戶）
- [SimulationContextFactory.java:153-162](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)（模擬起始的流動現金`initialLiquidCash`，是直接讀取各銀行帳戶「最新一筆交易紀錄的餘額欄位」加總而來——這個餘額本身，就是把今年至今所有薪水入帳、花費支出都反映在內之後的真實結果）

## 現狀
`YearlySimulationPipeline.processSimulationYear`在每一年開始時，不分這一年是模擬第一年還是後面任何一年，都用同一行判斷`context.setRetired(age >= targetRetirementAge)`去決定這一整年算「在職」還是「退休」。如果使用者設定的退休年齡剛好等於他現在的實際年齡（這是這個工具最常見的使用情境之一——使用者今年就要退休，想模擬退休後的現金流），模擬第一年整年就會被標記成「已退休」。

一旦整年被標記成退休：
1. `IncomeExpenseCalculator.calculateSalaryForYear`在`isRetired()`為true時直接回傳0，完全不會把「今年至今已經實際領到的薪水」（`ytdGrossIncome`）納入今年的全年所得計算。
2. `applyWorkingTaxes`（唯一會把房貸抵稅額、所得稅、住民稅算進今年所得稅負的地方）整段被跳過。
3. `applyRetireeTaxes`改用年金稅去算這一年的`netIncome`——如果使用者還沒到可以請領公的年金的年齡（這是常見設定，退休跟開始領年金往往差好幾年），年金所得是0，這一年的`state.getNetIncome()`結果就是0。

但同一時間，`computeYtdNetIncomeBaseline`依然會把「今年至今已經真實入帳的薪資淨額」（例如今年1月到6月已經實際領到、也已經實際存進銀行帳戶的薪水，扣掉已經繳的稅跟社保後的淨額）算出來，拿去跟上面那個已經變成0的`fullYearNetIncome`相減。

## 為何是問題
用一個具體數字說明：使用者今年65歲，設定65歲退休，現在是7月跑模擬（1-6月薪水已經實際入帳）。假設今年1-6月薪水稅後淨額合計231萬2千円，這筆錢現在已經實際躺在他的銀行帳戶裡，模擬起算時這個銀行餘額已經把這筆錢算進去了。使用者還沒打算馬上請領公的年金（設定70歲才開始領），所以模擬第一年（退休當年）的年金所得是0，`fullYearNetIncome`等於0。

模擬計算的`remainingNetIncome = 0 - 2,312,000 = -2,312,000`。這筆負值接著扣掉剩下半年的生活費（假設每月30萬，六個月180萬），得出`netSavings`約負411萬2千円，整筆加進銀行餘額。這代表：使用者1-6月已經真的領到手、已經安穩存在銀行裡的231萬2千円，在模擬裡被當成「這筆錢現在要被花掉／扣掉」又減了一次——這筆錢沒有真的消失，但模擬卻讓它憑空消失了。如果這個負值大到讓模擬判定現金不足，`CashflowLiquidationCalculator`會接著觸發賣NISA、賣公司股票去填補這個完全虛構出來的現金缺口，讓退休當年、甚至後續幾年的淨資產投影被嚴重低估。

連帶的另一個影響：因為`applyWorkingTaxes`整段被跳過，使用者今年1-6月在職期間若有符合資格的住宅ローン控除（房貸抵稅），這筆抵稅額在退休當年也完全不會被計入——不是因為退休後不該享有這筆抵稅（現實中退休當年透過確定申告，在職期間的所得依然可以申報房貸抵稅），而是因為系統用來承接這筆抵稅額的唯一入口，剛好被同一個「整年視為退休」的旗標關掉了。

這個bug只會在「模擬起算當年就是退休那一年」（`targetRetirementAge <= 使用者現在實際年齡`）這個情境下出現；如果退休發生在模擬未來某一年（不是起算當年），因為那一年的`context.getYear() != context.getCurrentYear()`，`ytdNetIncome`不會被扣減，不會有這個問題。

## 建議修正方向
這裡列出兩個可行方向，不代為決定：

**方向A：模擬起算當年若已經退休，直接跳過`ytdNetIncome`這筆扣減**
- 做法：在`applyCashflowAndSettlement`裡，判斷「這是過渡年」且「這一年整年都被標記退休」時，不執行`ytdNetIncome = computeYtdNetIncomeBaseline(context)`這一步，讓`remainingNetIncome`直接等於`fullYearNetIncome`（也就是`applyRetireeTaxes`已經依剩餘月份比例算好的年金淨所得）。
- 優點：改動範圍小，只在`applyCashflowAndSettlement`裡加一個條件判斷。
- 缺點：這個修法仍然沒有把「今年1-6月在職期間可能符合資格的房貸抵稅額」找回來——退休當年的房貸抵稅額依然會是0，這部分如果需要修正，需要另外處理`applyWorkingTaxes`完全被跳過的問題。

**方向B：把「今年已經入帳的薪資淨額」跟「今年開始的年金淨所得」視為兩條不該互相扣抵的獨立收入來源**
- 做法：`computeYtdNetIncomeBaseline`算出來的這筆錢，只應該拿去跟「今年全年預估的薪資淨所得」相減（兩者都是同一條收入來源，才有「同一筆錢算兩次」的風險）；一旦退休當年的`fullYearNetIncome`變成年金所得而非薪資所得，就不應該再被這筆薪資YTD基準去扣減，應該讓兩者分開呈現（例如今年的`netIncome`改成「今年已入帳的薪資淨額」+「退休後剩餘月份的年金淨所得」相加，而不是用薪資YTD去扣年金的全年值）。
- 優點：從根本上修正「用錯收入來源互相扣抵」這個問題，也連帶讓退休當年在職期間的房貸抵稅額有機會被正確找回來（因為這個方向需要同時檢視`applyWorkingTaxes`要不要針對「退休當年、但今年還有在職月份」這個情境另外處理）。
- 缺點：改動範圍比方向A大，需要同時調整`applyWorkingTaxes`/`applyRetireeTaxes`兩個方法在「過渡年剛好是退休年」這個特定情境下的分工方式。

無論選哪個方向，都不需要、也不應該讓系統去「回推」或「補算」使用者過去的真實資料——這個bug的根因是模擬邏輯用錯了收入來源互相扣抵，不是資料本身不完整，修正時只需要調整「這一年的淨收入該怎麼組裝」這段計算邏輯本身。

## 修正報告（採用方向 A：退休當年跳過 `ytdNetIncome` 扣減）

1. **修正邏輯**：
   在 [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L316) 的 `applyCashflowAndSettlement` 方法中，將計算 `ytdNetIncome` 的條件由：
   ```java
   if (context.getYear() == context.getCurrentYear()) {
       ytdNetIncome = computeYtdNetIncomeBaseline(context);
   }
   ```
   調整為防禦性檢查：
   ```java
   if (context.getYear() == context.getCurrentYear() && !context.isRetired()) {
       ytdNetIncome = computeYtdNetIncomeBaseline(context);
   }
   ```
   當模擬起算當年即為退休年度時，由於 `state.getNetIncome()` 已轉為年金淨所得（或未達年金領取年齡時為 0），不再扣除先前在職期間已領取並已計入銀行現金餘額的 `ytdNetIncome`。
   如此一來，`remainingNetIncome` 不會再變成負數（如 `0 - 2,312,000`），避免了流動現金遭重複扣除與誤觸發資產賤賣（如自動拋售 NISA 或公司股票）的問題。

2. **單元測試驗證**：
   在 [`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java#L345-L388) 新增單元測試 `testInitialYearRetirement_doesNotSubtractYtdNetIncomeFromLiquidCash()`：
   - 設定初始年齡 65 歲、退休目標年齡 65 歲（起算當年即退休）、年金起領年齡 70 歲。
   - 注入過去月份真實入帳的薪資及各項稅金與社保（YTD 淨額 2,312,000 円）。
   - 驗證管線執行後 `result.isRetired()` 為 true，`result.getNetIncome()` 為 0L（未扣除 2,312,000 円），且期末流動現金僅扣除退休健保費自負額，未蒸發 YTD 淨額。

## 二次review發現的附帶新bug：`applyYearEndAdjustment`少了同一個`!context.isRetired()`防衛條件，退休當年三個預估稅額顯示欄位會變成負數
這是二次review查證這份修正時，順手挖到的一個**同一種root cause、但方向A沒有觸及**的新問題，不是方向A修正本身造成的副作用。

查證確認[YearlySimulationPipeline.java:275-294](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)的`applyYearEndAdjustment`方法，判斷「這是不是起算年」時只檢查`if (age == initialAge)`，沒有像已經修好的`applyCashflowAndSettlement`那樣加上`&& !context.isRetired()`。這個方法會拿`state.getEstimatedIncomeTax()`等三個欄位（退休當年時，這些欄位已經被`applyRetireeTaxes`正確設定成依剩餘月份比例縮小過的小額年金稅）去減掉`context.getYtdIncomeTax()`等YTD欄位（退休前在職期間真實繳納的、金額大得多的薪資稅），算出來的`remainingIncomeTax`/`remainingInhabitantTax`/`remainingSocialInsurance`會是負數，而且這三個負數會直接寫回`state`、流進最終回傳的模擬結果DTO。

**跟方向A修正的`netIncome`不是同一個欄位，不影響現金流/淨資產正確性**：查證確認`applyYearEndAdjustment`完全沒有碰`state.setNetIncome(...)`，只碰`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`這三個欄位；這三個欄位查證確認只在這個pipeline檔案內部被讀取用來組裝結果DTO，沒有被任何其他計算邏輯讀回去當輸入——這代表這個bug不會導致資產賤賣或現金流誤判，純粹是「畫面上/API回應裡這三個稅額欄位的數字不對」的顯示層資料正確性問題，但這是現在就會發生的真實問題，不是理論風險，而且觸發條件跟原始bug完全相同（起算年同時是退休年）。

修法方向跟方向A完全一樣：把第276行的`if (age == initialAge)`改成`if (age == initialAge && !context.isRetired())`。這份issue記錄這個發現，但不代為決定要不要現在就一起修，還是留給下一輪處理。

## 延伸探討：年中退休與退休當年的稅金計算失真問題（供後續 Reviewer 討論）

在修復 Issue 172 過程中，進一步梳理出模擬管線在「退休當年」（無論是過渡年退休，或是未來某一年的年中退休）所存在的結構性稅金計算失真現象，整理如下供架構與產品方向評估。**二次review查證重新評估過這部分的實際發生範圍跟金額規模，結論比下方原始文字描述的更嚴重，見本節最後一段的更新。**

### 1. 現行引擎對「退休」的二元化抽象假定
- 目前整個模擬迴圈以「年」為粒度推進，退休狀態由 [`context.setRetired(age >= targetRetirementAge)`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L73) 決定。
- 這種二元化設計（整年皆在職 vs. 整年皆退休）意味著系統缺乏「年中某個月份退休」的混合年度概念：
  - 一旦標記為退休，`applyWorkingTaxes` 完全跳過，全年度有效薪資所得 `effectiveAnnualSalary` 視為 0。
  - 當年稅務完全由 `applyRetireeTaxes` 接管，僅計算公的年金雜所得稅。

### 2. 稅金與年末調整在不同情境下的失真細節
#### 情境一：過渡年（起算年）即為退休年
- **已繳稅款與全年稅負的不對稱**：
  若使用者於年中 7 月跑模擬，設定今年 65 歲退休，系統在 1~6 月已產生實際薪資（例如 YTD 毛薪 300 萬，並已源泉徵收所得稅、住民稅與社保）。
  然而因起算年整年被判定為退休，在 [`YearlySimulationPipeline.java:271`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L271) 的 `applyYearEndAdjustment` 中：
  `annualGross = effectiveAnnualSalary (0) + bonus (0) = 0`。
  當 `annualGross = 0`，年末調整引擎計算得出的全年應繳所得稅與社保為 0。
- **房貸抵稅額（住宅ローン控除）遺失**：
  在職期間若原本符合房貸抵稅資格，由於 `applyWorkingTaxes` 未被呼叫，`mortgageResult` 中的控除額完全無法被帶入所得稅折抵或隔年住民稅抵免通道，造成該年度稅負無法享受到抵稅利益。
- **稅款退補與結算失真**：
  在真實日本稅法中，年中退休退職者在年底若未再就職，應透過退職時之源泉徵收票於次年 2~3 月辦理「確定申告」，將上半年已繳納之過高源泉稅進行退稅結算。現有管線若將全年薪水視為 0，且跳過工作所得稅引擎，則無法準確反映該筆退稅（Kakutei Shinkoku Refund）之現金流回補。

#### 情境二：未來模擬年度於「年中退休」
- 若未來某一年使用者達到 `targetRetirementAge`，引擎在該整年度一律將其視為已退休（薪資收入為 0，只領年金）。
- 若真實生活情境為特定月份（如滿 65 歲當月或會計年度末 3 月底）退休，該年應有數個月薪資所得與數個月年金所得之混合申報，但現行系統會直接在該年切換為整年退休模式，使該年度的住民稅（依前一年度所得課徵）與所得稅無法反映部分月份薪資之社保扣除與薪資所得扣除額。

### 3. 建議 Reviewer 討論面向
1. **短期（現行架構內）**：
   方向 A 已阻斷流動現金遭負向扣減的核心資產錯誤，維持現狀已能確保大部分長期退休資產路徑不受致命扭曲。
2. **中長期（是否支援混合年度）**：
   是否需要在 `targetRetirementAge` 年度支援 `retirementMonth` 參數？
   若支援，則該年度需分段計算：
   - 退休前月份：按薪資所得、源泉扣繳及社保計算。
   - 退休後月份：按國民年金/國保自負額或公的年金計算。
   - 年末結算：統一透過確定申告或年末調整進行所得合併課稅結算與住宅ローン控除抵免。

### 4. 二次review更新：房貸抵稅額遺失的實際範圍比上面「情境一」描述的更廣，金額規模可能更大
上面「情境一」的文字只描述了「起算年因為被判定整年退休，房貸抵稅額在那一年遺失」。二次review查證重新追蹤了完整的呼叫鏈路，發現這個限制的實際範圍比這段文字呈現的更嚴重：

查證確認[MortgageCalculator.simulateMortgagesForYear](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)本身完全不檢查退休狀態（整份檔案查無`isRetired`），只要房貸還在法定控除年限窗口內（現行規則下通常是10~13年），每一年都會照常算出`yearEndDeduction`/`kakuteiDeduction`這兩個控除額。但這兩個值目前**唯一**的消費者是`ActiveTaxCalculationEngine.calculate(...)`，而這個方法**只會從`applyWorkingTaxes`被呼叫**——`applyRetireeTaxes`呼叫的是另一條完全不同的路徑（`calculatePensionTax`），這個方法完全不接收、也不參照任何房貸控除額參數。

這代表只要`isRetired()`判定為true，不管是起算年還是未來任何一年，`applyWorkingTaxes`永遠被整段跳過——房貸控除額不是「起算年那一年」才遺失，是**從退休那一年開始，往後只要這個人在模擬裡維持退休狀態，房貸只要還在控除窗口內，每一年的控除額都會被完全丟棄，直到控除窗口本身到期為止**。例如一個人50歲買房（13年控除窗口），60歲退休，模擬會讓他在退休後剩下的3年控除窗口裡，每年都遺失這筆控除——不是只遺失起算年那一年的部分金額。

查證依現有程式碼的控除額計算方式（`年末殘高×控除率`，並受`maxTaxDeductionAmount`跟實際所得稅負上限雙重封頂）估算，典型每年控除額規模落在20萬~46萬日圓之間；疊加「退休後維持退休直到控除窗口到期」這個更廣的發生範圍，多年加總起來的淨資產低估規模可能達到數十萬到百萬日圓等級——比原本「情境一」文字只強調起算年單一年份的描述，影響金額大得多。

這個限制維持原本「延伸探討」定位——記錄為已知但尚未決定要不要處理的架構限制，不代為決定是否要現在就投入`retirementMonth`這類中長期架構支援，但這份issue更新後明確反映「這不是起算年專屬的小問題，是退休後每年持續發生、影響範圍會隨退休年數疊加」這個更接近真實情況的事實。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                             10023  13.41G   12.19    16:53:37  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 247b30f4..a596fe0d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -75,14 +75,18 @@ public class YearlySimulationPipeline {
         // Restore NISA principal from previous year's liquidation
         restoreNisaPrincipal(context);
 
-        // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and inhabitant tax rollover credit)
+        // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and
+        // inhabitant tax rollover credit)
         PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
         if (prevTax != null && prevTax.pendingKakuteiShinkokuRefund() > 0) {
             context.setCurrentLiquidCash(context.getCurrentLiquidCash() + prevTax.pendingKakuteiShinkokuRefund());
-            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) upon cash disbursement to prevent duplicate payout.
+            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) upon cash disbursement
+            // to prevent duplicate payout.
             // DO NOT set context.setPreviousYearTaxRecord(null) here:
-            // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in context so that
-            // ActiveTaxCalculationEngine can consume them later in the annual pipeline for inhabitant tax
+            // We MUST preserve unusedMortgageDeductionForInhabitantTax and grossSalary in
+            // context so that
+            // ActiveTaxCalculationEngine can consume them later in the annual pipeline for
+            // inhabitant tax
             // deduction and Plan-21 prior-year income assessment.
             context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                     prevTax.grossSalary(),
@@ -299,8 +303,7 @@ public class YearlySimulationPipeline {
         }
         if (context.getGrowthNisaPrincipalToRecoverNextYear() > 0) {
             context.setAccumulatedGrowthNisaPrincipal(
-                    Math.max(0,
-                            context.getAccumulatedGrowthNisaPrincipal()
+                    Math.max(0, context.getAccumulatedGrowthNisaPrincipal()
                             - context.getGrowthNisaPrincipalToRecoverNextYear()));
             context.setGrowthNisaPrincipalToRecoverNextYear(0L);
         }
@@ -313,7 +316,7 @@ public class YearlySimulationPipeline {
         long fullYearNetIncome = state.getNetIncome();
         long ytdNetIncome = 0;
 
-        if (context.getYear() == context.getCurrentYear()) {
+        if (context.getYear() == context.getCurrentYear() && !context.isRetired()) {
             ytdNetIncome = computeYtdNetIncomeBaseline(context);
         }
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
index 9e8d9bba..fc18a55b 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -60,6 +60,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.Mortgag
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.DependentDeductionEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.SpouseDeductionEngine;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
 import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 
@@ -104,7 +105,9 @@ class YearlySimulationPipelineTest {
                 taxEngine,
                 yearEndAdjustmentEngine);
 
-        lenient().when(taxAdvantagedAccountProjector.processAccountsForYear(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
+        lenient()
+                .when(taxAdvantagedAccountProjector.processAccountsForYear(any(), anyInt(), anyInt(), anyInt(),
+                        anyInt(), anyInt()))
                 .thenReturn(new InvestmentContributions(0L, 0L, 0L, 0L));
         lenient().when(incomeExpenseCalculator.calculateExpensesForYear(any(), any(), anyInt(), anyInt()))
                 .thenReturn(0L);
@@ -133,8 +136,10 @@ class YearlySimulationPipelineTest {
 
     /**
      * Scenario A: Standard full deduction across multiple years.
-     * - Year 1 (Occupancy): 210,000 JPY deduction routed via Kakutei Shinkoku; actual paid income tax > 250,000 JPY.
-     * - Year 2: Spring cash strictly disburses 210,000 JPY refund; Year 2 Year-End Adjustment applies 210,000 JPY deduction.
+     * - Year 1 (Occupancy): 210,000 JPY deduction routed via Kakutei Shinkoku;
+     * actual paid income tax > 250,000 JPY.
+     * - Year 2: Spring cash strictly disburses 210,000 JPY refund; Year 2 Year-End
+     * Adjustment applies 210,000 JPY deduction.
      * - Year 3: Spring cash refund is 0 JPY (prevents duplicate refund).
      */
     @Test
@@ -159,7 +164,8 @@ class YearlySimulationPipelineTest {
         assertThat(resultYear1.getYear()).isEqualTo(2026);
         PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
         assertThat(recordAfterYear1).isNotNull();
-        // With 8M salary, baseIncomeTax is > 250,000 JPY, so 210,000 JPY is fully refundable
+        // With 8M salary, baseIncomeTax is > 250,000 JPY, so 210,000 JPY is fully
+        // refundable
         assertThat(recordAfterYear1.baseIncomeTax()).isGreaterThan(250_000L);
         assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(210_000L);
         assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
@@ -177,13 +183,15 @@ class YearlySimulationPipelineTest {
 
         assertThat(resultYear2.getYear()).isEqualTo(2027);
 
-        // Year-end adjustment directly reduced Year 2 estimated income tax by 210,000 JPY (plus 2.1% reconstruction tax)
+        // Year-end adjustment directly reduced Year 2 estimated income tax by 210,000
+        // JPY (plus 2.1% reconstruction tax)
         long expectedIncomeTaxReduction = 210_000L + (long) (210_000L * 0.021);
         assertThat(resultYear1.getEstimatedIncomeTax() - resultYear2.getEstimatedIncomeTax())
                 .isEqualTo(expectedIncomeTaxReduction);
 
         // Spring cash received the 210,000 JPY refund from Year 1
-        // Net savings of Year 2 is higher by expectedIncomeTaxReduction compared to Year 1, plus 210,000 JPY spring cash refund
+        // Net savings of Year 2 is higher by expectedIncomeTaxReduction compared to
+        // Year 1, plus 210,000 JPY spring cash refund
         long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
         long expectedCashDelta = resultYear2.getNetSavings() + 210_000L;
         assertThat(cashDelta).isEqualTo(expectedCashDelta);
@@ -209,10 +217,14 @@ class YearlySimulationPipelineTest {
     }
 
     /**
-     * Scenario B: Low-income with inhabitant tax rollover (Core Issue 137 fix verification).
-     * - Year 1: Mortgage deduction is 210,000 JPY, but baseIncomeTax is capped (~103,200 JPY < 210,000 JPY).
-     * - Year 2: Spring cash increases by the capped paid tax (NOT 210,000 JPY, solves Issue 137 Problem 2).
-     * - Year 2: Inhabitant tax deducts exactly 97,500 JPY (statutory rollover cap, solves Issue 137 Problem 1).
+     * Scenario B: Low-income with inhabitant tax rollover (Core Issue 137 fix
+     * verification).
+     * - Year 1: Mortgage deduction is 210,000 JPY, but baseIncomeTax is capped
+     * (~103,200 JPY < 210,000 JPY).
+     * - Year 2: Spring cash increases by the capped paid tax (NOT 210,000 JPY,
+     * solves Issue 137 Problem 2).
+     * - Year 2: Inhabitant tax deducts exactly 97,500 JPY (statutory rollover cap,
+     * solves Issue 137 Problem 1).
      */
     @Test
     void testScenarioB_lowIncomeWithInhabitantRollover_cappedRefundAndCredit() {
@@ -240,14 +252,16 @@ class YearlySimulationPipelineTest {
         assertThat(cappedRefund).isEqualTo(recordAfterYear1.baseIncomeTax());
         assertThat(cappedRefund).isLessThan(210_000L);
 
-        // Leftover mortgage deduction rollover is capped at statutory inhabitant tax cap (<= 97,500 JPY)
+        // Leftover mortgage deduction rollover is capped at statutory inhabitant tax
+        // cap (<= 97,500 JPY)
         long rollover = recordAfterYear1.unusedMortgageDeductionForInhabitantTax();
         assertThat(rollover).isGreaterThan(0L);
         assertThat(rollover).isLessThanOrEqualTo(97_500L);
 
         long cashAtEndOfYear1 = context.getCurrentLiquidCash();
 
-        // --- Year 2 (2027): Mortgage deduction ends (0L) to isolate rollover effect ---
+        // --- Year 2 (2027): Mortgage deduction ends (0L) to isolate rollover effect
+        // ---
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
 
@@ -255,11 +269,13 @@ class YearlySimulationPipelineTest {
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
 
-        // Problem 1 Verification: Year 2 inhabitant tax is credited by exactly the rollover amount
+        // Problem 1 Verification: Year 2 inhabitant tax is credited by exactly the
+        // rollover amount
         assertThat(resultYear1.getEstimatedInhabitantTax() - resultYear2.getEstimatedInhabitantTax())
                 .isEqualTo(rollover);
 
-        // Problem 2 Verification: Year 2 spring cash refund received EXACTLY the capped amount, NOT 210,000 JPY
+        // Problem 2 Verification: Year 2 spring cash refund received EXACTLY the capped
+        // amount, NOT 210,000 JPY
         long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
         long expectedCashDelta = resultYear2.getNetSavings() + cappedRefund;
         assertThat(cashDelta).isEqualTo(expectedCashDelta);
@@ -267,8 +283,10 @@ class YearlySimulationPipelineTest {
 
     /**
      * Scenario B (Direct): Verifies the exact statutory illustration:
-     * With 50,000 JPY paid income tax and 97,500 JPY inhabitant tax rollover from previous year,
-     * Year 2 spring cash increases by exactly 50,000 JPY, and inhabitant tax reduces by exactly 97,500 JPY.
+     * With 50,000 JPY paid income tax and 97,500 JPY inhabitant tax rollover from
+     * previous year,
+     * Year 2 spring cash increases by exactly 50,000 JPY, and inhabitant tax
+     * reduces by exactly 97,500 JPY.
      */
     @Test
     void testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500() {
@@ -278,7 +296,8 @@ class YearlySimulationPipelineTest {
         SimulationContext context = createContext(2027, 36, salary, initialCash);
         YearMonth nowMonth = YearMonth.of(2027, 1);
 
-        // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY inhabitant rollover
+        // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY
+        // inhabitant rollover
         context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                 salary, 2_000_000L, 50_000L, 50_000L, 97_500L));
 
@@ -290,7 +309,8 @@ class YearlySimulationPipelineTest {
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
 
-        // Inhabitant tax credited by 97,500 JPY (206,600 uncredited base + 5,000 flat levy - 97,500 = 114,100 JPY)
+        // Inhabitant tax credited by 97,500 JPY (206,600 uncredited base + 5,000 flat
+        // levy - 97,500 = 114,100 JPY)
         assertThat(result.getEstimatedInhabitantTax()).isEqualTo(114_100L);
 
         // Spring cash refunded exactly 50,000 JPY
@@ -301,7 +321,8 @@ class YearlySimulationPipelineTest {
 
     /**
      * Scenario C: High income disqualification phase-out (Issue 139 verification).
-     * - Year 1: Gross salary is 25,000,000 JPY (employment income > 20,000,000 JPY statutory threshold).
+     * - Year 1: Gross salary is 25,000,000 JPY (employment income > 20,000,000 JPY
+     * statutory threshold).
      * - Year 2: Spring cash refund is 0 JPY; Inhabitant tax credit is 0 JPY.
      */
     @Test
@@ -324,7 +345,8 @@ class YearlySimulationPipelineTest {
 
         assertThat(resultYear1.getYear()).isEqualTo(2026);
 
-        // Phase-out check in Year 1: both refund and inhabitant rollover must be strictly 0
+        // Phase-out check in Year 1: both refund and inhabitant rollover must be
+        // strictly 0
         PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
         assertThat(recordAfterYear1).isNotNull();
         assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
@@ -337,8 +359,62 @@ class YearlySimulationPipelineTest {
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 nowMonth, 60, 60, 60, 65, 65, initialAge, 36);
 
-        // Zero spring cash refund received in Year 2 (cash change is strictly net savings)
+        // Zero spring cash refund received in Year 2 (cash change is strictly net
+        // savings)
         long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
         assertThat(cashDelta).isEqualTo(resultYear2.getNetSavings());
     }
+
+    /**
+     * Issue 172 Verification:
+     * When user retires in the initial simulation year (age ==
+     * targetRetirementAge),
+     * YTD net income from historical salary earned earlier this year must NOT be
+     * subtracted
+     * from fullYearNetIncome (which is 0 or pension), preventing phantom deduction
+     * of liquid cash.
+     */
+    @Test
+    void testInitialYearRetirement_doesNotSubtractYtdNetIncomeFromLiquidCash() {
+        int initialAge = 65;
+        int targetRetirementAge = 65;
+        int pensionStartAge = 70;
+        long initialCash = 5_000_000L;
+        SimulationContext context = createContext(2026, initialAge, 0L, initialCash);
+        YearMonth nowMonth = YearMonth.of(2026, 7);
+
+        // Populate YTD salary and deductions already received and saved in bank cash
+        // (net = 2,312,000 JPY)
+        context.setYtdGrossIncome(3_000_000L);
+        context.setYtdIncomeTax(100_000L);
+        context.setYtdResidentTax(150_000L);
+        context.setYtdHealthInsurance(150_000L);
+        context.setYtdPension(270_000L);
+        context.setYtdEmploymentInsurance(18_000L);
+
+        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(0L);
+        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
+                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
+
+        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, initialAge);
+
+        // Before fix: remainingNetIncome was 0 - 2,312,000 = -2,312,000 JPY.
+        // With Direction A fix: ytdNetIncome is not subtracted when retired, so
+        // netIncome is 0L.
+        assertThat(result.isRetired()).isTrue();
+        assertThat(result.getNetIncome()).isEqualTo(0L);
+
+        // Health insurance for early retiree (age < 75 with 0 pension) is added to
+        // annualExpenses:
+        // 400,000 / 12 * 6 = 200,000 JPY
+        long expectedAnnualExpenses = Math.round((TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM / 12.0) * 6);
+        assertThat(result.getExpectedAnnualExpenses()).isEqualTo(expectedAnnualExpenses);
+
+        // Cash delta is only -expectedAnnualExpenses; 2,312,000 JPY YTD net income was
+        // NOT deducted
+        long expectedCash = initialCash - expectedAnnualExpenses;
+        assertThat(result.getProjectedLiquidCash()).isEqualTo(expectedCash);
+    }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                  10024  13.39G   12.22    16:54:06  