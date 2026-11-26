# Fixed-41：DC マッチング拠出過渡年已發生月份的扣款金額滲漏回現金——修正方向改為「用已知薪資單欄位反推殘值」，不試圖單獨估算 DC Matching 金額；iDeCo／NISA 確認排除在外

> 本文為修正版。原版把 iDeCo 跟 DC Matching 當成同一種東西處理（都用「YTD 已提繳金額回補」的方式修），這是錯的——iDeCo 是使用者自己另外跟證券商簽約、直接從銀行帳戶扣款，從頭到尾不會經過薪資單，`netAmount` 從來沒有扣過 iDeCo，沒有「基準不一致」這回事，不需要修。這個結論的推導過程也連動影響了 Fixed-42，請對照閱讀。

## 相關檔案與 Package 對照表 (Files Involved)

| Class | Package | 檔案路徑 | 是否需要修改 |
|---|---|---|---|
| `YearlySimulationPipeline` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` | **是**（唯一需要改的地方） |
| `ActiveTaxCalculationEngine` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java` | 否，僅供對照 `netIncome`/`socialInsurance` 的計算基準 |
| `TaxAdvantagedAccountProjector` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` | 否，僅供對照（DC 企業拠出/員工 Matching 的拆分已經是正確的，見下方說明） |
| `SalaryContextBuilder` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java` | 否，`ytdGrossIncome`／`ytdIncomeTax`／`ytdResidentTax`／`ytdHealthInsurance`／`ytdPension`／`ytdEmploymentInsurance` 已經存在，不需要新增欄位 |
| `SimulationContext` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.model` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java` | 否 |
| `InvestmentContributions` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.model` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java` | 否 |
| `SalaryRecord` | `ch.lin.finance.hub.backend.api.domain.model.accounting.salary` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java` | 否，僅供對照 `netAmount`／`details` 的欄位結構 |
| `BankTransaction` | `ch.lin.finance.hub.backend.api.domain.model.accounting.bank` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankTransaction.java` | 否，僅供對照「為什麼 iDeCo/NISA 不需要修」 |

**只需要改 `YearlySimulationPipeline.java` 一個檔案，一段程式碼。**

## 現況說明 (Current State)

`YearlySimulationPipeline.applyCashflowAndSettlement`（`hub-app/.../pipeline/YearlySimulationPipeline.java`，第 280-291 行附近）在過渡年（模擬當年）做這件事：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyCashflowAndSettlement，第 280-291 行（修正前）
long fullYearNetIncome = state.getNetIncome();      // 來自 ActiveTaxCalculationEngine，從未扣過 DC Matching
long ytdNetIncome = context.getYtdNetAmount();       // 來自真實薪資單加總，已經扣過 DC Matching
long remainingNetIncome = fullYearNetIncome - ytdNetIncome;
long netSavings = remainingNetIncome - state.getAnnualExpenses();
long remainingCashThisYear = netSavings - contributions.getTotalEmployeeInvestments() - ...;
```

`ActiveTaxCalculationEngine.calculate`（`hub-app/.../tax/ActiveTaxCalculationEngine.java` 第 198 行）：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.tax
// class ActiveTaxCalculationEngine，method calculate，第 198 行
long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;
```

DC Matching（企業型DC マッチング拠出，員工自己加碼提繳的部分）從薪水裡面直接扣，一定會反映在真實薪資單的「差引支給額」（`netAmount`）這個總額上——不需要薪資單解析器有專屬欄位去命名它，因為 `netAmount` 是薪資單上印出來的最終結算數字，不是程式再去把各項具名扣除額加總算出來的。但 `netIncome` 的公式（上面）從未扣過 DC Matching，只在 89-91 行把它算進 `retirementDeduction`（稅務扣除額）裡。這一點跟 [Fixed-40.md](Fixed-40.md) 的 ESPP 同源。

## 問題原因 (Cause)

`fullYearNetIncome`（理論值，未扣 DC Matching）跟 `ytdNetIncome = context.getYtdNetAmount()`（真實值，已扣 DC Matching）兩者基準不一致，相減時：

```
remainingNetIncome = fullYearNetIncome - ytdNetIncome
                    ≈ 剩餘月份的理論淨額 + YTD 已扣的 DC Matching 提繳金額
```

已經真實離開薪資帳戶的 DC Matching 提繳金額，會原封不動「滲漏」回 `remainingNetIncome`，最終灌水進 `currentLiquidCash`。

### 為什麼不能用「YTD 已提繳金額」這種具名欄位去回補（跟原版方向不同的地方）

原本的直覺修法是照 Fixed-40 的模式，新增一個 `context.getYtdDcMatchingContribution()`，把它加回 `ytdNetIncome`。但這個欄位要怎麼算出來？薪資單解析器（`SalaryParserImpl.mapCoreField`）目前沒有 DC Matching 的具名對應欄位，實際金額只會落入沒人讀取的 `details` map；就算要另外解析，也只能拿到「現在快照的月繳金額」，沒有任何逐月歷史記錄可以下載（跟 iDeCo 是同一個資料源限制，SBI/Sompo 網站都只有快照，沒有扣款歷史）。要湊出「YTD 已提繳金額」勢必要用「目前月繳金額 × 已經過去的月數」去外推——但這個外推假設「從某個時間點開始，金額一直沒變、沒有中斷」，而使用者可能到職 3 月才有薪資單、6 月才開始加入 DC Matching、11 月又轉換成 iDeCo（DC Matching 跟 iDeCo 互斥，同一時間只會存在一種），這種外推完全無法驗證，一旦猜錯方向就會產生「免費現金大放送」——比現在這個滲漏 bug 本身更危險。詳細的資料限制討論見 [Fixed-42.md](Fixed-42.md)。

### 真正的修法：不用去猜 DC Matching 花了多少，用殘值反推就好

**核心洞察**：我們並不需要知道「DC Matching 具體扣了多少」，只需要知道「YTD 這段期間，扣掉稅金跟社保之後，理論上（跟 `fullYearNetIncome` 用同一套基準）應該還剩多少」——這個數字可以直接用已經真實存在、逐月加總好的欄位算出來，不需要任何新欄位、不需要任何外推假設：

```
ytdNetIncome(對齊基準後) = ytdGrossIncome - ytdIncomeTax - ytdResidentTax
                          - ytdHealthInsurance - ytdPension - ytdEmploymentInsurance
```

這幾個欄位（`ytdGrossIncome`／`ytdIncomeTax`／`ytdResidentTax`／`ytdHealthInsurance`／`ytdPension`／`ytdEmploymentInsurance`）已經存在於 `SimulationContext`，由 `SalaryContextBuilder` 逐月加總真實薪資單資料算出（`hub-app/.../factory/SalaryContextBuilder.java` 第 177-179 行附近），並且已經在 `YearlySimulationPipeline.java` 第 229-239、284 行被使用於年末調整計算。

**為什麼這樣算是對的**：`fullYearNetIncome` 的公式是 `grossTotalSalary - socialInsurance - incomeTax - inhabitantTax`（`ActiveTaxCalculationEngine.java` 第 198 行）。上面這個殘值公式，用的是完全同一組項目（毛薪資、所得稅、住民稅、健保、年金、雇用保險），只是 YTD 這段用「真實已發生」的數字，而不是像 `fullYearNetIncome` 那樣整年都用公式模型推算。兩邊基準完全對齊，相減出來的 `remainingNetIncome` 才會正確地只代表「剩餘月份的理論淨額」，不會再混入任何已經真實發生過的薪資單雜項扣除（不管那雜項扣除叫 ESPP、DC Matching、還是任何公司自訂項目）。

**為什麼這個做法天然不怕「中途加入/中斷/轉換」**：因為它完全不需要知道「DC Matching 這個項目本身」發生了什麼事——不管使用者是 6 月才開始 DC Matching、還是 11 月轉去 iDeCo、還是完全沒加入過，這些變化都會直接反映在真實的 `ytdGrossIncome`／`ytdIncomeTax`／...等等這些已經逐月加總好的真實數字裡，公式不需要另外去猜測或還原任何「哪個月開始/哪個月結束」的細節。這跟 Fixed-42 面對的「無法驗證資料連續性」問題完全不同維度——Fixed-42 需要單獨抓出 DC Matching/iDeCo 的金額（因為要算稅務扣除額，跟 ESPP 不能混在一起），這裡則完全不需要拆開。

### 附帶確認：DC 企業拠出（公司出的部分）已經正確處理，不需要修

`TaxAdvantagedAccountProjector.processAccountsForYear`（`hub-app/.../calculator/TaxAdvantagedAccountProjector.java` 第 247-252 行）已經有 `annualDcMatching = Math.max(0, annualDc - annualEmployerContribution)`，會把公司出的那部分（`事業主拠出額`，不是員工自己的錢，本來就不該算進員工的稅務扣除額或現金流扣款）從合計金額中扣除，只留下員工自己出的 Matching 部分傳給 `InvestmentContributions.annualDcMatchingContribution()`。這部分邏輯是對的，不在本 Issue 的修正範圍內，特此記錄供未來對照，避免被誤判為新問題。

### 附帶確認：iDeCo／NISA 不需要任何修正

iDeCo 跟 NISA 都是使用者自己跟證券商簽約、直接從銀行帳戶扣款，從頭到尾不會經過薪資單、不會被算進 `netAmount`。也就是說：
- `ytdNetAmount`（以及上面新算法的 `ytdNetIncome`）從來就沒有扣過 iDeCo/NISA，沒有「已扣但基準不對」的問題。
- `fullYearNetIncome` 的公式也從未扣過 iDeCo/NISA（只透過 `retirementDeduction` 影響 iDeCo 的應課稅所得，NISA 本身不可抵稅，完全不出現在 `netIncome` 公式裡）。
- 兩邊基準本來就一致，`fullYearNetIncome - ytdNetIncome` 這一步對 iDeCo/NISA 而言沒有滲漏。
- iDeCo/NISA 真正的現金效果（過去已扣的部分、未來剩餘月份要扣的部分）分別由「真實銀行餘額快照本來就已經反映過去扣款」跟「`TaxAdvantagedAccountProjector` 用剩餘月份計算未來扣款」兩段各自正確處理，資產餘額（`nisaBalance`／`idecoBalance`）也確認是從真實快照（`NisaContextBuilder`／`DefinedContributionContextBuilder`）疊加剩餘月份成長，不是從零重建，不會雙重計算。

如果之後有人想把 iDeCo/NISA 也套用跟 DC Matching 一樣的「回補」邏輯，請不要——那樣反而會把一個本來正確的計算改錯（詳見 Fixed-42 對「假設連續性」風險的完整討論）。

## 建議修正方向 (Suggested Fix)

只修改 `YearlySimulationPipeline.java`：把殘值公式抽成一個共用的 private method（`computeYtdNetIncomeBaseline`），在 `applyCashflowAndSettlement` 呼叫它。抽成函式的原因：這個公式同時解決了本 Issue（DC Matching）跟 [Fixed-40.md](Fixed-40.md)（ESPP）描述的 YTD 滲漏問題，兩邊用同一個函式可以避免同一段邏輯散落兩處、日後改一邊忘了改另一邊。其餘檔案不需要變更。

## 最小範例程式碼 (Sample Code)

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，新增 private method（同時修好本 Issue 的 DC Matching 滲漏跟 Fixed-40 的 ESPP 滲漏）

// 用「真實 YTD 毛薪資 - 真實 YTD 稅金/社保」重建「尚未扣任何薪資單雜項扣除（ESPP、DC Matching 等）前」的
// 淨額基準，跟 fullYearNetIncome 的計算基準（grossTotalSalary - socialInsurance - incomeTax - inhabitantTax）
// 完全對齊。不需要另外去追蹤/推算 ESPP 或 DC Matching 各自實際扣了多少，也不受「哪個月開始加入/中途有沒有
// 中斷/改金額」影響——因為用的都是已經真實發生、逐月加總好的數字。
private long computeYtdNetIncomeBaseline(SimulationContext context) {
    return context.getYtdGrossIncome()
            - context.getYtdIncomeTax()
            - context.getYtdResidentTax()
            - context.getYtdHealthInsurance()
            - context.getYtdPension()
            - context.getYtdEmploymentInsurance();
}
```

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyCashflowAndSettlement（修正後）
long fullYearNetIncome = state.getNetIncome();
long ytdNetIncome = (context.getYear() == context.getCurrentYear())
        ? computeYtdNetIncomeBaseline(context)
        : 0;
long remainingNetIncome = fullYearNetIncome - ytdNetIncome;
long netSavings = remainingNetIncome - state.getAnnualExpenses();
long remainingCashThisYear = netSavings - contributions.getTotalEmployeeInvestments() - ...; // 其餘不變
```

## 備註：這個共用函式也修好了 Fixed-40 的 ESPP YTD 滲漏

[Fixed-40.md](Fixed-40.md) 描述的 ESPP YTD 滲漏，跟本 Issue 的 DC Matching YTD 滲漏，是同一個「基準沒對齊」問題在不同扣除項目上的表現。上面的 `computeYtdNetIncomeBaseline` 用的是薪資單的毛薪資跟法定稅金/社保欄位，不管實際被扣走的雜項是 ESPP、DC Matching、還是任何未來新增的扣除項目，都會自動被還原，不需要為每一種扣除項目各自新增專屬欄位（例如 Fixed-40 原本提議的 `ytdEsppContribution`）。因此 Fixed-40 已同步修改：移除該欄位提案，直接引用這裡的函式；Fixed-40 保留的是跟這裡無關的其他修正項目（移除年末錯誤加回 ESPP 的邏輯、不外推未來 ESPP、清理死碼）。

## 修正報告 (Resolution Report)

已依照「建議修正方向」完成修復，本修正同時解決了 **Fixed-40 (ESPP)** 與 **Fixed-41 (DC Matching)** 共同面臨的 YTD 基準不對齊與雙重扣減問題。

1. **統一提取基準公式**：
   - 在 `YearlySimulationPipeline.java` 中新增了 `computeYtdNetIncomeBaseline` 方法。
   - 該方法僅使用純粹的 `YTD 毛薪資 - YTD 稅金/社保`，不再混入已經在薪資單上扣除的 ESPP 或 DC Matching 等項目。
   - 透過此基準計算出的 `remainingNetIncome`，確保減法的兩邊完全對齊，徹底消除了第一年過渡期因重複扣減導致的現金滲漏（Liquid Cash 不再異常暴增或暴跌）。

2. **徹底移除無效的 ESPP 外推邏輯 (Fixed-40)**：
   - 刪除 `SalaryContextBuilder.java` 中透過歷史薪資單計算平均並外推未來 ESPP 的邏輯。
   - 刪除 `SimulationContext.java` 中不再需要的 `projectedAnnualEsppThisYear` 欄位。
   - 移除 `YearlySimulationPipeline.java` 的 `applyYearEndAdjustment` 中將 ESPP 錯誤加回 `netIncome` 的死碼，避免無中生有的現金灌水。

3. **測試與驗證**：
   - 後端測試已由開發者手動執行 `mvn clean install` 驗證通過，確保既有業務邏輯與測試案例未被破壞。

Resolves: Fixed-40, Fixed-41
