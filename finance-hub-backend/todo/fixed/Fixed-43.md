# Fixed-43：DC/iDeCo 年資計算「起始種子用資格取得日算、未來每年用累加」兩套標準不一致，累加的判斷條件本身也錯——應統一改成「有資格取得日就每年重算，不要累加」

## 相關檔案與 Package 對照表 (Files Involved)

| Class | Package | 檔案路徑 | 是否需要修改 |
|---|---|---|---|
| `YearlySimulationPipeline` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` | 是，核心修正：`applyWorkingTaxes` 方法內，把 `dcContributionYears`/`idecoContributionYears` 的「每年 +1」改成「每年用資格取得日重算」 |
| `SimulationContext` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.model` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java` | 是，新增兩個欄位：`dcEnrollmentDate`（`LocalDate`）、`idecoEnrollmentDate`（`LocalDate`），現有的 `dcContributionYears`（第 95 行）、`idecoContributionYears`（第 96 行）兩個 `int` 欄位不動 |
| `DefinedContributionContextBuilder` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java` | 是，`initializeDefinedContributionContext` 方法內，四個分支都要處理：有 `accountSnapshot` 時（第 53-60 行 DC、第 117-124 行 iDeCo）把「使用者覆寫值（若非 null）換算出來的日期」或「`accountSnapshot.getEnrollmentDate()`」存進 `context.setDcEnrollmentDate(...)`/`context.setIdecoEnrollmentDate(...)`；沒有 `accountSnapshot`、只有 profile-setting 假帳戶時（第 88-93 行 DC、第 152-157 行 iDeCo）也要同樣把使用者覆寫值換算出來的日期存進去 |
| `TaxAdvantagedAccountProjector` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` | 否，不需修改，僅供對照 `dcContributionYears`/`idecoContributionYears` 的下游用法：第 216 行 `int totalYears = Math.max(context.getDcContributionYears(), context.getIdecoContributionYears());`、第 225 行與第 230 行分別呼叫 `taxEngine.calculateRetirementTax(...)` |
| `ActiveTaxCalculationEngine` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java` | 否，不需修改，僅供對照 `calculateRetirementTax` 方法（第 266-274 行）的扣除額公式：`contributionYears <= 20` 時 `deduction = Math.max(800_000L, 400_000L * contributionYears)`，否則 `deduction = 8_000_000L + 700_000L * (contributionYears - 20)` |
| `DefinedContributionAccountSnapshot` | `ch.lin.finance.hub.backend.api.domain.model.accounting.dc` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java` | 否，不需修改，僅供對照 `enrollmentDate` 欄位（第 97 行，`private LocalDate enrollmentDate;`）的定義來源 |

## 現況說明 (Current State)

現況 `dcContributionYears`/`idecoContributionYears` 這兩個年資計數器，實際上是被**兩套完全不同標準**分別在兩個地方決定：

**標準一（模擬起始種子值）**：`DefinedContributionContextBuilder.initializeDefinedContributionContext` 方法（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java` 第 53-60 行，iDeCo 對應第 117-124 行），用的是**日期算法**：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.factory
// class DefinedContributionContextBuilder，method initializeDefinedContributionContext，第 53-60 行（DC）
} else if (accountSnapshot.getEnrollmentDate() != null) {
    context.setDcContributionYears(
            Period.between(accountSnapshot.getEnrollmentDate(), LocalDate.now()).getYears());
}
```

**標準二（模擬未來每一年）**：`YearlySimulationPipeline.applyWorkingTaxes` 方法（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` 第 145-150 行），用的是**逐年 +1 累加**，且判斷條件只看員工自行加碼（マッチング拠出）：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyWorkingTaxes，第 145-150 行
if (contributions.annualDcMatchingContribution() > 0) {
    context.setDcContributionYears(context.getDcContributionYears() + 1);
}
if (contributions.annualIdecoContribution() > 0) {
    context.setIdecoContributionYears(context.getIdecoContributionYears() + 1);
}
```

`annualDcMatchingContribution()` 來自 `TaxAdvantagedAccountProjector.processAccountsForYear` 方法（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` 第 252 行）：`long annualDcMatching = Math.max(0, annualDc - annualEmployerContribution);`——也就是「DC 總提繳金額 − 公司出資部分」，是**員工自己加碼提繳的部分**，不是 DC 帳戶的總提繳金額。

## 問題原因 (Cause)

這裡有兩層問題，疊在一起：

**第一層（表面症狀）**：日本企業型 DC 的退職所得控除，法律上是按照「加入 DC 制度的年資」計算，跟這段年資裡使用者本人是否額外做マッチング拠出無關——即使使用者完全沒有自行加碼，只要公司每個月都幫他出資進 DC 帳戶，年資照樣在累積。但標準二的判斷條件綁死在「本人是否有自行加碼」，對於「只有公司出資、使用者本人完全沒有設定マッチング拠出」這種常見組合，`dcContributionYears` 從模擬起始那一年之後就永遠不會再往上加，即使真實年資持續在累積。

**第二層（更根本的問題，第一層只是它的其中一個表現）**：標準一跟標準二本質上是兩套不同的計算方式，分別管過去（起始那一刻，用真實日期精確計算）跟未來（模擬展開後的每一年，用累加湊）。累加式的計算天生有「誤差會複利」的缺陷：只要某一年的判斷條件沒抓對（無論是這次抓到的「只看加碼」，或未來任何其他判斷條件上的疏漏），那一年的誤差會永久疊加到後面每一年，因為累加是「在舊值基礎上 +1」，不會自我校正。相對地，用資格取得日算的日期算法，每一年都是從固定的起點（資格取得日）重新算一次到當下這一刻，就算某一年因為某種原因没被正確更新，下一次重算照樣會得到正確答案，不會有誤差累積的問題。

換句話說：只要標準二繼續維持「累加」這個機制本身，不管這次把判斷條件修成看總提繳金額還是別的，都只是把「這次已知的錯」修好，並不能保證「以後不會有新的判斷條件錯誤，然後又永久累積下去」。既然資格取得日這個真實資料已經存在、也已經在標準一被使用，未來每一年應該直接沿用同一套日期算法重算，而不是另外維持一套累加機制。

**與 [Fixed-51.md](Fixed-51.md) 的關係**：Fixed-51 描述的是使用者自行覆寫年資（`isDcContributionYearsOverridden`/`dcContributionYearsOverride`）這個機制本身的資料一致性錯誤（開關打開但數值是 null 時被錯誤蓋成 0）。

這裡要特別強調一點：使用者自己填的覆寫值，代表的是使用者本人明確聲明的真實年資，其可信度不該低於系統從 `accountSnapshot.getEnrollmentDate()` 自動推算出來的日期——甚至沒有真實帳戶快照（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java` 第 88-93 行、第 152-157 行的 profile-setting 假帳戶分支）時，使用者的覆寫值也應該是唯一可信的年資來源，優先權不該比系統自己「逐年 +1 猜」還低。因此本 Issue 的修正建議不是只把 `dcEnrollmentDate`/`idecoEnrollmentDate` 設成 `accountSnapshot.getEnrollmentDate()`，而是：**只要使用者的覆寫值非 null，就用覆寫值換算出一個對應的日期（`LocalDate.now().minusYears(overrideVal)`），一樣存進 `dcEnrollmentDate`/`idecoEnrollmentDate`，讓覆寫值也能享有跟真實資格取得日一樣的「每年自我校正重算」機制，而不是只在模擬起始那一刻用一次就沒了、後面每一年退回去用容易複利誤差的 `+1` 累加。** 換算出來的日期優先權高於 `accountSnapshot.getEnrollmentDate()`（跟現有覆寫優先於自動推算的順位一致），且即使沒有真實帳戶快照（profile-setting 假帳戶分支）也同樣適用——只要使用者填了覆寫值，就不該落回 `+1` 累加。真正會落回 `YearlySimulationPipeline.applyWorkingTaxes`（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`）裡 `+1` 累加 fallback 的情況，只剩下「沒有真實帳戶快照、使用者也完全沒有填覆寫值」這種真正沒有任何可信資料的最後手段場景。

實作上有個附帶好處：要安全地呼叫 `LocalDate.now().minusYears(overrideVal)`，前提就必須先判斷 `overrideVal != null`（否則會直接 NPE）——這個判斷本身，剛好就是 Fixed-51 描述的修正（「覆寫開關打開但數值是 null 時，不該當成有效覆寫」）。也就是說，只要照本 Issue 建議的寫法實作，Fixed-51 描述的 bug 會被自然一併修正。但兩份文件仍建議保留為獨立 Issue：即使不採用本 Issue「日期重算」的整體設計、只單純想修 Fixed-51 描述的「覆寫優先權判斷」本身，那也是一個獨立且必要的修正，兩者描述的問題本質仍不同（覆寫機制的資料一致性 vs. 年資計算的展開方式）。

**與 [FINDING-37](../planned/FINDING-37-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md) 的關係**：FINDING-37 討論的是「DC/iDeCo 解鎖年齡被解耦後，會不會導致退職所得控除重複計算（double-dip）」，該問題目前因為 `TaxAdvantagedAccountProjector.java` 裡 `idecoUnlocked = dcUnlocked` 的寫死耦合而尚未觸發，屬於死碼、非即時問題，跟本 Issue 描述的「年資計算方式本身」是完全不同的面向，不建議合併處理。

## 建議修正方向 (Suggested Fix)

把 `YearlySimulationPipeline.applyWorkingTaxes` 裡的「+1 累加」，改成「有資格取得日就每年用資格取得日重算，沒有資格取得日（沒有真實帳戶快照，只有 profile-setting 假帳戶）才退回累加」：

1. `SimulationContext`（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java`）新增 `dcEnrollmentDate`、`idecoEnrollmentDate` 兩個 `LocalDate` 欄位。
2. `DefinedContributionContextBuilder.initializeDefinedContributionContext`（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java`）四個分支都要改成「先看使用者覆寫值，非 null 就換算成日期；否則才看 `accountSnapshot.getEnrollmentDate()`（如果有 `accountSnapshot` 的話）」，把決議出來的日期存進 `context.setDcEnrollmentDate(...)`/`context.setIdecoEnrollmentDate(...)`：
   - 有 `accountSnapshot`（第 53-60 行 DC、第 117-124 行 iDeCo）：覆寫值非 null → 用 `LocalDate.now().minusYears(overrideVal)` 當作日期；否則 → 用 `accountSnapshot.getEnrollmentDate()`（可能是 null）。
   - 沒有 `accountSnapshot`、只有 profile-setting 假帳戶（第 88-93 行 DC、第 152-157 行 iDeCo）：覆寫值非 null → 同樣用 `LocalDate.now().minusYears(overrideVal)` 當作日期；否則沒有任何資料可用，日期維持 null。
   這樣使用者的覆寫值不再只是「模擬起始那一刻用一次的種子值」，而是跟真實資格取得日一樣，能持續提供後面每一年重算的基準日期——即使完全沒有真實帳戶快照。
3. `YearlySimulationPipeline.applyWorkingTaxes`（`hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`）把 `+1` 累加改成依 `dcEnrollmentDate`/`idecoEnrollmentDate` 是否存在分兩條路：
   - 有日期（不管是來自真實資格取得日還是使用者覆寫值換算出來的）：只要今年 DC/iDeCo 帳戶還有任何提繳（用總提繳金額 `annualDcContribution()`/`annualIdecoContribution()`，不是只看加碼部分——帳戶有沒有在動用這個當判斷依據就夠了），直接用 `Period.between(enrollmentDate, 今年12/31).getYears()` **重新算一次**蓋掉舊值；今年沒有任何提繳（帳戶已經停止/離職），維持原值不動，等於凍結在最後一次還有效的年資上。
   - 沒有日期（沒有真實帳戶快照、使用者也完全沒有填覆寫值——兩者都沒有才會發生，是真正沒有任何可信資料的最後手段場景）：退回原本的 `+1` 累加，判斷條件改用總提繳金額 `annualDcContribution()`/`annualIdecoContribution()`，理由跟第一層症狀描述的一樣（公司出資也該算年資，不是只看加碼）。

這個改法同時解決了第一層症狀（不會再卡在 0）跟第二層問題（過去跟未來用同一套標準，不會再有誤差複利的風險），同時把使用者的覆寫值也一併納入同一套標準，不會因為「沒有真實帳戶快照」就被打回相對不可靠的 `+1` 累加機制，且已內含 Fixed-51 描述的 null 值判斷修正（見上方「與 Fixed-51 的關係」說明）。

## 最小範例程式碼 (Sample Code)

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.model
// class SimulationContext，新增欄位（放在現有 dcContributionYears/idecoContributionYears 附近，第 95-96 行）
private LocalDate dcEnrollmentDate;
private LocalDate idecoEnrollmentDate;
```

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.factory
// class DefinedContributionContextBuilder，method initializeDefinedContributionContext
// 分支一（有 accountSnapshot，取代原本第 53-60 行 DC；iDeCo 對應第 117-124 行同樣處理）
Integer dcOverrideVal = (dcSettingsOpt.isPresent()
        && Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden()))
                ? dcSettingsOpt.get().getDcContributionYearsOverride()
                : null;
// 覆寫值非 null 就換算成日期，優先權高於 accountSnapshot 的真實資格取得日；
// 兩者都沒有時 resolvedDcEnrollmentDate 為 null，維持原本「什麼都不設」的行為。
LocalDate resolvedDcEnrollmentDate = dcOverrideVal != null
        ? LocalDate.now().minusYears(dcOverrideVal)
        : accountSnapshot.getEnrollmentDate();
if (resolvedDcEnrollmentDate != null) {
    context.setDcEnrollmentDate(resolvedDcEnrollmentDate);   // 新增：把日期存下來，供後面每年重算用
    context.setDcContributionYears(
            Period.between(resolvedDcEnrollmentDate, LocalDate.now()).getYears());
}
```

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.factory
// class DefinedContributionContextBuilder，method initializeDefinedContributionContext
// 分支二（沒有 accountSnapshot，只有 profile-setting 假帳戶，取代原本第 88-93 行 DC；iDeCo 對應第 152-157 行同樣處理）
// 這裡沒有 accountSnapshot 可用，唯一可能的日期來源就是使用者的覆寫值——
// 即使沒有真實帳戶快照，只要使用者填了覆寫值，一樣要給他一個 dcEnrollmentDate，
// 不能讓他退回去被系統用 +1 累加。
if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden())) {
    Integer overrideVal = dcSettingsOpt.get().getDcContributionYearsOverride();
    if (overrideVal != null) {
        context.setDcEnrollmentDate(LocalDate.now().minusYears(overrideVal));
        context.setDcContributionYears(overrideVal);
    }
    // overrideVal 為 null：視同沒有覆寫，dcEnrollmentDate 維持 null，
    // dcContributionYears 維持預設值 0（跟現況行為一致，不算回歸）。
}
```

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline
// class YearlySimulationPipeline，method applyWorkingTaxes（取代原本第 145-150 行的「+1」累加）
if (context.getDcEnrollmentDate() != null) {
    if (contributions.annualDcContribution() > 0) {
        context.setDcContributionYears(
                Period.between(context.getDcEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31)).getYears());
    }
    // 今年沒有任何 DC 提繳（帳戶已停止/離職）：維持原值，凍結在最後一次還有效的年資
} else if (contributions.annualDcContribution() > 0) {
    // 沒有真實帳戶快照可用資格取得日，退回累加，但判斷條件改用總提繳金額（不是只看員工加碼部分）
    context.setDcContributionYears(context.getDcContributionYears() + 1);
}

if (context.getIdecoEnrollmentDate() != null) {
    if (contributions.annualIdecoContribution() > 0) {
        context.setIdecoContributionYears(
                Period.between(context.getIdecoEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31)).getYears());
    }
} else if (contributions.annualIdecoContribution() > 0) {
    context.setIdecoContributionYears(context.getIdecoContributionYears() + 1);
}
```

---

## 實作報告 (Implementation Report)
- **實作狀態**: 已完成 (Completed)
- **實作細節**: 
  - `SimulationContext` 成功擴充了 `dcEnrollmentDate` 與 `idecoEnrollmentDate` 兩個欄位。
  - `DefinedContributionContextBuilder` 的四個分支皆已依照計畫重構，確實處理了覆寫值與 `accountSnapshot.getEnrollmentDate()` 的優先順位轉換，並將解析出的基準日期存入 Context 中，同時完成當下年資初值的精確計算。
  - `YearlySimulationPipeline` 中的累加邏輯已經成功被「絕對日期重算」取代。有基準日時一律採用 `Period.between` 精確重算；極端情況下無基準日才 Fallback 至 `+1` 累加。
- **Claude 提供的 Sample Code 勘誤**:
  - 在修改 `YearlySimulationPipeline` 時，發現 Claude 提供的範例程式碼中有誤：`InvestmentContributions` 中用來取得 DC 總提繳額的正確方法名稱為 `annualDcTotalContribution()`，而非範例中的 `annualDcContribution()`。實作時已將其自動修正。
- **驗證**: 編譯通過，使用者已確認手動跑測試正常。年資計算誤差複利的隱患徹底根除。
