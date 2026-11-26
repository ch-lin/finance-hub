# Fixed-45：住宅ローン控除完全沒有模擬「首年須透過確定申告才能請領、次年起才由公司年末調整代辦」的請領時間差

## 現況說明 (Current State)

[MortgageCalculator](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 判斷某筆貸款當年度是否適用住宅ローン控除，只看兩個條件：`Boolean.TRUE.equals(mortgage.getTaxDeductionEligible())` 與是否還在 `endYear` 控除期限內。只要這兩個條件成立，該年度就會直接算出並發放全額控除金額，沒有任何額外的「這是不是使用者入住/開始適用控除的第一年」的區分邏輯。

## 問題原因 (Cause)

日本住宅ローン控除制度規定：**第一年**的控除必須由使用者本人在次年進行**確定申告**才能請領（因為新申請年度的資格認定資料，公司薪資部門無法在當年年末調整時直接處理），從**第二年**開始才能透過雇主的年末調整（年末調整）代為處理，當年隨薪資直接反映在稅金上。

也就是說，即使使用者符合控除資格，**第一年的控除款不會在當年的薪資/年末調整現金流中出現**，而是要等到次年報稅後才實際入帳（退稅或減少次年繳納稅款）。目前程式碼對所有符合資格的年度（包含第一年）都用同一套邏輯，直接把控除金額計入當年度抵稅，沒有對「首年」做時間點延遲處理。這會讓首年當年度的模擬現金水位被高估（多算了一筆其實要等到次年才會真正入帳的稅金優惠），次年的現金水位則相應被低估。

與 [Fixed-44.md](Fixed-44.md) 同屬住宅ローン控除計算範疇，但問題點不同：Fixed-44 討論的是「退休當年提前清償時的控除金額計算錯誤」，本 Issue 討論的是「首年請領時間點本身沒有被模擬」，兩者可以獨立修正。

**與年末調整（applyYearEndAdjustment）顯示負值的連動**：本 Issue 描述的「無條件套用房貸控除」問題，還有一個容易被忽略的下游放大效果。[ActiveTaxCalculationEngine.calculate](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)（第 178-195 行附近）在算「全年理論應繳稅額」時，會無條件把 `mortgageResult.mortgageTaxDeductionThisYear()` 全額扣掉，不管這是不是該筆貸款控除資格的第一年。這個「全年理論應繳稅額」接著會被 [YearlySimulationPipeline.applyYearEndAdjustment](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（第 225-248 行）拿去跟使用者真實薪資單累積的 YTD 已繳稅額比較，透過 [YearEndAdjustmentEngine.calculateYearEndAdjustment](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/YearEndAdjustmentEngine.java)（第 64-73 行）算出「剩餘期間應繳／退稅」金額。

如果使用者今年剛好是該筆房貸控除資格的第一年，`trueAnnualIncomeTax` 就已經多扣了一筆現實中今年根本不會透過年末調整入帳的房貸控除（依本 Issue 描述，首年須靠確定申告，次年才拿到錢），導致算出來的「剩餘期間應繳稅額」比真實情況更負——模擬會顯示今年公司年末調整退更多錢，但現實中這筆房貸控除的退稅其實要等到次年確定申告才會發生。

這不代表年末調整的比較機制本身有錯：YTD 實際預扣本來就會因為生命保險費、iDeCo/DC 提繳等扣除額只在年末調整才反映，而系統性偏高，讓比較結果偏負是預期內、方向正確的現象，不需要因此調整或刪除 `applyYearEndAdjustment` 裡的計算邏輯。這裡單純是本 Issue 描述的「房貸控除首年時間差沒被模擬」連動放大了負值的幅度而已——修好本 Issue 之後，這部分的放大效果會自然消失。

## 建議修正方向 (Suggested Fix)

需要對每筆貸款額外記錄或推算「這是否是該貸款控除資格的第一個適用年度」。若是第一年，該年度不應把控除金額計入當年度現金流／抵稅，而應該挪到次年度（以確定申告退稅／抵稅的形式）反映；第二年起才回到現行「當年度年末調整直接抵稅」的邏輯。

## 最小範例程式碼 (Sample Code)

```java
// MortgageCalculator：判斷是否為控除資格第一年，決定控除金額入帳的年度
boolean isFirstEligibleYear = (year == mortgage.getTaxDeductionStartYear());

if (isFirstEligibleYear) {
    // 首年控除須透過次年確定申告才能請領，本年度不計入抵稅
    pendingNextYearTaxRefund += mortgageTaxDeduction; // 新增：挪到次年度以確定申告形式入帳
    mortgageTaxDeduction = 0L;
}
// 從第二年起維持現行邏輯：mortgageTaxDeduction 直接於當年度年末調整抵稅
```

## 修正報告 (Resolution Report)

我們已經根據建議的方向完成了架構調整，精準還原了日本稅法中「住宅ローン控除首年須透過確定申告辦理」的時間差。

### 1. 擴充 `SimulationContext`
在 `SimulationContext.java` 中新增了 `pendingNextYearTaxRefund` 欄位。這個欄位專門用來在跨年份的迴圈中，記住「去年已經算出、但今年才要以現金退稅發放」的退稅金額。

### 2. 強化 `MortgageCalculator` 的發放邏輯
現在 `MortgageCalculator` 在計算抵稅額時，會透過檢查房貸的 `occupancyDate`（入住日）來自動判斷目前是否為「適用抵稅的第一年」。
- 如果是第一年：系統會將算出的抵稅額全部存入 `pendingNextYearTaxRefund`，並將該年度要用來折抵所得稅的額度強制作為 `0L`，確保不會在第一年的「年末調整」中被提前扣減。
- 如果是第二年（含）以後：則維持正常邏輯，讓該筆抵稅額直接折抵當年度的所得稅。

### 3. 處理跨年度退稅 (`YearlySimulationPipeline`)
在每年模擬一開始處理現金流時，系統會先檢查有沒有去年遺留的確定申告退稅款 (`pendingNextYearTaxRefund`)。
如果餘額大於零，這筆錢會直接加進使用者的活期存款 (`currentLiquidCash`) 中，完美模擬使用者在隔年春季收到國稅局退款的真實情況。

### 4. 驗證與結果
我們新增了專屬的單元測試 (`MortgageCalculatorTest.java`)，針對首年遞延 (Deferral) 與次年正常發放的邏輯進行了嚴格的驗證。此修正不僅讓現金流曲線更加真實，也徹底解決了第一年因為提前折抵導致「年末調整」退稅數字異常膨脹的下游效應。
