# [FIXED] 190：模擬起算當年同時是退休年時，`applyYearEndAdjustment`算出的巨大負數稅額，被前端既有的「過渡年退稅」Tooltip機制套用了一段對不上退休情境的說明文字——不是「不該顯示負數」，是「負數背後的真實原因跟UI上講的原因不一樣，需要依`isRetired`分流成兩種不同的說明」

## 這份issue怎麼來的
這是[issue-172](issue-172.md)（退休當年薪資YTD被從年金淨收入裡雙重扣減）二次review查證那份修正時，順手挖到的一個**同一種root cause、但完全沒被那次修正觸及**的獨立問題。原本這份issue的第一版建議修法是「幫`applyYearEndAdjustment`加一個`!context.isRetired()`防衛條件，讓退休當年整段跳過、不要算出負數」——這個方向後來被使用者推翻：使用者指出前端本來就有一套**既有、刻意設計**的機制，讓過渡年算出負數時用綠色文字+Tooltip告訴使用者「這是退稅，不是算錯」，如果190的修法讓退休當年的負數完全消失（變成顯示`-`或0），會跟這個既有機制互相矛盾——同樣是過渡年，在職時負數被溫柔解釋成退稅，退休時卻讓它完全不出現，邏輯不一致。重新查證後確認：問題不在於「要不要顯示負數」，是「現有的Tooltip說明文字，只對應在職過渡年的情境，套用在退休當年時說明內容是錯的」。這份issue因此整個重寫，方向跟原本的「加防衛條件」完全不同。

為了讓這份文件不需要回頭查閱issue-172就能理解完整背景，下面先重述172原本的問題跟修法，再說明這份issue要處理的部分。

## 背景：issue-172原本在修什麼
系統模擬退休資產時，退休狀態是用二元旗標`context.setRetired(age >= targetRetirementAge)`整年度判斷——如果使用者設定的退休年齡剛好等於他現在的實際年齡，模擬起算的第一年（過渡年）整年就會被標記成「已退休」。

一旦整年被標記成退休，`YearlySimulationPipeline.applyWorkingTaxes`（計算在職薪資稅負、也是唯一會把房貸抵稅額算進去的地方）會被整段跳過（進入條件`if (!context.isRetired() && effectiveAnnualSalary > 0)`），改由`applyRetireeTaxes`接管，用年金稅取代薪資稅去算這一年的淨收入。

issue-172發現的原始bug：同一時間，`applyCashflowAndSettlement`方法會把「今年至今已經實際入帳的薪資淨額」（`ytdNetIncome`）從這一年的`fullYearNetIncome`（退休後變成很小的年金淨所得，甚至是0）裡扣掉，導致現金憑空蒸發，可能觸發不必要的資產賤賣。這個bug已經修好，這份issue不重複討論，詳見issue-172本身。

## 相關檔案
**以下「相關檔案」「現狀」「具體錯誤情境」三節是191落地前寫的歷史記錄，保留原文不動；191落地後的最新查證結果、行號、以及這份issue現在是否還成立，見下方新增的「191落地後的複核」一節。**
- [YearlySimulationPipeline.java:275-294](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`applyYearEndAdjustment`，算出負數的地方，第276行判斷條件只有`if (age == initialAge)`）
- [YearEndAdjustmentEngine.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/YearEndAdjustmentEngine.java)（計算公式`remainingX = trueAnnualX - ytdX`，程式碼自己的註解寫著「負數代表多繳了、該退稅」）
- [AssetSimulationYearlyResult.java:51](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/AssetSimulationYearlyResult.java)、[AssetSimulationYearlyResponse.java:48](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationYearlyResponse.java)（查證確認`isRetired`這個旗標已經完整串通到API回應，不需要任何後端改動就能在前端取用）
- [`Fixed-52-01- 實作過渡年年末調整前端提示 (Tax Adjustment Tooltips).md`](../fixed/Fixed-52-01-%20實作過渡年年末調整前端提示%20(Tax%20Adjustment%20Tooltips).md)（這份issue要尊重、不要推翻的既有產品決策文件，詳見下方「既有的產品決策」一節）
- [asset-simulation-table.tsx:28-47](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx)（`TaxAdjustmentCell`，現有實作，查證確認這個元件目前**只接收`value`這一個參數**，完全沒有接收`isRetired`，無法區分兩種情境）
- 同檔案`:77-78`（查證確認同一個表格元件，在別的地方（整行底色、年齡後面的`(Ret)`標籤）已經在使用`row.isRetired`這個欄位——代表要把這個欄位傳進`TaxAdjustmentCell`，不需要新增資料來源，單純是這個元件目前沒有被傳入這個已經存在的值）

## 既有的產品決策：過渡年顯示負數是刻意設計，不是bug，這份issue不能推翻它
[Fixed-52-01](../fixed/Fixed-52-01-%20實作過渡年年末調整前端提示%20(Tax%20Adjustment%20Tooltips).md)記錄了這個既有決定：「當模擬第一年的預扣稅超過全年理論應繳時，系統會正確產生負數稅金（代表退稅），我們將在表格中用綠色正向文字顯示，並附上懸浮提示（Tooltip）解釋原因」，目的是「解決使用者看到稅金為負數時產生的『系統算錯』疑慮」。

查證[asset-simulation-table.tsx:28-47](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx)確認這個機制已經實作在目前的程式碼裡，`TaxAdjustmentCell`元件對負數值會顯示綠色文字加底線，滑鼠移上去會看到這段Tooltip文字：
> "The withheld tax this year has exceeded your annual tax liability (often due to deductions like insurance, iDeCo/DC, or mortgages that only apply during year-end adjustments). A negative value means you will receive this amount as a tax refund. This is not a calculation error."

這份issue完全同意並沿用「過渡年負數=正確行為，不該被隱藏或擋掉」這個核心立場——第一版建議的「加防衛條件讓退休當年跳過整段計算」方向已經確認是錯的，不會這樣修。

## 現狀：既有Tooltip文字的敘事，建立在「今年會被公司辦年末調整」這個前提上，退休當年這個前提不成立
上面引用的Tooltip文字，敘事核心是「今年預扣的稅超過全年真實應繳，常見原因是保險費、iDeCo/DC提繳、房貸控除這類扣除額只有在年末調整時才會被套用」——這整段說明的前提是「這個人今年會被現在這家公司辦年末調整」，這是日本稅制裡真實存在的機制：年末調整只會由「12月31日當天還在職的那家公司」幫在職員工辦理。

退休當年完全不符合這個前提。查證`applyYearEndAdjustment`方法確認，退休當年`state.getEstimatedIncomeTax()`等三個欄位，此刻已經被`applyRetireeTaxes`正確設定成依剩餘月份比例縮小過的小額年金稅（甚至因為還沒到年金請領年齡而全部是0）。這個方法把這幾個退休後的小額年金稅，去跟YTD欄位（退休前在職期間真實繳納的、金額大得多的薪資稅）相減，算出來的負數：
1. **量級跟正常在職過渡年的退稅完全不成比例**：正常在職過渡年的負數，是「某幾項扣除額差異」造成的落差，通常是幾萬到幾十萬日圓等級；退休當年算出來的負數，是「退休前整段YTD薪資稅」整筆被扣掉，量級可能達到幾十萬甚至上百萬日圓。
2. **真實世界裡拿回這筆錢的途徑不一樣**：在職過渡年的退稅，是「現在這家公司」在年末調整時直接退還；退休當年如果年底前沒有被其他公司重新雇用，使用者必須在**次年2-3月自己辦確定申告**才能拿回這筆溢繳的稅款——這是完全不同的時間線跟辦理方式。

查證確認[`TaxAdjustmentCell`元件](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx)目前只接收`value`這一個參數（第28行`function TaxAdjustmentCell({ value }: { value?: number })`），完全沒有接收`isRetired`，所以不管負數是在職過渡年算出來的、還是退休當年算出來的，使用者看到的都是同一段「常見原因：保險費/iDeCo/房貸」的說明——退休的使用者點開這個Tooltip，會讀到一段完全對不上自己情況的解釋。

## 為何是問題
這不是「系統算錯」，是「系統給使用者的解釋文字錯了」——跟Fixed-52-01最初想解決的「使用者以為系統算錯」疑慮是同一類風險，只是觸發情境不同。退休的使用者看到一個金額遠大於平常退稅規模的負數，點開Tooltip卻只看到跟自己情況無關的「保險費/iDeCo/房貸扣除額」說明，不會因此理解「這是因為我今年退休、沒有年末調整，需要自己辦確定申告才能拿回這筆錢，而且要等到次年2-3月」——這段真正適用於自己情況的資訊，使用者完全得不到，Tooltip反而成為誤導的來源。

## 具體錯誤情境
使用者今年65歲，設定65歲退休，7月跑模擬（1-6月薪水已經實際入帳，YTD所得稅10萬円、住民稅15萬円、社會保險43萬8千円）。因為還沒到年金請領年齡，這一年的年金稅額是0，`applyYearEndAdjustment`算出`0 - 100,000 = -100,000`等三個負數。使用者在模擬結果表格裡看到這一年的「Income Tax」欄位顯示綠色的「-¥100,000」，滑鼠移上去看到Tooltip寫著「常見原因：保險費、iDeCo/DC提繳、房貸控除……」——使用者今年根本沒有換過這些扣除額設定，完全看不懂這段說明跟自己的狀況有什麼關係，更不會知道「這筆錢要等到次年2-3月自己辦確定申告才能拿到」這個關鍵的真實資訊。

## 跟issue-172、question-191共享的根因
跟issue-172同一個根因——系統用二元旗標判斷整年退休/在職，任何沒有針對「退休當年」特別處理的既有邏輯，都會預設沿用「在職年」的計算或說明方式去處理一個實際上已經轉為退休身分的年度。issue-172的`applyCashflowAndSettlement`已經修好（現金層面）；這份issue要處理的是同一個模式在UI說明文字層面的體現。

跟[question-191](question-191.md)「補充：房貸確定申告的真實時序」「討論過程中釐清的真實世界稅制機制」兩節討論的內容直接相關——這份issue要補的Tooltip文字，內容上需要跟191正在討論的「年中離職自己辦確定申告、次年2-3月才拿到退稅」這個真實世界機制一致，不是這份issue自己另外發明一套說法。

## 建議修正方向（191落地前的版本，已被下方「191落地後的複核」撤回，保留原文不動）
不是多方案選一，方向已經透過上面的討論確認：

1. `TaxAdjustmentCell`元件新增一個`isRetired`參數（或任何能表達「這一列是不是退休當年」的參數），`AssetSimulationTable`傳入時直接用現成的`row.isRetired`（這個欄位已經在同一個檔案的第77-78行被使用，不需要新增資料來源）。
2. 負數時的Tooltip文字依`isRetired`分成兩段不同的說明：
   - `isRetired`為false（在職過渡年）：維持現有文字不變（「常見原因：保險費、iDeCo/DC提繳、房貸控除……今年的預扣稅已超過全年應繳，年末調整會退還」）。
   - `isRetired`為true（退休當年）：改成反映退休真實情境的說明——今年退休、沒有被現在的公司辦理年末調整，這筆溢繳的稅款需要在次年2-3月自己辦確定申告才能拿回。
3. 第二段文字的具體措辭，需要等[question-191](question-191.md)「年金受給者の確定申告不要制度」這個未確認項目有查證結果後才能精確措辭（例如要不要提到年金所得是否也要一併申報），這份issue不代為斷言這塊還沒查證的細節，先用現有已經確認的事實（年中離職沒有年末調整、需要自己辦確定申告、次年2-3月才能拿到）去寫文字，不要把還沒查證的部分寫進去。

## 191落地後的複核：這份issue描述的情境已經不會再發生，建議修正方向整段撤回

這是收到Grok對這份issue的質疑後，親自重新讀過191落地後的最新程式碼得到的結論：**不是bug被修好了才消失，是191新增的退休當年分段邏輯，從結構上就讓這份issue原本設想的「退休當年`applyYearEndAdjustment`算出巨大負數、套用對不上的Tooltip文字」這個情境不會再出現**。上面「相關檔案」「現狀」「具體錯誤情境」「建議修正方向」四節描述的都是191落地前的程式碼行為，保留原文不動，這裡只記錄191落地後重新查證的結果。

**查證[YearlySimulationPipeline.java:215-220](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認，`applyYearEndAdjustment`現在被一個新的外層判斷包住**：
```java
if (isRetirementYear && workingMonths < 12) {
    // Mid-year retirement: no employer at year end to perform Year-End Adjustment
} else {
    applyYearEndAdjustment(context, state, age, initialAge, effectiveAnnualSalary);
}
```
`applyYearEndAdjustment`本身現在在[第378-398行](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（不是這份issue原本記錄的第275行，行號已經偏移），內部仍然只有`if (age == initialAge)`這一層判斷，邏輯本身沒有變，但現在的呼叫點多了一層外層保護。

**退休當年會分成兩種情境，查證結果如下**：

1. **年中退休（`workingMonths < 12`，典型例子是`BIRTH_MONTH`模式、生日不在12月）**：`applyYearEndAdjustment`整段被跳過，**不會**算出這份issue描述的那個巨大負數寫回`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`這三個欄位。退休前在職段跟YTD的差額，改由[第278-294行](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)另外算出`pendingWorkingSegmentTaxAdjustment`，查證[第77-82行](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認這筆金額隔年會直接加進`currentLiquidCash`（現金），**完全不會寫回三個稅額欄位**。前端`TaxAdjustmentCell`讀的正是這三個稅額欄位（查證[AssetSimulationYearlyResult.java]、[AssetSimulationYearlyResponse.java]的欄位對應關係確認），所以這份issue描述的「使用者在Income Tax欄位看到巨大負數、Tooltip說明對不上」這個畫面，在年中退休的情境下**根本不會出現**——不是負數消失了、是這筆錢現在走的是完全不同的欄位（現金），不經過`TaxAdjustmentCell`這個元件。

2. **退休月剛好是12月（預設`DECEMBER`模式，或`BIRTH_MONTH`模式剛好生日在12月）**：`workingMonths == 12`，不滿足`workingMonths < 12`，`applyYearEndAdjustment`**會**執行，有機會算出負數。但這個情境下，使用者整年都在職、12月31日當天確實還在這家公司——這正是日本年末調整制度真實適用的對象（「12月31日當天還在職的那家公司」辦理年末調整），既有Tooltip文字講的「常見原因：保險費、iDeCo/DC提繳、房貸控除……年末調整會退還」在這個情境下**完全正確**，不是對不上的說明，不需要修改。

**`isRetired`本身也不是能用來分流說明文字的正確依據**：查證[第64行](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)`context.setRetired(age >= targetRetirementAge)`確認，`isRetired`從退休那一年開始，往後每一年都是`true`，不是只有退休當年才是`true`。而`applyYearEndAdjustment`本身只在`age == initialAge`這一年有機會執行（第380行），退休後的其他年份完全不會呼叫到這個方法——如果照191落地前建議修正方向的做法，用`isRetired`去切換Tooltip文字，會在邏輯上把「退休後所有年份」都當成「這一年是退休當年」處理，即使那些年份根本不會算出這份issue討論的負數，說明文字的判斷依據本身也是錯的。

**結論**：191落地後，這份issue原本設想的「退休當年負數配上錯誤說明文字」這個具體情境，在兩種退休月分支下都不會發生（年中退休時這筆錢不經過這個UI元件；12月退休時既有說明文字本身就是對的）。「建議修正方向」整段（新增`isRetired`參數、分流Tooltip文字）**不應該照做**——照做反而會在邏輯上使用一個跟「這一年是否真的是退休當年」不對應的旗標，去判斷一個現在已經不會再出現的情境。這份issue建議標記為WONTFIX，具體去留留給使用者決定。

## 這份文件沒有解決的部分
- 191落地前的「建議修正方向」已確認不應該照做，但這份issue要標記成WONTFIX還是直接移除，留給使用者決定。
- Tooltip文字第二段（退休情境）的精確措辭，需要等question-191裡年金確定申告不要制度的查證結果，或至少確認這份文案不需要依賴那個還沒查證的細節——**這一點現在已經不再適用**，因為191落地後退休當年的負數不會再經過`TaxAdjustmentCell`這個元件，沒有第二段文字需要寫。
- 要不要現在就修正，還是留給之後排入時程，留給使用者決定。
