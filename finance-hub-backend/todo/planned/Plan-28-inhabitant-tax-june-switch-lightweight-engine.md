# Plan 28：住民稅6月切換輕量引擎——不需要月級別模擬引擎（Plan-21雙引擎架構），每年只需要多呼叫一次既有的稅額計算函數、搭配「繼承去年稅額+用去年所得算今年稅額」這兩個數字做加權混合，就能精確還原真實時序

## 這份計畫怎麼來的
這是在191（退休當年稅務分段計算）的review過程中，討論「`PreviousYearTaxRecord`裡`taxableIncomeForIncomeTax`這類『前一年所得』欄位,目前完全沒人讀取,是不是死欄位」時,使用者提出的方案——不需要等[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)那種把整個模擬引擎改成月單位的大改，只要把住民稅的計算結果本身拆成兩段、依比例混合，就能精確還原「住民稅6月切換」這個真實世界的時序規則。這份文件經過四輪修正：第一輪確認了真實時序牽涉到「前年」跟「去年」兩個所得年份；第二輪確認不需要分別保存兩年的所得再各自計算，因為「去年該用的稅額」在去年模擬時就已經算好，只要直接繼承這個算好的數字，每年只需要新呼叫一次稅額計算函數；第三輪使用者指出切換月份是不是「6月」需要查「住民稅額決定通知書」核實，不能沿用Plan-21原文裡未附查證來源的斷言；第四輪（本次）使用者查到並提供了可信來源（住民稅因依前一年1-12月所得課徵，地方政府約5月完成核定、6月開始適用新稅額，不分特別徵收或普通徵收），確認切換月是6月，這份文件改回用具體數字6月。

## 針對什麼問題
[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)「需求一」主張：日本住民稅是用「前一年」的所得，在6月重新核定接下來12個月要扣的住民稅額，之前的月份（1-5月）扣的是上一次核定、用更早一年所得算出來的舊稅額。**Plan-21原文的「6月」說法本身沒有附帶任何法源引用或查證來源，是直接當作既定事實寫下來的斷言——這份計畫原本因此改用變數`M`描述規則、要求實作前核實；使用者後續查到並提供了可信來源（住民稅依前一年1-12月所得課徵，地方政府約5月完成核定、6月開始適用，不分特別徵收/普通徵收），確認切換月確實是6月**，下面直接用6月、5個月（1-5月）、7個月（6-12月）這些具體數字。

現行系統（`ActiveTaxCalculationEngine.calculate(...)`第118-119行、第210-211行、第241行；`calculatePensionTax(...)`第369-370行、第379-380行、第388行）完全不管這個時間差——不管在職段還是退休段，都是直接拿「今年」的所得去查「今年」的住民稅稅率表，整年用同一個基準。

## 核心概念：某一年的住民稅總額，是「去年算好的稅額」（繼承，不重算）+「用去年所得新算出來的稅額」（本年度唯一需要新計算的部分），依5個月（1-5月）與7個月（6-12月）比例混合

用具體年份把真實時序走一次（把每年6月核定的那筆年度稅額記作`TaxAmount_N`，它是用`N-1`年的所得算出來的，繳納期間橫跨`N`年6月到`N+1`年5月）：

- **2026年1-5月**繳的稅，是`TaxAmount_2025`（2025年6月核定、用2024年所得算出來）的尾段5個月。
- **2026年6-12月**繳的稅，是`TaxAmount_2026`（2026年6月核定、用2025年所得算出來）的前段7個月。

```
今年住民稅總額 = TaxAmount_去年 × 5/12 + TaxAmount_今年 × 7/12
```

**關鍵簡化**：`TaxAmount_去年`不需要在今年重新計算——它在模擬去年的時候，本來就已經被算出來、用在去年自己6-12月那一段了。這代表只要把這個已經算好的數字，原封不動繼承到今年，當作今年1-5月的基準即可，不需要額外保存「前年的所得」再重新呼叫一次計算函數。

真正需要在今年新計算的，只有`TaxAmount_今年 = f(去年所得)`——這需要知道「去年的所得」這一個數字，而這個數字本來就是去年自己模擬時產生的，只需要原樣繼承過來，在今年模擬時呼叫一次既有的稅額計算函數即可。

**這代表每年只需要繼承兩個數字（一個稅額、一個所得），每年只需要新呼叫一次既有的稅額計算函數，不是兩次**：
- 繼承`TaxAmount_{Y-1}`（已經算好的稅額，直接拿來用，不重算）→ 當作今年1-5月的基準。
- 繼承`income_{Y-1}`（去年的課稅所得）→ 今年呼叫一次`calculateInhabitantTax`算出`TaxAmount_Y`，當作今年6-12月的基準，同時這個新算出來的`TaxAmount_Y`，原樣繼承給明年當它的「1-5月基準」。
- 今年自己的課稅所得，繼承給明年，讓明年去算它自己的`TaxAmount_{Y+1}`。

### 查證確認：`calculateInhabitantTax`是純函數，完全不用修改，外層包一層「總稅額計算」的概念去管這兩個繼承數字
查證[StandardInhabitantTaxRateStrategy.java:51-58](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java)確認，`calculateInhabitantTax(long taxableIncome, long totalIncome)`是一個純函數——不查任何年份、不依賴任何歷史記錄，單純「給課稅所得，依固定稅率公式算出稅額」。這份計畫不修改這個方法本身，只是在它外面包一層新的協調邏輯：「讀出繼承的稅額+讀出繼承的所得→呼叫一次這個既有函數算出新稅額→兩個稅額依比例混合→把新稅額跟今年所得繼續往下傳」。

## 需要新增的狀態：`PreviousYearTaxRecord`新增兩個欄位——一個裝「已經算好、直接繼承的稅額」，一個裝「去年的課稅所得，供今年算新稅額用」

查證[PreviousYearTaxRecord.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/PreviousYearTaxRecord.java)（191落地後的最新狀態）：

```java
public record PreviousYearTaxRecord(
        long grossSalary,
        long taxableIncomeForIncomeTax,
        long baseIncomeTax,
        long pendingKakuteiShinkokuRefund,
        long unusedMortgageDeductionForInhabitantTax,
        long pendingWorkingSegmentTaxAdjustment) {
}
```

**現有的`taxableIncomeForIncomeTax`是「所得稅」版本的課稅所得,不能直接拿來用**——查證[ActiveTaxCalculationEngine.java:206-211](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，所得稅版本（`taxableIncomeForIncomeTax`）跟住民稅版本（`taxableIncomeForInhabitantTax`）的扣除額計算是分開兩條線（`totalDeductionsIncomeTax`跟`totalDeductionsInhabitantTax`各自獨立加總，兩者的基礎控除、配偶控除等各項扣除額數字不完全相同），不能混用——這份計畫需要住民稅版本的課稅所得。

**新增兩個欄位**：
```java
public record PreviousYearTaxRecord(
        long grossSalary,
        long taxableIncomeForIncomeTax,
        long baseIncomeTax,
        long pendingKakuteiShinkokuRefund,
        long unusedMortgageDeductionForInhabitantTax,
        long pendingWorkingSegmentTaxAdjustment,
        long priorYearDeterminedInhabitantTax, // 去年算好的稅額，直接繼承，不重算，當今年1-5月的基準
        long priorYearTaxableIncomeForInhabitantTax) { // 去年的住民稅版課稅所得，今年用來算一次新稅額，當今年6-12月的基準
}
```

## 核心改動：住民稅計算這一步，改成「繼承稅額+新算一次稅額、依比例混合」取代「一次呼叫」；年末把新算出的稅額跟今年所得往下傳

**`calculate(...)`第240-241行**：
```java
// 現行：
long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
        .calculateInhabitantTax(taxableIncomeForInhabitantTax, employmentIncome);
```
**改成**：
```java
PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord(); // 如果上面已經讀過，重用同一個變數

// 沒有前一年記錄時（模擬起算第一年），兩項都退回用「今年」自己的資料當替代值——
// 這個退回不需要特別精確，見下方「起算年前半段的住民稅其實有真實事實資料可用，不靠這個退回值」。
long priorBasisTax = (prevTax != null)
        ? prevTax.priorYearDeterminedInhabitantTax()
        : inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
                .calculateInhabitantTax(taxableIncomeForInhabitantTax, employmentIncome);
long priorYearIncome = (prevTax != null) ? prevTax.priorYearTaxableIncomeForInhabitantTax() : taxableIncomeForInhabitantTax;

long newBasisTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
        .calculateInhabitantTax(priorYearIncome, employmentIncome);
        // 注意：第二個參數理論上也該是「去年的」employmentIncome（用來判斷均等割定額稅5000円要不要加），
        // 這份計畫不追求這個精確度——不是稅金模擬器，用今年的employmentIncome判斷這一項即可。

long inhabitantTax = Math.round(priorBasisTax * (5.0 / 12.0) + newBasisTax * (7.0 / 12.0));
```

**年末寫入`PreviousYearTaxRecord`時，把「新算出來的稅額」跟「今年的所得」往下傳給明年**：
```java
context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
        grossTotalSalary,
        taxableIncomeForIncomeTax,
        baseIncomeTax,
        actualKakuteiRefund,
        unusedForInhabitantTax,
        0L,
        newBasisTax,                    // 今年新算出來的稅額 → 變成明年1-5月的基準，明年不用重算
        taxableIncomeForInhabitantTax)); // 今年自己的課稅所得 → 變成明年用來算明年6-12月稅額的依據
```

**這個設計每年只呼叫一次`calculateInhabitantTax`去產生新稅額（`newBasisTax`），`priorBasisTax`是直接繼承，不是重新計算**——這是比「存兩個所得、每年各自呼叫一次」更精簡的做法，少了一次重複呼叫，而且概念上更貼近真實世界（去年的稅額本來就已經是一個確定的數字，不需要在今年重新用去年的所得「驗證」一次）。

**`calculatePensionTax(...)`第387-388行**：同樣的改法，退休段的住民稅一樣要讀繼承的稅額、算一次新稅額、混合——住民稅6月切換這條規則不分在職或退休，退休的人一樣要繳住民稅、一樣受這條規則約束，年末一樣要把新算出的稅額跟今年所得往下傳。

### 起算年（模擬第一年）前半段的住民稅其實有真實事實資料可用，不是靠上面的退回值硬猜
查證[YearlySimulationPipeline.java:378-392](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`applyYearEndAdjustment`）確認，這個既有方法在起算年本來就會拿`context.getYtdResidentTax()`——這是從使用者真實薪資單逐筆加總出來的「今年至今已經實際被扣繳的住民稅」（查證[SalaryContextBuilder.java:105-122,202](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)確認`ytdResidentTax`的來源），去跟「整年用上面公式算出來的理論總額」（`state.getEstimatedInhabitantTax()`）比較，算出剩餘月份該調整多少，這套比較邏輯完全不受這份計畫影響。

這代表起算年前半段（模擬當下月份之前）的住民稅，**根本不需要被模擬出來**——它是已經真實發生過的事實，記錄在使用者的薪資單裡，`applyYearEndAdjustment`本來就在用這筆事實資料去反推剩餘月份該調整的數字。這份計畫改變的只是`applyYearEndAdjustment`拿去比較的那個「整年理論總額」要怎麼被算出來（套用6月切換的公式），不需要在這份計畫裡額外設計一套「起算年前半段怎麼猜」的邏輯——上面`prevTax == null`時的退回值，只在「起算年同時也缺乏前一年房貸/所得稅記錄」這種邊界情況下才會被用到，而且因為`applyYearEndAdjustment`最終會拿YTD事實去修正，這個退回值的精確度本身不是關鍵。

**這個改動範圍侵限在住民稅這一個計算步驟內部，不影響所得稅**：所得稅本身沒有「前年/去年所得課稅」這個規則（日本所得稅是用「當年所得」直接申報,不是像住民稅這樣跨年課徵），`baseIncomeTax`/`incomeTax`這兩個變數的計算完全不受這份計畫影響,只有`inhabitantTax`這一個數字的算法改變。

**關於「怎麼從薪水逐月扣除」**：這個疑慮應該不會影響這份計畫——查證確認現有系統從頭到尾只計算「整年總住民稅」這一個數字（`estimatedInhabitantTax`），不模擬逐月扣薪水這個動作本身。這份計畫改變的是「整年總數字怎麼被算出來」，不涉及任何按月扣款的模擬，跟現有系統的顆粒度一致。

## 這個方案能順便解決什麼：房貸控除扣抵住民稅的既有缺口，現在有了正確的目標可以扣

查證[ActiveTaxCalculationEngine.java:244-247](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，房貸控除結轉住民稅的既有邏輯（`unusedMortgageDeductionForInhabitantTax`）是直接從`inhabitantTax`這個變數扣減——這份計畫改變的是`inhabitantTax`怎麼被算出來，不改變它被怎麼消費，所以這個既有的扣抵邏輯完全不需要修改，天然就會作用在這份計畫算出來的新住民稅總額上。

這也間接回應了191-04文件標注過的「退休段消費既有住民稅結轉額目前沒有對應程式碼」這個缺口（`calculatePensionTax`裡沒有對應[ActiveTaxCalculationEngine.java:244-247](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)的消費邏輯）——這份計畫如果落地，`calculatePensionTax`內部也需要產生正確的住民稅總額（上面「核心改動」），**順手把這個退休段消費缺口的目標位置準備好**，但消費邏輯本身（扣減`inhabitantTax`）仍然要另外補上，不是這份計畫自動解決的。

## 跟Plan-21的關係：這份計畫不是Plan-21的替代品，是繞過Plan-21判斷的前提（缺歷史狀態）後，直接解決需求一；需求二維持原狀留在Plan-21

Plan-21把「6月切換」（需求一）跟「現金流年中波動偵測」（需求二）歸在同一份文件裡，理由是兩者表面上都指向「模擬引擎需要月級別時間精度」。這份計畫證明需求一不需要這個前提：

- **需求一（這份計畫處理）**：6月切換本質上只是「整年固定切一刀、繼承去年已經算好的數字+每年只新算一次」的規則，不需要逐月模擬現金流。這份計畫改動侵限在`ActiveTaxCalculationEngine`內部的住民稅計算步驟,不涉及`AssetSimulationEngineImpl`主迴圈、`YearlySimulationPipeline`的年度步進結構,不需要Plan-21設想的雙引擎架構。
- **需求二（維持留在Plan-21，這份計畫不處理）**：「年中現金流一度見底又回正」這種波動,需要的是**逐月**追蹤現金餘額、逐月判斷破產風險——這跟6月切換「整年只切一刀」的性質完全不同，沒有辦法用這種輕量手法解決，確實需要Plan-21設想的月級別現金流追蹤機制。這份計畫完全不影響需求二的狀態，Plan-21維持open、等未來評估雙引擎架構。

**基礎控除、配偶特別控除、扶養親屬控除這三個Plan-21底下的子題（原Plan-21-02、question-120、question-119/121）呢？**——這三個子題描述的現象，跟這份計畫要解決的「住民稅整體金額」是同一層級的問題，但掛的是扣除額這一層，不是稅額這一層。查證[Plan-21-02](Plan-21-02-basic-deduction-prior-year-income.md)確認，這些子題描述的是「`getIncomeTaxStrategy(year)`/`getInhabitantTaxStrategy(year)`只決定法規版本、不決定用哪一年的所得去查表」——這份計畫的做法（新算一次稅額時，用的是去年的課稅所得去跑完整的住民稅計算流程），天然會讓這些扣除額子題一併被正確處理：因為`calculateInhabitantTax`前置的`totalDeductionsInhabitantTax`（包含基礎控除、配偶控除、扶養控除）本身也要跟著「用去年課稅所得的那一次計算」走去年的版本,不是只換稅率表查詢這一步。這代表這份計畫如果落地,等同一併解決了Plan-21-02/question-120/question-119/121這幾個子題描述的現象,不需要分開處理——但這些子題目前的狀態是「併入Plan-21、open」,這份計畫落地後需要回頭更新這些文件的狀態（不是刪除,是標記成「由Plan-28解決」）。

## 跟issue-192（移除191新增的三個死欄位）的關係：那三個欄位要被直接移除，不是定義累加規則後保留

191在`calculatePensionTax`裡新增的relay邏輯，曾經把`taxableIncomeForIncomeTax`/`baseIncomeTax`這兩個「所得稅版本」欄位改成跨年累加（`workingTaxable + taxableIncomeForIncomeTax`）。查證確認[issue-192](../pending%20fix/issue-192.md)後確認這兩個欄位（連同`grossSalary`）從Fixed-137誕生以來從未被真正讀取消費，191的累加只是在一個本來就沒人要的欄位上疊加了新的語意混亂——issue-192的結論是直接移除這三個欄位，不是定義一套「什麼時候該累加、什麼時候該覆寫」的規則讓它們繼續留著。這份計畫新增的`priorYearDeterminedInhabitantTax`/`priorYearTaxableIncomeForInhabitantTax`是另外兩個獨立欄位，跟issue-192要移除的那三個欄位完全無關，不受issue-192影響。

**但issue-192揭露的問題本身（退休當年在職段+退休段都發生時，兩段的數字該怎麼處理）這份計畫自己也要面對，不能迴避**：如果退休當年同時有在職段（6月以前）跟退休段（6月以後）的住民稅都要計算，這份計畫新增的`newBasisTax`/`priorYearTaxableIncomeForInhabitantTax`這兩個欄位，理論上也該讓兩段的結果合併處理後再往下傳給明年——這份計畫目前的「核心改動」一節只描述了`calculate(...)`這個方法內部怎麼改，沒有處理「退休當年在職段跟退休段都要產生這份計畫新增欄位的值時，兩者怎麼合併」這個情境，留給實作時依照191-03已經確認的「兩段結果相加、寫回一次」這個原則去設計，不是定案。

## 這份計畫沒有解決、或確定不追求的部分
- 退休當年在職段+退休段都要產生這份計畫新增欄位的值時，兩段怎麼合併——見上方「跟issue-192的關係」一節，留給實作時依191-03已確認的「兩段結果相加、寫回一次」原則設計。
- Plan-21需求二（現金流年中波動偵測）——維持留在Plan-21，見上方「跟Plan-21的關係」。
- 191-04標注的住民稅結轉額退休段消費缺口——這份計畫只是讓`inhabitantTax`這個被扣減的目標變數算得更準確，消費邏輯本身（`calculatePensionTax`內部補一段跟[ActiveTaxCalculationEngine.java:244-247](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)平行的扣減邏輯）仍然要另外處理，不在這份計畫範圍內。
- **（確定不做，不是待決定）**`calculateInhabitantTax`第二個參數（`totalIncome`，判斷均等割定額稅5000円）要不要也精確到「去年」的版本——這套系統不是稅金模擬器，用「今年」的`employmentIncome`判斷這一項即可，不追加第三個繼承欄位去換取這個精度。
- 具體的四捨五入/捨去規則（`Math.round`在這裡是否恰當，還是應該跟現有`taxableIncomeForIncomeTax`一樣捨到千位再計算）留給實作時決定。
- 要不要現在就投入實作，還是繼續留在planned狀態等待排入時程，留給使用者決定。
