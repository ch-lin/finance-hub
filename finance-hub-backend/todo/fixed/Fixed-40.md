# Fixed-40：ESPP（持株会）薪資扣款計算有兩個獨立問題——①已發生的 YTD 扣款被誤加回 netIncome 造成灌水（跟未來是否持續提繳無關）；②「未來剩餘月份」的 ESPP 完全是用歷史平均值單方面外推、假設會持續發生，這個假設本身不成立

> 問題①（YTD 滲漏）跟 [Fixed-41.md](Fixed-41.md) 描述的 DC Matching YTD 滲漏是同一個「基準沒對齊」問題，Fixed-41 已經把修法抽成共用函式 `computeYtdNetIncomeBaseline`（`YearlySimulationPipeline.java`），套用後 ESPP 會自動一起被修好，不需要為 ESPP 另外新增欄位。詳細推導請直接看 Fixed-41.md，這裡不重複。問題②（未來 ESPP 不外推）跟 Fixed-41 無關，維持獨立修正。

## 相關檔案與 Package 對照表 (Files Involved)

為了避免每次都要重新 `find`，這裡先列出本 issue 會提到、或修正時會動到的**全部檔案**，含完整 package 名稱與相對路徑：

| Class | Package（完整） | 檔案路徑（相對於 `finance-hub-backend/`） |
|---|---|---|
| `ActiveTaxCalculationEngine` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java` |
| `YearEndAdjustmentEngine` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/YearEndAdjustmentEngine.java` |
| `YearlySimulationPipeline` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` |
| `SalaryContextBuilder` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java` |
| `SimulationContextFactory` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java` |
| `SimulationContext`（欄位定義的地方） | `ch.lin.finance.hub.backend.api.app.service.simulation.core.model` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java` |
| `SimulationFund`（iDeCo/DC 對照用） | `ch.lin.finance.hub.backend.api.app.service.simulation.core.model` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java` |
| `TaxAdvantagedAccountProjector`（iDeCo/DC 對照用） | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` |
| `CashflowLiquidationCalculator` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/CashflowLiquidationCalculator.java` |
| `SalaryRecord`（`netAmount`/`esppContribution` 欄位定義） | `ch.lin.finance.hub.backend.api.domain.model.accounting.salary` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java` |

**這次修正實際要改的檔案**：`SimulationContext.java`（刪除死欄位，不新增任何欄位）、`SalaryContextBuilder.java`（刪除死程式碼，不新增任何欄位或計算）、`YearlySimulationPipeline.java`（移除錯誤加法；YTD 基準對齊的修法已經合併進 [Fixed-41.md](Fixed-41.md) 的共用函式，這裡不重複）。其餘檔案是理解問題脈絡跟對照 iDeCo/DC 邏輯用的，不需要修改。

## 現況說明 (Current State)

`ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine#calculate`（`ActiveTaxCalculationEngine.java` 第 198 行）計算 `netIncome` 的公式：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.tax
// class ActiveTaxCalculationEngine，method calculate，第 198 行
long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;
```

這個公式從頭到尾都沒有扣除 ESPP（持株会）薪資扣款——也就是說，這個 `netIncome` 對「全年」（含已發生的月份跟還沒發生的月份）來說，都是「假設使用者完全沒有參加持株会」情境下的到手金額。

接著，`ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline.YearlySimulationPipeline#applyYearEndAdjustment`（`YearlySimulationPipeline.java` 第 244-246 行，僅在模擬第一年 `age == initialAge` 執行）：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyYearEndAdjustment，第 244-246 行
if (context.getProjectedAnnualEsppThisYear() != null) {
    state.setNetIncome(state.getNetIncome() + context.getProjectedAnnualEsppThisYear());
}
```

`projectedAnnualEsppThisYear` 是 `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SalaryContextBuilder#initializeSalaryContext`（`SalaryContextBuilder.java` 第 181-198 行）算出來的，程式碼如下：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.factory
// class SalaryContextBuilder，method initializeSalaryContext，第 181-198 行
int salaryCountThisYear = 0;
long totalEsppThisYear = 0L;
for (SalaryRecord sr : salaryRecords) {
    if (sr.getPaymentDate() != null && sr.getPaymentDate().getYear() == currentYear) {
        salaryCountThisYear++;
        Long esppAmount = sr.getEsppContribution();
        totalEsppThisYear += (esppAmount != null ? esppAmount : 0L);
    }
}
if (salaryCountThisYear > 0) {
    long avgMonthlyEspp = totalEsppThisYear / salaryCountThisYear;
    int currentMonth = YearMonth.now().getMonthValue();
    int remainingMonthsToPay = 12 - currentMonth + 1;
    context.setProjectedAnnualEsppThisYear(totalEsppThisYear + (avgMonthlyEspp * remainingMonthsToPay));
}
```

這個欄位（宣告於 `ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext` 第 87 行：`private Long projectedAnnualEsppThisYear;`）是「**已發生的 YTD 真實 ESPP 扣款金額（`totalEsppThisYear`，事實，不是預測）** + **剩餘月份用『今年已發生月份的平均值』外推出來的金額（`avgMonthlyEspp * remainingMonthsToPay`，這是預測，而且是完全沒有根據使用者當下設定、純粹拿歷史平均值硬推的預測）**」兩者相加的結果，兩者被合併成一個數字，在 `YearlySimulationPipeline.java` 第 244-246 行整個被加回 `netIncome`。

（額外確認：全專案 `grep -rn "ProjectedAnnualEspp"` 只命中這 3 個地方——`SimulationContext.java:87` 的欄位宣告、`SalaryContextBuilder.java:195,197` 的寫入、`YearlySimulationPipeline.java:244-245` 的讀取。沒有任何 DTO／前端在用這個欄位，代表這個欄位可以連同下面的外推邏輯一起整段移除，不用擔心破壞其他呼叫端。）

## 問題原因 (Cause)

這裡其實是兩個性質不同、要分開回答的問題，不能混為一談：

### ① 已發生的 YTD 部分——這是確定的事實，跟「未來會不會繼續提繳 ESPP」完全無關，屬於單純的計算 bug

`context.getYtdNetAmount()`（`SimulationContext.java` 第 58 行欄位，由 `SalaryContextBuilder.java` 第 80-81 行從真實薪資單加總寫入）本來就已經反映了「已經發生的月份，ESPP 已經真的從薪資裡被扣走」這個事實——這不是預測，是已經發生、無法改變的歷史資料（`SalaryRecord.netAmount` 對應薪資單上的「差引支給額」，見 `hub-domain` 模組 `SalaryRecord.java` 的欄位定義）。而 `netIncome`（全年理論值）的公式（`ActiveTaxCalculationEngine.java` 第 198 行）從頭就沒有 ESPP 這一項，所以它对「已發生的月份」而言，也是「假設沒有 ESPP」的金額，比真實到手金額多算了一份「已發生月份的 ESPP」。

用計劃書慣用的方式驗算：假設全年理論 `netIncome`（未扣 ESPP，未年末調整）為 4,500,000，模擬時點為 6 月底，YTD 已發生的真實 ESPP 扣款（`totalEsppThisYear`）為 120,000，YTD 真實到手淨額 `ytdNetAmount` = 2,130,000（此數字因為來自真實薪資單，已經是扣除了這 120,000 ESPP 之後的金額）：

- **移除 244-246 行的錯誤加法之後**（只解決「疊加的顯性錯誤」，不解決底層問題）：
  `remainingNetIncome = netIncome − ytdNetAmount = 4,500,000 − 2,130,000 = 2,370,000`
  但「剩餘半年」在完全不假設任何未來 ESPP 的前提下，正確值應該是 `4,500,000 ÷ 2 = 2,250,000`（單純剩餘半年的理論到手金額，跟 ESPP 未來會不會發生無關）。
  **多算了 120,000——剛好是 YTD 已發生的 ESPP 金額**，這筆錢已經真的離開薪資帳戶、被真實扣走了，但因為 `netIncome` 從頭就沒扣過它，相減時就會原封不動地滲漏回 `remainingNetIncome`，直接灌水進 `currentLiquidCash`（`YearlySimulationPipeline.java` 第 280-287 行 `applyCashflowAndSettlement`）。

  這跟 [Fixed-41.md](Fixed-41.md) 描述的 DC マッチング拠出／iDeCo「YTD 滲漏」是完全同一種機制，只是這裡的滲漏對象是 ESPP，發生的檔案也是同一個 `YearlySimulationPipeline.java`。

  **為什麼「單純減法基準不一致」會直接等於漏洞金額，而不是巧合**：`remainingNetIncome = fullYearNetIncome(無ESPP基準,全年) − ytdNetIncome(有ESPP基準,YTD)`。把全年拆成「YTD 段」+「剩餘段」：`fullYearNetIncome = fullYearNetIncome_YTD段(無ESPP) + fullYearNetIncome_剩餘段(無ESPP)`。代入後：`remainingNetIncome = fullYearNetIncome_剩餘段(無ESPP) + [fullYearNetIncome_YTD段(無ESPP) − ytdNetIncome(有ESPP)] = fullYearNetIncome_剩餘段(無ESPP) + YTD期間的ESPP金額`。這證明了只要兩個運算元的「ESPP 是否已扣」基準不一致，差額必定精確等於 YTD 期間的 ESPP 金額，不會多也不會少——所以修法必須是「把兩個運算元的基準對齊」，而不是事後隨便扣一筆估計值。

- **在目前的程式碼（244-246 行的加法還沒被移除）之下**：加法本身額外又把 `projectedAnnualEsppThisYear`（=YTD 的 120,000 + 剩餘半年外推的 120,000 = 240,000）整筆加回去，等於在上面「已經多算 120,000」的基礎上，再多加一份 240,000，總計多算了 `120,000 + 240,000 = 360,000`。這是目前程式碼實際發生的灌水金額。

**結論：無論未來的 ESPP 要不要假設持續，YTD 已發生部分的重複計算都是一個確定要修的 bug——這部分用的是既成事實（真實薪資單資料），不是預測，跟你提到的「無法保證未來一定會/不會扣」完全不衝突。**

### ② 剩餘月份（未來）的部分——這確實是預測假設的問題，你的質疑是對的

目前 `avgMonthlyEspp * remainingMonthsToPay` 這段外推邏輯（`SalaryContextBuilder.java` 第 191-195 行），跟 iDeCo/DC 的「剩餘月份提繳金額」（`ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector` 用的是 `ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund#getMonthlyContribution()`）性質完全不同：iDeCo/DC 的剩餘月份金額，來自使用者**目前明確設定**的提繳金額（`SimulationFund.monthlyContribution` 是使用者在系統裡實際填寫的欄位，使用者隨時可以把它改成 0），所以拿它往後外推是合理的——反映的是「使用者現在打算怎麼做」這個有資料根據的意願。

但 ESPP 完全沒有對應的「目前 ESPP 提繳設定」這種欄位／資料結構（`SimulationContext`／`SimulationFund` 體系裡找不到任何 ESPP 版本的提繳設定欄位，已用 `grep -rn "espp" SimulationContext.java SimulationFund.java` 確認為零命中），`avgMonthlyEspp` 純粹是拿「今年已經發生的月份」的歷史平均值硬推剩餘月份，完全不知道使用者現在的真實狀態。就像實際情境：如果使用者今年年中把 ESPP 停掉、但之前幾個月仍有扣款紀錄，這個歷史平均值仍然會是正數，剩餘月份還是會被錯誤地假設「會繼續有 ESPP 扣款」——這個假設沒有依據，也不尊重使用者實際上已經做的選擇。

**為什麼不能「先求有再求好」、暫時保留這段外推邏輯**：這不是精確度不夠的問題，是這段程式碼在斷言一件系統完全沒有資料支持的事（使用者未來會不會提繳 ESPP）。跟 iDeCo/DC 不同，這裡沒有使用者可以修改的開關可以讓這個假設變成「使用者的選擇」——所以只要使用者現況跟過去平均不同（提高、降低、停止），這段程式碼就會錯，而且使用者完全沒有辦法透過系統上任何設定去糾正它。這正是本次 review 任務書要求的「比照實際人生現金水位去做準確推算」原則下必須排除的假設，不是可以留待「以後優化」的次要瑕疵。

### 附帶更正：公司股票資產（`companyStockBalance`）並非完全沒有對應處理

原文誤稱 `context.getCompanyStockBalance()` 在模擬中「從未被寫入或累加」，這不準確。它會在模擬起點被 `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SimulationContextFactory`（`SimulationContextFactory.java` 第 102-106 行）正確初始化成真實 E*Trade 帳戶餘額（反映「過去已經買進」的持股價值），也會在現金不足時透過 `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator` 正確減損。真正缺的只是「模擬期間**新增**的 ESPP 提繳」沒有被累加進這筆資產——而根據上面②的結論，既然「未來是否會繼續提繳 ESPP」本身就不該被假設，這筆「新增累加」現階段也不需要存在，等以後系統真的加上使用者可設定的 ESPP 提繳欄位時，才需要一併處理「扣現金、加股票資產」的對稱邏輯。

## 建議修正方向 (Suggested Fix)

1. **無條件移除 `YearlySimulationPipeline.java` 第 244-246 行的加法。**
   **為什麼**：這行程式碼的前提是「`state.getNetIncome()` 在某處已經扣過 ESPP，所以這裡要加回來補償」。但全專案 grep 確認 `netIncome` 的唯一計算來源 `ActiveTaxCalculationEngine.calculate`（第 198 行公式）從來沒有 ESPP 這一項，年末調整引擎 `YearEndAdjustmentEngine` 也只處理所得税／住民税／社會保險的 YTD 對齊，同樣不碰 ESPP。也就是說，這行加法要「補償」的那個扣除動作，在程式裡根本不存在——它不是修正一個被多扣的數字，而是憑空灌入一筆全新的現金。這跟第 2 點「基準對齊」是完全不同性質的問題：第 2 點是要讓兩個數字的計算基準一致，這裡則是刪除一個沒有配對扣除動作的孤立加法，兩者互不依賴，可以獨立執行、風險互不影響。

2. **修正 YTD 已發生 ESPP 的滲漏：直接套用 [Fixed-41.md](Fixed-41.md) 的共用函式 `computeYtdNetIncomeBaseline`，不在這裡另外實作。**
   Fixed-41 已經證明：用 `ytdGrossIncome - ytdIncomeTax - ytdResidentTax - ytdHealthInsurance - ytdPension - ytdEmploymentInsurance` 這個殘值公式重建的 YTD 基準，跟 `fullYearNetIncome` 的計算基準完全對齊，而且不管實際被扣走的雜項是 ESPP、DC Matching、或任何其他項目都會自動一起還原，不需要為 ESPP 單獨新增 `ytdEsppContribution` 這種專屬欄位（那是本 Issue 原本的提案，現已撤回）。詳細代數證明與設計理由請見 Fixed-41.md，這裡不重複。

3. **剩餘月份（未來）的 ESPP：不要假設會持續發生**，因此不需要在 `remainingCashThisYear` 額外扣一筆「剩餘 ESPP」，也不需要為此把假設的未來金額累加進 `companyStockBalance`。
   **為什麼**：跟 iDeCo/DC 不同，ESPP 在系統裡沒有任何使用者可設定的「目前提繳金額」欄位可以作為外推依據（`SimulationFund.monthlyContribution` 是 iDeCo/DC 專用，沒有 ESPP 對應版本）。用歷史平均值取代使用者當下設定，等於用「過去的行為」冒充「使用者現在的意願」，一旦使用者中途調整或停止提繳，這個假設就會系統性地錯，而且使用者無從在介面上糾正它。更重要的是：完成第 1、2 點之後，`fullYearNetIncome` 對剩餘月份本來就已經是「未扣 ESPP」基準了，所以「不外推」不需要額外寫任何程式碼去讓剩餘月份「維持未扣 ESPP」——這是移除錯誤加法、修好 YTD 基準之後的自然結果，不是一個要另外實作的分支。等系統之後真的新增使用者可自行設定的「目前 ESPP 提繳金額」欄位（類似 iDeCo/DC 的 `SimulationFund`）之後，才適合套用跟 iDeCo/DC 一致的「用當下設定值外推剩餘月份、同時扣現金／加股票資產」邏輯，不在本次修正範圍內。

4. **清理死碼**：`SimulationContext.java` 第 87 行的 `projectedAnnualEsppThisYear` 欄位、以及 `SalaryContextBuilder.java` 第 181-198 行整段（`salaryCountThisYear`／`totalEsppThisYear`／`avgMonthlyEspp`／`remainingMonthsToPay` 外推運算），在完成上面 1-2 點之後不會再被任何地方使用，直接整段刪除，**不需要任何替代欄位或替代寫入**——第 2 點已經改用 Fixed-41 的共用函式，該函式只讀取既有的 `ytdGrossIncome`/`ytdIncomeTax`/... 等欄位，跟 ESPP 完全無關，不需要幫 ESPP 另外維護任何 YTD 欄位。
   **為什麼**：`projectedAnnualEsppThisYear` 目前唯一的讀取端就是要刪除的第 244-246 行；一旦刪除讀取端卻保留寫入端，會留下一個「算出來但沒人用」的欄位與計算邏輯，下次有人（人類或 Gemini）讀到這段程式碼時，會誤以為這個外推值仍然在系統裡發揮作用，反而增加理解成本——這正是這次 review 任務書要求排除的「死碼」在成因上的相反案例：先讓它變成死碼再留著，比一開始就沒有更容易誤導人。

## 最小範例程式碼 (Sample Code)

**檔案 1／`SimulationContext.java`**（package: `ch.lin.finance.hub.backend.api.app.service.simulation.core.model`，路徑: `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java`）——直接刪除第 87 行，不新增任何欄位（Fixed-41 的共用函式不需要 ESPP 專屬欄位）：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.model
// class SimulationContext（Lombok @Data，getter/setter 自動產生）

// 刪除（原第 87 行），無替代欄位：
// private Long projectedAnnualEsppThisYear;
```

**檔案 2／`SalaryContextBuilder.java`**（package: `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory`，路徑: `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java`）——`initializeSalaryContext` method，原第 181-198 行整段直接刪除，無替代程式碼：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.factory
// class SalaryContextBuilder，method initializeSalaryContext

// 刪除原第 181-198 行整段（salaryCountThisYear／totalEsppThisYear／avgMonthlyEspp／remainingMonthsToPay／
// setProjectedAnnualEsppThisYear 呼叫），無替代寫入。Fixed-41.md 的 computeYtdNetIncomeBaseline 只讀取
// ytdGrossIncome/ytdIncomeTax/ytdResidentTax/ytdHealthInsurance/ytdPension/ytdEmploymentInsurance
// 這幾個既有欄位（本檔案別處已經在維護），跟 ESPP 無關，不需要在這裡幫 ESPP 另外算任何東西。
```

**檔案 3／`YearlySimulationPipeline.java`**（package: `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline`，路徑: `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`）——`applyYearEndAdjustment` method，原第 240-247 行：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyYearEndAdjustment

if (age == initialAge) {
    state.setEstimatedIncomeTax(adj.remainingIncomeTax());
    state.setEstimatedInhabitantTax(adj.remainingInhabitantTax());
    state.setEstimatedSocialInsurance(adj.remainingSocialInsurance());

    // 移除：不應該把 ESPP 加回 netIncome，netIncome 從未扣過 ESPP，沒有對應的扣除動作可以「補回」
    // if (context.getProjectedAnnualEsppThisYear() != null) {
    //     state.setNetIncome(state.getNetIncome() + context.getProjectedAnnualEsppThisYear());
    // }
}
```

同一個檔案的 `applyCashflowAndSettlement` method——**不在這裡另外修改，直接沿用 [Fixed-41.md](Fixed-41.md) 最小範例程式碼裡的 `computeYtdNetIncomeBaseline` 函式與呼叫方式即可**，該函式已經同時涵蓋 ESPP 跟 DC Matching 的 YTD 基準對齊，不需要在本 Issue 重複貼一份幾乎一樣的程式碼。完成第 1、2、4 點之後，剩餘月份自然不含任何 ESPP 假設，也不需要額外程式碼去維持「不外推」——這是移除死碼之後的自然結果。
