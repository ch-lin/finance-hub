# [FIXED] Issue 139：房貸稅制排富閘門（question-123）只檢查了正常年度的所得稅抵扣，完全沒有檢查「確定申告次年退稅」這條支線，所得超標的使用者仍會收到全額退稅現金

## 這份issue怎麼被發現的
這是review [question-123](question-123.md)（2026年房貸稅制排富閘門）實作完成度時發現的問題。這次新增的排富檢查（所得超過2,000萬/3,000萬門檻時，房貸減稅整筆歸零）本身邏輯正確，但只接到了`ActiveTaxCalculationEngine`這一條路徑，沒有注意到房貸抵稅還有另一條完全獨立、繞過這個檢查點的支線。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:352-363`（首年入住的房貸，`isFirstEligibleYear`判斷為true時，不會走`ActiveTaxCalculationEngine`那套排富檢查，而是把全額減稅暫存進`context.setPendingNextYearTaxRefund(deduction)`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:79-81`（次年開頭，`context.setCurrentLiquidCash(context.getCurrentLiquidCash() + context.getPendingNextYearTaxRefund())`，把這筆暫存金額全額當現金加進去，整個過程完全沒有呼叫`ActiveTaxCalculationEngine`，也就不會經過這次新加的`maxTotalIncomeForMortgageDeduction`排富檢查）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:209-211`（這次question-123新加的排富檢查，只在這個方法被呼叫時生效：`if (mortgageTaxDeduction > 0 && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) { mortgageTaxDeduction = 0L; }`）

## 現狀
房貸抵稅在系統裡實際上有兩條完全獨立的路徑：
1. **正常年度（入住第二年起）**：抵稅額傳進`ActiveTaxCalculationEngine`，在這裡被用來抵扣當年所得稅/住民稅，這次question-123新加的排富檢查就是接在這一步。
2. **入住第一年**：因為日本稅法規定首年必須透過次年「確定申告」才能領到退稅（這是[Fixed-45-01](../fixed/Fixed-45-01-%20%E4%BD%8F%E5%AE%85%E3%83%AD%E3%83%BC%E3%83%B3%E6%8E%A7%E9%99%A4%E9%A6%96%E5%B9%B4%E6%99%82%E9%96%93%E5%B7%AE%E4%BF%AE%E5%BE%A9%E8%A8%88%E7%95%AB.md)實作的既有機制），這年的抵稅額完全不走`ActiveTaxCalculationEngine`，是直接暫存起來、隔年當現金發放。

這次question-123新加的排富檢查，只接在路徑1，完全沒有碰路徑2。

## 為何是問題
如果使用者的所得剛好超過排富門檻（2026年起2,000萬円，舊制3,000萬円），依法這整筆房貸抵稅應該完全歸零，不管是透過年末調整還是確定申告領到都不該有這筆錢。但系統目前的實作，只擋住了「入住第二年起」這條路徑；「入住第一年」這條路徑完全沒有被這次新加的檢查觸碰到，使用者在入住第一年所得超標的情況下，次年依然會收到系統算出的全額退稅現金，等於這筆依法不該存在的退稅，透過確定申告這條支線整個繞過了排富閘門。

## 具體錯誤情境
使用者年薪2,200萬円（超過2,000萬円門檻），今年剛買房入住，房貸抵稅算出21萬円。因為是入住第一年，這筆21萬円不會在今年被排富檢查擋下（因為根本沒有經過`ActiveTaxCalculationEngine`這一步），而是被存進`pendingNextYearTaxRefund`；次年開頭，系統會把這筆21萬円當現金全額加進使用者的流動資產——即使這個人的所得早就超過排富門檻，依法這筆減稅本來就不該存在。

## 這個問題的觸發情境並不罕見
這個工具的核心客群本來就是年薪2,000萬~3,500萬這個區間的使用者（question-123文件自己這麼定義），而「房貸入住第一年」是任何新簽約購屋案例都必經的情境——這兩個條件疊加的機率不低，不是邊緣案例。

## 建議修正
在`MortgageCalculator.java`判斷`isFirstEligibleYear`、要把`deduction`存進`pendingNextYearTaxRefund`之前，應該先套用跟`ActiveTaxCalculationEngine`裡一樣的排富判斷（所得超過`mortgageResult.maxTotalIncomeForMortgageDeduction()`就直接把這筆`deduction`歸零，不要存進`pendingNextYearTaxRefund`）。這裡需要注意：`MortgageCalculator`這一步在計算當下，可能還不知道這一年最終的`employmentIncome`是多少（這點跟issue-137討論的「首年遞延機制沒有正確聯動所得稅實繳上限」是同一類「兩個計算引擎之間的呼叫順序」問題），如果要正確解決，可能需要跟issue-137一併評估`MortgageCalculator`跟`ActiveTaxCalculationEngine`之間的資料傳遞方式，不是這裡單獨補一個判斷就能完整解決。

---

## 實作完成報告（Resolved via Issue 137）

### 1. 處理結論
**本 Issue 所指出的首年確定申告支線排富穿透漏洞，已於 [Issue 137](issue-137.md) 之「純金額雙通道架構」與「跨年狀態機」重構中一併徹底根治並驗證通過。**

---

### 2. 根治機制作業對照

| 環節 | 舊機制漏洞（Fixed-45-01 Hack） | 新機制修復（Issue 137 純金額通道架構） |
|---|---|---|
| **首年額度流向** | 在 `MortgageCalculator` 粗暴攔截存入 `pendingNextYearTaxRefund` 並提前歸零，**完全繞過稅務引擎** | 徹底移除越權暫存；理論額度純函數分流至不可變 `kakuteiDeduction` 通道交給稅務引擎 |
| **排富閘門查核** | 僅檢查年末調整路徑（`mortgageTaxDeduction`），確定申告支線完全無檢查點 | 稅務引擎同時審查雙通道：`(yearEndDeduction > 0 \|\| kakuteiDeduction > 0)`，超標時**雙通道當場全數歸零** |
| **次年退稅發放** | `YearlySimulationPipeline` 無條件將暫存額全額發放為現金 | 封裝進 `PreviousYearTaxRecord` 的退稅與轉扣全數為 `0L`，次年年初管線自然發放 `0L`，絕不多發一毛錢 |

---

### 3. 關鍵代碼落實點

1. **[MortgageCalculator.java:L353-L365](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L353-L365)**：
   - 徹底移除 `context.setPendingNextYearTaxRefund` 與 `deduction = 0L`。
   - 首年理論抵稅額純粹歸入 `MortgageYearlyResult.kakuteiDeduction`。
2. **[ActiveTaxCalculationEngine.java:L222-L226](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L222-L226)**：
   - 所得超標時雙通道即時歸零：
     ```java
     if ((yearEndDeduction > 0 || kakuteiDeduction > 0)
             && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
         yearEndDeduction = 0L;
         kakuteiDeduction = 0L;
     }
     ```
3. **[ActiveTaxCalculationEngine.java:L256-L262](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L256-L262)**：
   - 因 `kakuteiDeduction == 0L`，算出的 `actualKakuteiRefund` 與 `unusedForInhabitantTax` 自然為 `0L`，封裝進 `PreviousYearTaxRecord`。
4. **[YearlySimulationPipeline.java:L80](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L80)**：
   - 次年開頭讀取 `pendingKakuteiShinkokuRefund()` 為 `0L`，現金發放為 0，次年夏季住民稅抵扣為 0。

---

### 4. 測試驗收結果

- **單元測試**：[`ActiveTaxCalculationEngineTest.testCalculateTaxes_firstYear_highIncomePhaseOut_issue139`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java#L733-L768)
  - 驗證年薪 2,200 萬（超標 2,000 萬門檻）首年購屋時，封裝進 `PreviousYearTaxRecord` 的退稅與轉扣額嚴格為 `0L`。
- **跨年端到端整合測試**：[`YearlySimulationPipelineTest.testScenarioC_highIncomeDisqualification_phaseOut`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java#L302-L339)
  - 驗證年薪 2,500 萬超標者首年購屋，次年春季現金退稅增加嚴格為 `0L`（現金變動嚴格等於純儲蓄 `netSavings`）。
- **專案全測**：全專案編譯通過，779 項測試全數維持綠燈（`BUILD SUCCESS`）。
